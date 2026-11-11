package com.timmonet.core

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.timmonet.settings.SettingsWriteReceiver
import com.timmonet.settings.TimMonetSettings
import com.timmonet.ui.theme.AppSettings
import io.github.libxposed.api.XposedModule

/**
 * QQ 进程侧：读取/保存模块设置。
 *
 * 两条存储，按 `revision` 时间戳取新的那份（谁后改以谁为准）：
 *  1. **LSPosed 远端偏好**（模块 App 的远端设置偏好）——
 *     宿主侧是**只读**的：`getRemotePreferences()` 返回只读实现，`edit()` 抛
 *     `UnsupportedOperationException: Read only implementation`（实测 logcat 原文）。
 *  2. **宿主本地偏好**（QQ 私有目录 `tim_monet_host.xml`）—— 可写；QQ 私有目录中
 *     QQ 进程内保存的设置落在这里，重启后立即生效，不依赖跨进程投递。
 *
 * 宿主的改动还会**尽力广播**给模块 App（[SettingsWriteReceiver]）以同步远端偏好
 * 和模块界面 —— 实测部分 ROM 上宿主发出的广播投递不到模块 App，所以它只做
 * "锦上添花"，不作为生效条件。
 *
 * 历史坑：`attach()` 一旦抛异常，`prefs` 就停在 null，而旧 `write()` 是
 * `val prefs = prefs ?: return` —— 于是"在宿主进程内改设置"这件事**永久静默失效**。
 * 现在改成惰性重试：任何一次访问发现还没接上，就再试一次（失败有退避）。
 */
object SettingsBridge {

    private const val TAG = "TimMonet"

    /** 模块自己的包名（广播目标）。 */
    private const val MODULE_PACKAGE = "com.github.neetsama114.qqmonet"

    private const val SETTINGS_WRITE_RECEIVER = "com.timmonet.settings.SettingsWriteReceiver"

    /** 宿主本地偏好文件名（QQ 私有目录）。 */
    private const val HOST_PREFS_NAME = "tim_monet_host"

    /** 远端偏好接不上时的重试间隔。 */
    private const val ATTACH_RETRY_MS = 5000L

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var module: XposedModule? = null

    /** 是否已注册过变更监听（监听只注册一次，附着本身可重试）。 */
    @Volatile
    private var listenerRegistered = false

    @Volatile
    private var lastAttachAttemptAt = 0L

    /** readCurrent 连续失败计数（只用于日志节流，避免热路径刷屏）。 */
    @Volatile
    private var readFailureCount = 0

    @Volatile
    var current: AppSettings = TimMonetSettings.defaults()
        private set

    /** 宿主可写的本地偏好（QQ 私有目录）。 */
    @Volatile
    private var hostPrefs: SharedPreferences? = null

    @Volatile
    private var appContext: Context? = null

    private fun context(): Context? {
        appContext?.let { return it }
        val ctx = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
        if (ctx != null) appContext = ctx
        return ctx
    }

    private fun hostStore(): SharedPreferences? {
        hostPrefs?.let { return it }
        val ctx = context() ?: return null
        return try {
            ctx.getSharedPreferences(HOST_PREFS_NAME, Context.MODE_PRIVATE).also { hostPrefs = it }
        } catch (t: Throwable) {
            Log.w(TAG, "host prefs unavailable", t)
            null
        }
    }

    /** 两份存储里 revision 更新的那份（宿主本地 / 远端）。 */
    private fun newestOf(local: SharedPreferences?, remote: SharedPreferences?): AppSettings? {
        val l = local?.let { runCatching { TimMonetSettings.read(it) }.getOrNull() }
        val r = remote?.let { runCatching { TimMonetSettings.read(it) }.getOrNull() }
        if (l == null) return r
        if (r == null) return l
        val lr = runCatching { TimMonetSettings.revision(local) }.getOrDefault(0L)
        val rr = runCatching { TimMonetSettings.revision(remote) }.getOrDefault(0L)
        // 只留一行、可用来判断"谁后改"：面板改的落宿主本地，模块 App 改的走远端
        Log.i(
            TAG,
            "settings source: host(rev=$lr) vs remote(rev=$rr) -> " +
                if (lr >= rr) "host" else "remote"
        )
        return if (lr >= rr) l else r
    }

