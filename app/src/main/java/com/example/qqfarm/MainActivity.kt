package com.example.qqfarm

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private companion object {
        const val PREFS_NAME = "app_agreement"
        const val KEY_AGREED_DISCLAIMER = "agreed_disclaimer"
        const val DISCLAIMER_COUNTDOWN_SECONDS = 10
    }

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_launch).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_tutorial).setOnClickListener {
            showTutorialDialog()
        }

        showDisclaimerIfNeeded()
    }

    private fun showTutorialDialog() {
        AlertDialog.Builder(this)
            .setTitle("使用教学")
            .setMessage(
                """
                一、使用前准备

                1. 本工具目前只支持安卓微信端的 QQ 经典农场小程序。
                2. 使用前需要开启悬浮窗权限，用于显示控制面板、日志和配置界面。
                3. 使用前需要开启无障碍权限，用于执行截图识别、点击和滑动操作。
                4. 权限开启后，点击“启动悬浮窗”，再回到微信或游戏界面使用。

                二、悬浮窗按钮

                开始 / 暂停：启动或暂停当前选中的脚本。
                停止：立即停止当前脚本。
                日志：查看脚本运行状态和识别结果。
                配置：选择脚本，并设置脚本需要的参数或坐标。
                退出：关闭悬浮窗。

                三、自动偷菜

                自动偷菜功能需要在自己的农场界面开始运行。

                首次使用前，请先在悬浮窗配置界面选择“自动偷菜”，并依次抓取需要点击的位置。配置完成后，回到自己的农场界面，点击悬浮窗开始按钮运行。

                大致流程：
                1. 打开好友列表。
                2. 识别是否存在可操作目标。
                3. 拜访好友农场并执行对应点击。
                4. 返回自己的农场。
                5. 循环执行，直到手动暂停或停止。

                四、自动加好友

                自动加好友功能要点进微信群聊天界面之后再运行。

                进入微信群聊天界面后，选择“自动加好友”，再点击悬浮窗开始按钮。脚本会在聊天记录中查找好友卡片，识别到后点击，并按流程关闭相关页面。

                大致流程：
                1. 截图识别聊天记录中的好友卡片。
                2. 找到多个卡片时，优先点击最下面的一个。
                3. 点击后等待页面打开。
                4. 识别并点击关闭按钮。
                5. 向下滑动聊天记录，继续查找下一张好友卡片。
                6. 如果连续无法识别关闭按钮，脚本会暂停，避免持续误操作。
                """.trimIndent()
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showDisclaimerIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AGREED_DISCLAIMER, false)) return

        val dialog = AlertDialog.Builder(this)
            .setTitle("免责声明与权限说明")
            .setMessage(
                """
                欢迎使用农场小助手。

                本程序基于屏幕截图、图像识别和无障碍模拟点击等纯视觉方式运行，不读取、获取、修改或篡改目标应用的内部文件、内部数据、通信内容或服务器数据。

                本程序仅作为个人学习、研究和自用工具提供。用户应自行确认其使用行为符合相关法律法规、平台规则及目标应用的服务条款，并自行承担因使用本程序产生的全部风险与责任。

                本程序运行需要用户主动授予“显示在其他应用上层”的悬浮窗权限，以及用于截图识别、点击和滑动操作的无障碍权限。未授予上述权限时，相关功能将无法正常使用。

                点击“同意并继续”即表示你已阅读、理解并同意以上内容。
                """.trimIndent()
            )
            .setCancelable(false)
            .setPositiveButton("同意并继续") { _, _ ->
                prefs.edit()
                    .putBoolean(KEY_AGREED_DISCLAIMER, true)
                    .apply()
            }
            .setNegativeButton("不同意") { _, _ ->
                finish()
            }
            .show()

        startDisclaimerCountdown(dialog)
    }

    private fun startDisclaimerCountdown(dialog: AlertDialog) {
        val agreeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        var remainingSeconds = DISCLAIMER_COUNTDOWN_SECONDS

        val runnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return

                if (remainingSeconds > 0) {
                    agreeButton.isEnabled = false
                    agreeButton.text = "同意并继续（${remainingSeconds}秒）"
                    remainingSeconds--
                    countdownHandler.postDelayed(this, 1000)
                } else {
                    agreeButton.text = "同意并继续"
                    agreeButton.isEnabled = true
                }
            }
        }

        countdownRunnable = runnable
        dialog.setOnDismissListener {
            countdownHandler.removeCallbacks(runnable)
            if (countdownRunnable == runnable) {
                countdownRunnable = null
            }
        }
        runnable.run()
    }

    override fun onDestroy() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = null
        super.onDestroy()
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "请授予「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivity(intent)
    }
}
