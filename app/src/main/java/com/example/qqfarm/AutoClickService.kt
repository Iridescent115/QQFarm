package com.example.qqfarm

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
        val script = AutomationScriptConfig.loadSelected(this)
        scriptJob = scope.launch {
            when (script) {
                AutomationScript.STEAL_VEGETABLE -> runStealVegetableScript()
                AutomationScript.ADD_FRIEND -> runAddFriendScript()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (scriptJob == job) scriptJob = null
            }
        }
    }

    fun stopAutoClick() {
        scriptJob?.cancel()
        scriptJob = null
    }

    private suspend fun runStealVegetableScript() {
        Logger.log("启动脚本：自动偷菜")
        Logger.log("加载模板图片 hand_1.png / hand_2.png...")
        val template1 = loadTemplate("hand_1.png") ?: run {
            Logger.log("找不到 hand_1.png，请放入 assets 目录")
            return
        }
        val template2 = loadTemplate("hand_2.png") ?: run {
            Logger.log("找不到 hand_2.png，请放入 assets 目录")
            return
        }
        Logger.log("模板加载成功，开始自动偷菜循环")

        while (currentCoroutineContext().isActive) {
            try {
                val coordinates = CoordinateConfig.loadAll(this)

                Logger.log("自动偷菜：打开好友列表")
                click(coordinates.openFriends)

                Logger.log("自动偷菜：等待 0.8 秒")
                delay(800)

                Logger.log("自动偷菜：截图识别 hand_1")
                val shot1 = screenshot()
                if (shot1 == null) {
                    Logger.log("截图失败，跳过本轮")
                    continue
                }
                val result1 = ImageMatcher.find(shot1, template1)
                Logger.log(
                    "hand_1 置信度: ${"%.3f".format(result1.confidence)}${
                        if (result1.found) " 已找到" else " 未找到"
                    }"
                )

                if (!result1.found) {
                    Logger.log("未识别到 hand_1，关闭好友列表")
                    click(coordinates.closeFriends)
                    delay(1000)
                    continue
                }

                Logger.log("自动偷菜：拜访第一个好友")
                click(coordinates.visitFirst)
                delay(500)

                Logger.log("自动偷菜：截图识别 hand_2")
                val shot2 = screenshot()
                if (shot2 != null) {
                    val result2 = ImageMatcher.find(shot2, template2)
                    Logger.log(
                        "hand_2 置信度: ${"%.3f".format(result2.confidence)}${
                            if (result2.found) " 已找到" else " 未找到"
                        }"
                    )
                    if (result2.found) {
                        Logger.log("识别到 hand_2，点击 (${result2.centerX}, ${result2.centerY})")
                        click(result2.centerX.toFloat() to result2.centerY.toFloat())
                    }
                }

                delay(500)
                Logger.log("自动偷菜：返回自己家")
                click(coordinates.goHome)

                delay(1000)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Logger.log("自动偷菜发生异常，2秒后重试")
                delay(2000)
            }
        }
    }

    private suspend fun runAddFriendScript() {
        Logger.log("启动脚本：自动加好友")
        Logger.log("加载模板图片 card.png / close.png...")
        val cardTemplate = loadTemplate("card.png") ?: run {
            Logger.log("找不到 card.png，请放入 assets 目录")
            return
        }
        val closeTemplate = loadTemplate("close.png") ?: run {
            Logger.log("找不到 close.png，请放入 assets 目录")
            return
        }
        Logger.log("模板加载成功，开始自动加好友循环")

        while (currentCoroutineContext().isActive) {
            try {
                val shot = screenshot()
                if (shot == null) {
                    Logger.log("截图失败，2秒后重试")
                    delay(2000)
                    continue
                }

                val cardMatches = ImageMatcher.findAll(shot, cardTemplate)
                val bottomCard = cardMatches.maxByOrNull { it.centerY }
                if (bottomCard == null) {
                    Logger.log("未识别到 card.png，向下滑动聊天记录")
                    swipeDownAndWait(shot.width, shot.height)
                    continue
                }

                Logger.log(
                    "识别到 ${cardMatches.size} 个 card.png，点击最下面的 (${bottomCard.centerX}, ${bottomCard.centerY})，置信度 ${
                        "%.3f".format(bottomCard.confidence)
                    }"
                )
                click(bottomCard.centerX.toFloat() to bottomCard.centerY.toFloat())
                delay(1000)

                val close = findCloseButton(closeTemplate, attempt = 1)
                    ?: run {
                        Logger.log("第一次未识别到 close.png，等待1秒后重试")
                        delay(1000)
                        findCloseButton(closeTemplate, attempt = 2)
                    }

                if (close == null) {
                    Logger.log("连续两次未识别到 close.png，已暂停脚本")
                    return
                }

                Logger.log("点击 close.png (${close.centerX}, ${close.centerY})")
                click(close.centerX.toFloat() to close.centerY.toFloat())
                delay(500)

                val afterCloseShot = screenshot()
                if (afterCloseShot != null) {
                    Logger.log("关闭后向下滑动聊天记录")
                    swipeDownAndWait(afterCloseShot.width, afterCloseShot.height)
                } else {
                    Logger.log("关闭后截图失败，使用默认距离滑动")
                    swipeDownAndWait()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Logger.log("自动加好友发生异常，2秒后重试")
                delay(2000)
            }
        }
    }

    private suspend fun findCloseButton(template: Bitmap, attempt: Int): MatchResult? {
        val shot = screenshot()
        if (shot == null) {
            Logger.log("第 ${attempt} 次 close.png 检测截图失败")
            return null
        }
        val result = ImageMatcher.find(shot, template)
        Logger.log(
            "第 ${attempt} 次 close.png 置信度: ${"%.3f".format(result.confidence)}${
                if (result.found) " 已找到" else " 未找到"
            }"
        )
        return result.takeIf { it.found }
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

    private fun swipeDown(screenWidth: Int = 1260, screenHeight: Int = 2720) {
        val x = screenWidth * 0.5f
        val startY = screenHeight * 0.42f
        val endY = screenHeight * 0.585f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 350)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private suspend fun swipeDownAndWait(screenWidth: Int = 1260, screenHeight: Int = 2720) {
        swipeDown(screenWidth, screenHeight)
        delay(1350)
    }
}