    fun attach(module: XposedModule) {
        this.module = module
        ensureRemote()
    }

    /**
     * 惰性附着（幂等、带退避）。返回是否拿到了可用的远端偏好。
     *
     * 成功后的调用只是一次 volatile 读，热路径开销可忽略。
     */
    private fun ensureRemote(): SharedPreferences? {
        prefs?.let { return it }
        val m = module ?: return null
        val now = System.currentTimeMillis()
        if (lastAttachAttemptAt != 0L && now - lastAttachAttemptAt < ATTACH_RETRY_MS) return null
        lastAttachAttemptAt = now
        return try {
            val remotePrefs = m.getRemotePreferences(TimMonetSettings.REMOTE_PREFS_NAME)
            prefs = remotePrefs
            // 宿主本地（面板里改的）与远端（模块 App 改的）取 revision 新的那份
            current = newestOf(hostStore(), remotePrefs) ?: TimMonetSettings.read(remotePrefs)
            if (!listenerRegistered) {
                listenerRegistered = true
                remotePrefs.registerOnSharedPreferenceChangeListener { _, _ ->
                    val next = TimMonetSettings.read(remotePrefs)
                    // 颜色相关设置(色彩标准/深浅模式/色彩风格/色域)变化时，热刷新
                    // 无法覆盖底栏/顶栏等已绘制区域 → 需要强停 QQ 全量重建。
                    // ⚠️ 面板正在显示时必须**推迟到退出面板再重启**（否则用户刚点
                    // 一个开关，整个面板连同界面一起被重启掉，看起来像"点了没反应"）。
                    val paletteChanged = paletteDiffers(current, next)
                    current = next
                    if (paletteChanged) requestQQRestart()
                    MonetPalette.onSettingsChanged()
                }
            }
            Log.i(TAG, "remote settings attached: ${current.paletteStyle}/${current.colorSpec}")
            remotePrefs
        } catch (t: Throwable) {
            Log.e(TAG, "attach remote settings failed (will retry)", t)
            prefs = null
            null
        }
    }

    /** 配色相关字段是否有变化（监听器与 write() 共用同一份判据）。 */
    private fun paletteDiffers(a: AppSettings, b: AppSettings): Boolean =
        a.colorMode != b.colorMode ||
            a.paletteStyle != b.paletteStyle ||
            a.colorSpec != b.colorSpec ||
            a.keyColor != b.keyColor ||
            a.amoledBlack != b.amoledBlack

    /** 配色发生变化后重启 QQ，使新调色板在下次冷启动时完整生效。 */
    private fun requestQQRestart() {
        killQQProcess()
    }

