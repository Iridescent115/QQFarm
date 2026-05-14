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

        showDisclaimerIfNeeded()
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
