# XCLoggerTestDemo 多包压测使用说明

## 1. APK 组成

一次构建会生成 6 个相互独立的 Android 应用：

| APK | Application ID | 用途 |
| --- | --- | --- |
| `XCLoggerTestDemo-debug.apk` | `com.xcheng.xcloggertestdemo` | 完整 Demo、压测界面、主压测服务 |
| `XCLoggerStress-fork1-debug.apk` | `com.xcheng.xcloggertestdemo.fork1` | 无 Launcher 压测 Agent |
| `XCLoggerStress-fork2-debug.apk` | `com.xcheng.xcloggertestdemo.fork2` | 无 Launcher 压测 Agent |
| `XCLoggerStress-fork3-debug.apk` | `com.xcheng.xcloggertestdemo.fork3` | 无 Launcher 压测 Agent |
| `XCLoggerStress-fork4-debug.apk` | `com.xcheng.xcloggertestdemo.fork4` | 无 Launcher 压测 Agent |
| `XCLoggerStress-fork5-debug.apk` | `com.xcheng.xcloggertestdemo.fork5` | 无 Launcher 压测 Agent |

6 个 APK 不使用 `sharedUserId`，安装后由 Android 分配 6 个独立 UID。fork APK 不包含桌面入口，不会改变原 Demo 的入口和其他页面。

## 2. 在 Android Studio 中构建

1. 在 Android Studio 中打开 `XcLoggerTestDemo`，等待 Gradle Sync 完成。
2. 打开右侧 Gradle 工具窗口。
3. 执行 `XcLoggerTestDemo > Tasks > build > assembleStressFleetDebug`。

也可以在项目根目录执行：

```powershell
.\gradlew.bat assembleStressFleetDebug
```

6 个 APK 会统一输出到：

```text
build/outputs/stress-fleet/debug/
```

Android Studio 普通 Run 只构建当前选择的模块和变体；需要完整 6 APK 时应运行 `assembleStressFleetDebug`。

## 3. 安装

解压交付包后，可直接双击 `install_all_debug.bat`，也可依次执行：

```powershell
adb install -r XCLoggerTestDemo-debug.apk
adb install -r XCLoggerStress-fork1-debug.apk
adb install -r XCLoggerStress-fork2-debug.apk
adb install -r XCLoggerStress-fork3-debug.apk
adb install -r XCLoggerStress-fork4-debug.apk
adb install -r XCLoggerStress-fork5-debug.apk
```

建议安装同一次构建生成的完整 APK 集合。所有 APK 使用相同签名；fork 导出的压测服务受 signature 权限 `com.xcheng.xcloggertestdemo.permission.CONTROL_STRESS` 保护，其他签名的应用不能控制它们。

## 4. 使用

1. 打开桌面上的 `XCLoggerTestDemo`。
2. 进入“日志输出压测”页面。
3. 使用滑块，或手动输入 `10` 到 `100` 的整数并点击“应用”。
4. 打开“后台日志输出”开关，原 APK 与所有已安装 fork 将同步开始打印。
5. 修改速率会同步应用到所有正在运行的实例。
6. 关闭开关会停止全部 6 个实例。

页面 Fleet 区域会显示 fork 安装数量、运行数量、每实例速率和累计输出行数。缺少部分 fork APK 时，原 Demo 和已安装的其他实例仍可独立运行。

速率是“每个 APK 每秒的日志行数”，不是六个 APK 的合计值。例如设置 `100 行/秒` 且 6 个 APK 全部安装时，理论总速率为 `600 行/秒`。

压测日志循环覆盖以下 6 个 Tag：

```text
XCStressTag1, XCStressTag2, XCStressTag3,
XCStressTag4, XCStressTag5, XCStressTag6
```

每个实例独立循环覆盖 `V/D/I/W/E/F` 六个 Level，并完整覆盖 6 Tag × 6 Level 的 36 种组合。不同实例使用不同的循环相位，使多个包在同一时刻也能产生不同 Tag 和 Level。

每一行内容都包含 `package`、`instance`、`tag`、`level`、`session`、`seq`、`rate` 和 `elapsed_ms`。其中 `package` 是当前实际运行 APK 的完整包名。

## 5. 验证

```powershell
adb shell pm list packages | Select-String "com.xcheng.xcloggertestdemo"
adb shell ps -A -o UID,PID,NAME | Select-String "xcloggertestdemo"
adb shell logcat -v threadtime,uid | Select-String "XCStressTag"
```

在 XCLogger 包名过滤中，可分别填写 6 个精确包名。若使用当前的包名前缀语法覆盖整组应用，可配置：

```text
com.xcheng.xcloggertestdemo,com.xcheng.xcloggertestdemo.
```

第一项匹配原 Demo，第二项末尾的点用于匹配 `fork1` 到 `fork5`。