    /** 颜色相关设置变更 → 结束 QQ 进程（仅在 QQ 进程内执行；用户重开即全量新配色）。
     *  系统深浅色切换（自动模式下）也走这里，见 hookSystemNightChange。 */
    internal fun killQQProcess() {
        try {
            val app = context() ?: return
            if (app.packageName != "com.tencent.mobileqq") return
            Log.i(TAG, "palette settings changed, restarting QQ")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    android.os.Process.killProcess(android.os.Process.myPid())
                } catch (t: Throwable) {
                    // 强停失败不是致命错误：设置已经写进本地/远端，下次冷启动才全量生效。
                    Log.w(TAG, "killProcess failed, palette applies on next cold start", t)
                }
            }, 300L)
        } catch (t: Throwable) {
            Log.w(TAG, "killQQProcess unavailable, palette applies on next cold start", t)
        }
    }

    fun readCurrent(): AppSettings {
        val remoteModule = module ?: return current
        return try {
            // RemotePreferences 是获取时的快照，模块 UI 在另一进程写入后不会自动刷新，
            // 因此每次轮询都重新向框架请求最新数据。
            val next = newestOf(
                hostStore(),
                remoteModule.getRemotePreferences(TimMonetSettings.REMOTE_PREFS_NAME)
            ) ?: current
            if (next != current) {
                current = next
            }
            current
        } catch (t: Throwable) {
            // 不能再静默了：这里一旦开始抛，readCurrent 会永远返回旧快照，表现为
            // "改了设置没反应"且没有任何线索。第一次和之后每 50 次各留一条。
            readFailureCount++
            if (readFailureCount == 1 || readFailureCount % 50 == 0) {
                Log.w(TAG, "readCurrent failed (#$readFailureCount), serving stale settings", t)
            }
            current
        }
    }

    /**
     * QQ 进程侧保存设置（当前由宿主侧设置桥接使用）。
     *
     * ⚠️ **不能写远端偏好**：宿主拿到的 `getRemotePreferences()` 是只读实现，
     * `edit()` 抛 `UnsupportedOperationException: Read only implementation`
     * （实测 logcat 原文）。旧实现就死在这里 —— 写失败抛异常，后面的重启逻辑
     * 根本没执行，表现为"设置没有保存，QQ 也没有按预期重启"。
     *
     * 现在：① **写宿主本地偏好**（可写、权威）→ 宿主自己读的就是它，重启即生效；
     * ② **尽力广播**给模块 App（[SettingsWriteReceiver]）同步远端偏好与模块界面，
     * 投递失败不影响宿主生效。
     *
     * @return 宿主本地是否写入成功。
     */
    fun write(context: Context, settings: AppSettings): Boolean {
        if (appContext == null) appContext = context
        if (prefs == null) ensureRemote()
        val paletteChanged = paletteDiffers(current, settings)
        val stored = try {
            val store = hostStore()
            if (store == null) {
                Log.w(TAG, "host prefs unavailable, settings not saved")
                false
            } else {
                TimMonetSettings.write(store, settings)
                Log.i(
                    TAG,
                    "settings saved to host prefs: ${settings.paletteStyle}/" +
                        "${settings.colorSpec}/mode=${settings.colorMode}"
                )
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "write host prefs failed", t)
            false
        }
        // 尽力同步给模块 App（远端偏好 + 它的界面）。投递不到只记日志。
        try {
            val intent = Intent(SettingsWriteReceiver.ACTION_WRITE).apply {
                setClassName(MODULE_PACKAGE, SETTINGS_WRITE_RECEIVER)
                // 模块 App 可能处于"停止"状态（刚安装 / 被强停）→ 不带这个 flag
                // 系统会直接把广播丢掉（日志说"已发出"，对面什么都没收到）
                addFlags(
                    Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND
                )
                putExtra(SettingsWriteReceiver.EXTRA_PROTOCOL, SettingsWriteReceiver.PROTOCOL)
                putExtra(SettingsWriteReceiver.EXTRA_COLOR_MODE, settings.colorMode.value)
                putExtra(SettingsWriteReceiver.EXTRA_KEY_COLOR, settings.keyColor)
                putExtra(
                    SettingsWriteReceiver.EXTRA_PALETTE_STYLE,
                    settings.paletteStyle.name
                )
                putExtra(SettingsWriteReceiver.EXTRA_COLOR_SPEC, settings.colorSpec.name)
                putExtra(SettingsWriteReceiver.EXTRA_AMOLED, settings.amoledBlack)
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "settings write broadcast sent (best effort)")
        } catch (t: Throwable) {
            Log.w(TAG, "send settings write broadcast failed (host store already saved)", t)
        }
        current = settings
        if (paletteChanged) requestQQRestart()
        return stored
    }
}
