# QQFarm Agent 说明

## 项目概况

QQFarm 是一个单模块 Android/Kotlin 项目。它的主要用途是提供一个悬浮控制面板，并通过 `AccessibilityService` 实现截图、图像识别和自动点击。

核心行为：

1. 主界面负责申请悬浮窗权限，并提供跳转到无障碍设置的入口。
2. 前台悬浮窗服务显示开始、暂停、停止、日志和退出按钮。
3. 无障碍服务负责截图、OpenCV 模板匹配和派发点击手势。
4. `app/src/main/assets/` 下的 `hand_1.png` 和 `hand_2.png` 是当前识别用的模板图。

## 重要文件

- `app/src/main/java/com/example/qqfarm/MainActivity.kt`
  - App 入口界面。
  - 在获得悬浮窗权限后启动 `FloatingWindowService`。
  - 提供跳转系统无障碍设置的按钮。

- `app/src/main/java/com/example/qqfarm/FloatingWindowService.kt`
  - 前台服务。
  - 创建可拖动的悬浮控制胶囊。
  - 创建可拖动的日志窗口。
  - 调用 `AutoClickService.instance?.startAutoClick()` 和 `stopAutoClick()` 控制自动化流程。

- `app/src/main/java/com/example/qqfarm/AutoClickService.kt`
  - 无障碍服务。
  - 保存自动化主循环。
  - 从 assets 加载模板图。
  - 使用 `takeScreenshot` 截图。
  - 使用 `dispatchGesture` 派发点击。
  - 内部包含若干硬编码屏幕坐标。

- `app/src/main/java/com/example/qqfarm/ImageMatcher.kt`
  - OpenCV 封装。
  - 使用 `Imgproc.matchTemplate` 和 `TM_CCOEFF_NORMED` 做模板匹配。
  - 默认匹配阈值是 `0.8`。

- `app/src/main/java/com/example/qqfarm/Logger.kt`
  - 在内存中保存最近日志。
  - 通过 `Logger.onUpdate` 刷新悬浮日志窗口。

- `app/src/main/AndroidManifest.xml`
  - 声明悬浮窗、前台服务和无障碍服务相关权限。

- `app/src/main/res/xml/accessibility_service_config.xml`
  - 开启无障碍服务的手势能力和截图能力。

## 自动化流程

`AutoClickService.runScript()` 中的主循环大致如下：

1. 加载 `hand_1.png` 和 `hand_2.png`。
2. 点击硬编码的“打开好友列表”坐标。
3. 等待 800 ms。
4. 截图并查找 `hand_1`。
5. 如果没有找到 `hand_1`，关闭好友列表并进入下一轮。
6. 如果找到，点击硬编码的“拜访第一个好友”坐标。
7. 截图并查找 `hand_2`。
8. 如果找到 `hand_2`，点击匹配结果的中心点。
9. 点击硬编码的“回自己家”坐标。
10. 重复执行，直到被停止。

当前硬编码坐标是按 1260 x 2720 这类设备调出来的：

```kotlin
private val btnOpenFriends  = 1098f to 2566f
private val btnVisitFirst   = 1093f to 1177f
private val btnCloseFriends = 1169f to 651f
private val btnGoHome       = 1164f to 2127f
```

这些坐标强依赖设备分辨率和游戏 UI 布局。如果换设备后点击不准，优先检查这里。

## 设备权限要求

App 在设备上需要：

- 悬浮窗权限，也就是“显示在其他应用上层”。
- QQFarm 的无障碍权限。
- ADB 授权，用于命令行安装和调试。

悬浮窗可以在无障碍服务未开启时启动，但自动化流程只有在 `AutoClickService.isConnected()` 为 `true` 时才能启动。

## 本地工具链

当前机器上已验证可用的路径：

```powershell
$studio = "D:\Program Files\Android\Android Studio\bin\studio64.exe"
$jbr = "D:\Program Files\Android\Android Studio\jbr"
$adb = "D:\Program Files\Android\SDK\platform-tools\adb.exe"
```

执行 Gradle 命令前先设置 `JAVA_HOME`：

```powershell
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
```

## 编译和安装

在项目根目录执行：

```powershell
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

如果 Gradle 需要下载 wrapper 分发包或依赖，而沙箱网络拦截导致失败，需要用相同命令申请提权后重跑。

## ADB 调试

查看已连接设备：

```powershell
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" devices -l
```

之前验证过可用的无线设备：

```text
192.168.31.201:5555    device product:ALN-AL00 model:ALN_AL00
```

启动 App：

```powershell
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" shell am start -n com.example.qqfarm/.MainActivity
```

查看进程：

```powershell
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" shell pidof com.example.qqfarm
```

查看进程是否对 JDWP 可见：

```powershell
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" jdwp
```

查看关键日志：

```powershell
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" logcat -d -t 200 |
    Select-String -Pattern "qqfarm|AndroidRuntime|FATAL EXCEPTION|ActivityTaskManager"
```

查看当前前台 Activity：

```powershell
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" shell dumpsys activity top
```

用 Android Studio 打开当前项目：

```powershell
Start-Process -FilePath "D:\Program Files\Android\Android Studio\bin\studio64.exe" -ArgumentList "E:\Program\Android\QQFarm"
```

启动图形界面需要沙箱提权。

## Git 说明

仓库远端：

```text
origin https://github.com/Iridescent115/QQFarm.git
```

当前仓库本地 Git 配置了：

```text
core.excludesfile = .git/info/exclude
```

这是为了避免 Git 尝试读取 `C:\Users\11581\.config\git\ignore` 时出现本机权限警告。

已忽略的主要文件包括：

- `.gradle/`
- `.idea/`
- `.kotlin/`
- `local.properties`
- 所有 `build/` 目录
- APK/AAB/AAR/Dex 等构建产物
- keystore 和签名配置

## 已知风险和维护点

- `AutoClickService` 目前从 `Dispatchers.Main` 的 scope 中执行长期自动化流程。如果后续出现界面或服务响应卡顿，考虑把图像匹配移到非主线程。
- `Bitmap.wrapHardwareBuffer` 得到的截图在进入 OpenCV 前会在 `ImageMatcher` 中复制成普通 Bitmap，这是处理硬件 Bitmap 所必需的。
- `ImageMatcher.find()` 已释放 OpenCV `Mat` 对象，但复制出来的 Bitmap 没有显式 recycle。如果截图频率变高，需要关注内存。
- 模板匹配对缩放、主题变化和游戏 UI 变化敏感。如果识别置信度下降，需要更新 `hand_1.png` 和 `hand_2.png`。
- `FirstFragment`、`SecondFragment` 和导航模板资源看起来是 Android Studio 模板遗留代码，不属于当前主要业务流程。

## 常用端到端调试命令

```powershell
Set-Location "E:\Program\Android\QQFarm"
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" shell am start -n com.example.qqfarm/.MainActivity
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" shell pidof com.example.qqfarm
& "D:\Program Files\Android\SDK\platform-tools\adb.exe" logcat -d -t 200 |
    Select-String -Pattern "qqfarm|AndroidRuntime|FATAL EXCEPTION|ActivityTaskManager"
```
