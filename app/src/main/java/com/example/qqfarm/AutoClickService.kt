package com.example.qqfarm

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

@SuppressLint("AccessibilityService")
class AutoClickService : AccessibilityService() {

    companion object {
        var instance: AutoClickService? = null
        fun isConnected() = instance != null
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scriptJob: Job? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopAutoClick() }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
    }

    fun startAutoClick() {
        if (scriptJob?.isActive == true) return
        scriptJob = scope.launch { runScript() }
    }

    fun stopAutoClick() {
        scriptJob?.cancel()
        scriptJob = null
    }

    private suspend fun runScript() {
        Logger.log("加载模板图片...")
        val template1 = loadTemplate("hand_1.png") ?: run {
            Logger.log("❌ 找不到 hand_1.png，请放入 assets 目录")
            return
        }
        val template2 = loadTemplate("hand_2.png") ?: run {
            Logger.log("❌ 找不到 hand_2.png，请放入 assets 目录")
            return
        }
        Logger.log("模板加载成功，开始循环")

        while (currentCoroutineContext().isActive) {
            try {
                val coordinates = CoordinateConfig.loadAll(this)

                // 步骤 1
                Logger.log("步骤1: 点击打开好友列表")
                click(coordinates.openFriends)

                // 步骤 2
                Logger.log("步骤2: 等待 0.8 秒")
                delay(800)

                // 步骤 3
                Logger.log("步骤3: 截图识别 hand_1...")
                val shot1 = screenshot() ?: run { Logger.log("截图失败，跳过"); continue }
                val result1 = ImageMatcher.find(shot1, template1)
                Logger.log("hand_1 置信度: ${"%.3f".format(result1.confidence)}${if (result1.found) " ✓" else " ✗"}")

                if (!result1.found) {
                    Logger.log("未识别到 hand_1，关闭好友列表回家")
                    click(coordinates.closeFriends)
                    delay(1000)
                    continue
                }

                // 步骤 4
                Logger.log("步骤4: 拜访第一个好友")
                click(coordinates.visitFirst)
                delay(500)

                Logger.log("步骤4: 截图识别 hand_2...")
                val shot2 = screenshot()
                if (shot2 != null) {
                    val result2 = ImageMatcher.find(shot2, template2)
                    Logger.log("hand_2 置信度: ${"%.3f".format(result2.confidence)}${if (result2.found) " ✓" else " ✗"}")
                    if (result2.found) {
                        Logger.log("识别到 hand_2，点击 (${result2.centerX}, ${result2.centerY})")
                        click(result2.centerX.toFloat() to result2.centerY.toFloat())
                    }
                }

                // 步骤 5
                delay(500)
                Logger.log("步骤5: 返回自己家")
                click(coordinates.goHome)

                delay(1000)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Logger.log("⚠ 发生异常，2秒后重试")
                delay(2000)
            }
        }
    }

    private fun loadTemplate(name: String): Bitmap? = runCatching {
        assets.open(name).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun screenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    result.hardwareBuffer.close()
                    cont.resumeWith(Result.success(bmp))
                }
                override fun onFailure(errorCode: Int) {
                    cont.resumeWith(Result.success(null))
                }
            }
        )
    }

    private fun click(coord: Pair<Float, Float>) {
        val path = Path().apply { moveTo(coord.first, coord.second) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
