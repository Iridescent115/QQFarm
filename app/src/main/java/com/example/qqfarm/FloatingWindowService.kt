package com.example.qqfarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager

    // 主控制胶囊
    private lateinit var pillView: View
    private lateinit var pillParams: WindowManager.LayoutParams
    private var pillInitX = 0; private var pillInitY = 0
    private var pillTouchX = 0f; private var pillTouchY = 0f
    private var pillDragging = false

    // 日志窗口
    private var logView: View? = null
    private var logParams: WindowManager.LayoutParams? = null
    private var logInitX = 0; private var logInitY = 0
    private var logTouchX = 0f; private var logTouchY = 0f
    private var logDragging = false
    private var isLogAdded = false

    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createPillWindow()
        createLogWindow()
    }

    private fun startForegroundWithNotification() {
        val channelId = "floating_window_channel"
        val channel = NotificationChannel(channelId, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("QQFarm 运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    private fun createPillWindow() {
        pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 120
        }

        pillView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)

        val btnStartPause = pillView.findViewById<TextView>(R.id.btn_start_pause)
        val btnStop       = pillView.findViewById<TextView>(R.id.btn_stop)
        val btnLog        = pillView.findViewById<TextView>(R.id.btn_log)
        val btnExit       = pillView.findViewById<TextView>(R.id.btn_exit)

        btnStartPause.setOnClickListener {
            if (!AutoClickService.isConnected()) {
                Toast.makeText(this, "请先在设置→无障碍中开启 QQFarm 服务", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            isRunning = !isRunning
            if (isRunning) {
                btnStartPause.text = "⏸"
                btnStartPause.setTextColor(Color.parseColor("#FFC107"))
                AutoClickService.instance?.startAutoClick()
            } else {
                btnStartPause.text = "▶"
                btnStartPause.setTextColor(Color.parseColor("#4CAF50"))
                AutoClickService.instance?.stopAutoClick()
            }
        }

        btnStop.setOnClickListener {
            isRunning = false
            btnStartPause.text = "▶"
            btnStartPause.setTextColor(Color.parseColor("#4CAF50"))
            AutoClickService.instance?.stopAutoClick()
            Logger.log("⏹ 已停止")
        }

        btnLog.setOnClickListener {
            if (isLogAdded) hideLogWindow() else showLogWindow()
        }

        btnExit.setOnClickListener { stopSelf() }

        // 拖动：区分点击和拖动
        pillView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pillInitX = pillParams.x; pillInitY = pillParams.y
                    pillTouchX = event.rawX; pillTouchY = event.rawY
                    pillDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - pillTouchX
                    val dy = event.rawY - pillTouchY
                    if (!pillDragging && (abs(dx) > 8 || abs(dy) > 8)) pillDragging = true
                    if (pillDragging) {
                        pillParams.x = pillInitX + dx.toInt()
                        pillParams.y = pillInitY + dy.toInt()
                        windowManager.updateViewLayout(pillView, pillParams)
                        true
                    } else false
                }
                MotionEvent.ACTION_UP -> { pillDragging = false; false }
                else -> false
            }
        }

        windowManager.addView(pillView, pillParams)
    }

    private fun createLogWindow() {
        logParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 220
        }

        logView = LayoutInflater.from(this).inflate(R.layout.floating_log, null)
        val logDragHandle = logView!!.findViewById<View>(R.id.log_drag_handle)
        val tvLog         = logView!!.findViewById<TextView>(R.id.tv_log)
        val scrollLog     = logView!!.findViewById<ScrollView>(R.id.scroll_log)
        val btnClear      = logView!!.findViewById<TextView>(R.id.btn_clear_log)

        Logger.onUpdate = { text ->
            tvLog.post {
                tvLog.text = text
                scrollLog.post { scrollLog.fullScroll(View.FOCUS_UP) }
            }
        }

        btnClear.setOnClickListener { Logger.clear() }

        logDragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    logInitX = logParams!!.x; logInitY = logParams!!.y
                    logTouchX = event.rawX; logTouchY = event.rawY
                    logDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - logTouchX
                    val dy = event.rawY - logTouchY
                    if (!logDragging && (abs(dx) > 8 || abs(dy) > 8)) logDragging = true
                    if (logDragging) {
                        logParams!!.x = logInitX + dx.toInt()
                        logParams!!.y = logInitY + dy.toInt()
                        windowManager.updateViewLayout(logView!!, logParams!!)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { logDragging = false; true }
                else -> false
            }
        }
    }

    private fun showLogWindow() {
        if (!isLogAdded && logView != null) {
            windowManager.addView(logView, logParams)
            isLogAdded = true
        }
        pillView.findViewById<TextView>(R.id.btn_log)
            .setTextColor(Color.parseColor("#42A5F5"))
    }

    private fun hideLogWindow() {
        if (isLogAdded && logView != null) {
            windowManager.removeView(logView)
            isLogAdded = false
        }
        pillView.findViewById<TextView>(R.id.btn_log)
            .setTextColor(Color.parseColor("#90CAF9"))
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.onUpdate = null
        AutoClickService.instance?.stopAutoClick()
        if (isLogAdded) logView?.let { windowManager.removeView(it) }
        if (::pillView.isInitialized) windowManager.removeView(pillView)
    }
}
