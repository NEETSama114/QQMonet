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

## 建议
安装前先在 LSPosed 中仅勾选 QQ 作用域。若 QQ 启动异常，先关闭本模块。要完成真正的版本适配，需要 QQ 9.2.85 的 classes.dex/反编译类名与运行日志，逐项校准 Hook 目标和资源 ID。
