<div align="center">
<h1>QQMonet（实验移植版）</h1>

<a href="https://github.com/NEETSama114/QQMonet/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/NEETSama114/QQMonet?label=Stars"></a>
<a href="https://github.com/NEETSama114/QQMonet/releases"><img alt="GitHub all releases" src="https://img.shields.io/github/downloads/NEETSama114/QQMonet/total?label=Downloads"></a>

<p>从 TimMonet 移植到 QQ 9.2.85 的实验版本；目标是适配 Material You「莫奈取色」与深色主题</p>

<p>
  <b>支持框架</b>：
  <b><a href="https://github.com/LSPosed/LSPosed">LSPosed</a></b>
</p>
</div>

---

## 项目简介(必看)

- 这是一款基于 **Xposed API** 开发的 LSPosed 模块，作用于 **QQ 9.2.85（实验目标，尚未完成设备验证）**
- 推荐使用最新的LSPosed, 于lsposed.zip下载
- 在模块内部完成 Material You 调色板计算，**不依赖系统莫奈引擎**（低版本 Android 同样可用）
- 将 QQ 的品牌蓝与中性色阶整体映射为壁纸/种子色派生的 Material 3 配色：
  - 深色主题化：登录页、聊天（AIO）、转发对话框、浮层菜单、我的页等原生页面
  - 品牌色统一：按钮、链接、选中态、聊天气泡、徽标红点、底栏图标等
- 来自KernelSU的设置页，可实时预览配色
- 全程事件驱动、无延时轮询，热路径全部记忆化，性能开销低
- **在TimMonet模块内**设置深色浅色与Monet配色方案

---

## 功能分类

本模块功能主要分为以下 3 类：

| 功能类型 | 作用说明 | 启用方式 |
|---|---|---|
| 主题覆盖 | 对 QQ 各原生页面/组件做深色化与调色映射 | 勾选 QQ 后直接生效 |
| 配色定制 | 自定义颜色模式、种子色、调色板风格与色彩规范 | 在模块设置页调整 |
| AMOLED黑 | 你将看到一个气泡都是AMOLED黑的最省电Tim | 在模块设置页设置 |

## 使用说明

### 主题覆盖

1. 在 LSPosed 管理器中启用本模块，作用域勾选 `com.tencent.mobileqq`（`scope.list` 已声明）
2. 强制停止并重启 QQ
3. 生效后进入各页面检查配色
4. 如果某些页面未生效，可能是该组件走的渲染路径未覆盖，请前往 [Issues](https://github.com/NEETSama114/QQMonet/issues) 反馈（附截图与日志最佳）

### 配色定制

1. 打开模块的 Compose 设置页（直接打开模块应用）
2. 选择颜色模式 / 取色源 / 调色板风格 / 色彩规范
3. 保存后模块会自动强制重启 QQ 使新配色完整生效
4. 更换壁纸后如需跟随系统重新取色，同样建议重启一次 QQ

---

## 兼容性

- 推荐 **LSPosed**（或兼容 Xposed API 93+ 的框架）配合 **QQ 4.1.0.4050** 使用
- 模块最低支持 Android 8.0（API 26）
- QQ 混淆会在版本升级时重命名方法，hook 使用「方法名 + 签名」匹配并附带签名兜底，但跨大版本仍可能需要同步更新类名
- 部分色彩如 错误红 / 成功绿 / 警告黄、纯黑纯白蒙层等固定语义色不参与取色

---


## 构建

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求 JDK 17+。

---

## 免责声明

- 本模块仅供学习交流使用，请勿用于商业或违法用途
- 本模块完全免费，不存在收费盈利；如果你是付费购买的，请联系售卖者退款
- 禁止售卖、倒卖或二次打包分发本模块
- 因使用本模块产生的任何后果，由使用者自行承担

---