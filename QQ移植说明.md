# QQ 9.2.85 实验移植说明

本目录是在 TimMonet 原项目基础上制作的 QQ 9.2.85 实验移植版。

## 已修改
- Xposed 目标包名：`com.tencent.tim` → `com.tencent.mobileqq`
- 模块静态作用域：`scope.list` 改为 `com.tencent.mobileqq`
- Android Manifest 的 package visibility 查询改为 QQ
- Hook 入口 `MainModule.TARGET_PACKAGE` 改为 QQ
- `TimMonetHooks.HOST_PACKAGE` 与 SettingsBridge 宿主包名判断改为 QQ
- 昵称资源查询的包名改为 QQ
- 模块显示名称和简介改为 QQ 莫奈取色

## 尚未完成/未验证
- 原项目的 Hook 类名、混淆方法签名、资源 ID 和主题 token 主要依据 TIM 4.1.0 的实现。QQ 9.2.85 与 TIM 并非完全相同，包名切换不等于完成适配。
- `TimMonetHooks.kt` 中仍有 TIM 专属类名/逻辑（例如 `com.tencent.tim.function.FunctionItemInfo`、TIM 我的页面与个人资料视图等）。对应 Hook 在 QQ 中可能找不到目标类而自动跳过，也可能需要针对 QQ 9.2.85 重新分析。
- 当前环境无法联网下载 Gradle 8.13，因此未能完成 Gradle 编译；也没有在 QQ 9.2.85 真机上验证运行效果。

## 建议
安装前先在 LSPosed 中仅勾选 QQ 作用域并保留原 TIM 版本备份。若 QQ 启动异常，先关闭本模块。要完成真正的版本适配，需要 QQ 9.2.85 的 classes.dex/反编译类名与运行日志，逐项校准 Hook 目标和资源 ID。
