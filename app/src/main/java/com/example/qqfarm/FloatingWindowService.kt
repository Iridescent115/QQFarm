package com.example.qqfarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
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

    // 坐标配置窗口
    private var configView: View? = null
    private var configParams: WindowManager.LayoutParams? = null
    private var configInitX = 0; private var configInitY = 0
    private var configTouchX = 0f; private var configTouchY = 0f
    private var configDragging = false
    private var isConfigAdded = false
    private val coordinateTextViews = mutableMapOf<String, TextView>()
    private val scriptOptionTextViews = mutableMapOf<AutomationScript, TextView>()
    private var stealVegetableConfigView: View? = null
    private var addFriendConfigView: View? = null

    private var isRunning = false

    private val pointCapturedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PointCaptureService.ACTION_POINT_CAPTURED) return
            refreshConfigCoordinates()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerPointCapturedReceiver()
        createPillWindow()
        createLogWindow()
        createConfigWindow()
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

        val btnStartPause = pillView.findViewById<ImageButton>(R.id.btn_start_pause)
        val btnStop       = pillView.findViewById<ImageButton>(R.id.btn_stop)
        val btnLog        = pillView.findViewById<ImageButton>(R.id.btn_log)
        val btnConfig     = pillView.findViewById<ImageButton>(R.id.btn_config_float)
        val btnExit       = pillView.findViewById<ImageButton>(R.id.btn_exit)

        btnStartPause.setOnClickListener {
            if (!AutoClickService.isConnected()) {
                Toast.makeText(this, "请先在设置→无障碍中开启 QQFarm 服务", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            isRunning = !isRunning
            if (isRunning) {
                btnStartPause.setImageResource(R.drawable.ic_pause_24)
                tintIcon(btnStartPause, "#FFC107")
                AutoClickService.instance?.startAutoClick()
            } else {
                btnStartPause.setImageResource(R.drawable.ic_play_24)
                tintIcon(btnStartPause, "#4CAF50")
                AutoClickService.instance?.stopAutoClick()
            }
        }

        btnStop.setOnClickListener {
            isRunning = false
            btnStartPause.setImageResource(R.drawable.ic_play_24)
            tintIcon(btnStartPause, "#4CAF50")
            AutoClickService.instance?.stopAutoClick()
            Logger.log("⏹ 已停止")
        }

        btnLog.setOnClickListener {
            if (isLogAdded) hideLogWindow() else showLogWindow()
        }

        btnConfig.setOnClickListener {
            if (isConfigAdded) hideConfigWindow() else showConfigWindow()
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

    private fun tintIcon(button: ImageButton, color: String) {
        button.imageTintList = ColorStateList.valueOf(Color.parseColor(color))
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
        tintIcon(pillView.findViewById(R.id.btn_log), "#42A5F5")
    }

    private fun hideLogWindow() {
        if (isLogAdded && logView != null) {
            windowManager.removeView(logView)
            isLogAdded = false
        }
        tintIcon(pillView.findViewById(R.id.btn_log), "#90CAF9")
    }

    private fun createConfigWindow() {
        configParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 520
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE212121"))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp, 8.dp, 8.dp, 8.dp)
            setBackgroundColor(Color.parseColor("#99111111"))
        }

        val title = TextView(this).apply {
            text = "脚本配置"
            setTextColor(Color.parseColor("#CE93D8"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val close = TextView(this).apply {
            text = "关闭"
            setTextColor(Color.parseColor("#BDBDBD"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(12.dp, 6.dp, 4.dp, 6.dp)
            setOnClickListener { hideConfigWindow() }
        }

        header.addView(title)
        header.addView(close)
        root.addView(header, LinearLayout.LayoutParams(340.dp, LinearLayout.LayoutParams.WRAP_CONTENT))

        scriptOptionTextViews.clear()
        root.addView(createSectionTitle("功能选择"))
        root.addView(createScriptSelector())

        stealVegetableConfigView = createStealVegetableConfigArea()
        root.addView(stealVegetableConfigView)

        addFriendConfigView = createAddFriendConfigArea()
        root.addView(addFriendConfigView)

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    configInitX = configParams!!.x; configInitY = configParams!!.y
                    configTouchX = event.rawX; configTouchY = event.rawY
                    configDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - configTouchX
                    val dy = event.rawY - configTouchY
                    if (!configDragging && (abs(dx) > 8 || abs(dy) > 8)) configDragging = true
                    if (configDragging) {
                        configParams!!.x = configInitX + dx.toInt()
                        configParams!!.y = configInitY + dy.toInt()
                        windowManager.updateViewLayout(configView!!, configParams!!)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { configDragging = false; true }
                else -> false
            }
        }

        configView = root
        refreshScriptSelection()
        refreshConfigCoordinates()
    }

    private fun createSectionTitle(text: String): View {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#FFC107"))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(12.dp, 10.dp, 12.dp, 4.dp)
        }
    }

    private fun createScriptSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp, 4.dp, 12.dp, 8.dp)
        }

        AutomationScript.entries.forEach { script ->
            val option = TextView(this).apply {
                text = script.label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(12.dp, 7.dp, 12.dp, 7.dp)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (script == AutomationScript.STEAL_VEGETABLE) 8.dp else 0
                }
                setOnClickListener {
                    AutomationScriptConfig.saveSelected(this@FloatingWindowService, script)
                    Logger.log("已选择功能：${script.label}")
                    refreshScriptSelection()
                }
            }
            scriptOptionTextViews[script] = option
            row.addView(option)
        }

        return row
    }

    private fun createStealVegetableConfigArea(): View {
        val area = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        area.addView(createSectionTitle("自动偷菜配置"))
        coordinateTextViews.clear()
        CoordinateConfig.points.forEach { point ->
            area.addView(createConfigRow(point))
        }
        return area
    }

    private fun createAddFriendConfigArea(): View {
        val area = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        area.addView(createSectionTitle("自动加好友配置"))
        area.addView(createAddFriendPlaceholder())
        return area
    }

    private fun createConfigRow(point: ClickPoint): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp, 7.dp, 10.dp, 7.dp)
        }

        val label = TextView(this).apply {
            text = point.label
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val coordinate = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(88.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        coordinateTextViews[point.key] = coordinate

        val capture = TextView(this).apply {
            text = "抓取"
            setTextColor(Color.parseColor("#90CAF9"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(10.dp, 6.dp, 0, 6.dp)
            setOnClickListener { startPointCapture(point) }
        }

        row.addView(label)
        row.addView(coordinate)
        row.addView(capture)
        return row
    }

    private fun createAddFriendPlaceholder(): View {
        return TextView(this).apply {
            text = "自动加好友的配置项待补充"
            setTextColor(Color.parseColor("#BDBDBD"))
            textSize = 12f
            setPadding(12.dp, 7.dp, 12.dp, 12.dp)
        }
    }

    private fun refreshScriptSelection() {
        val selected = AutomationScriptConfig.loadSelected(this)
        scriptOptionTextViews.forEach { (script, textView) ->
            if (script == selected) {
                textView.setTextColor(Color.parseColor("#212121"))
                textView.setBackgroundColor(Color.parseColor("#FFC107"))
            } else {
                textView.setTextColor(Color.WHITE)
                textView.setBackgroundColor(Color.parseColor("#44333333"))
            }
        }
        stealVegetableConfigView?.visibility =
            if (selected == AutomationScript.STEAL_VEGETABLE) View.VISIBLE else View.GONE
        addFriendConfigView?.visibility =
            if (selected == AutomationScript.ADD_FRIEND) View.VISIBLE else View.GONE
    }

    private fun refreshConfigCoordinates() {
        coordinateTextViews.forEach { (key, textView) ->
            val (x, y) = CoordinateConfig.loadPoint(this, key)
            textView.text = "${x.toInt()}, ${y.toInt()}"
        }
    }

    private fun startPointCapture(point: ClickPoint) {
        hideLogWindow()
        Toast.makeText(this, "请点击「${point.label}」的位置", Toast.LENGTH_SHORT).show()
        startService(
            Intent(this, PointCaptureService::class.java).apply {
                putExtra(PointCaptureService.EXTRA_TARGET_KEY, point.key)
                putExtra(PointCaptureService.EXTRA_TARGET_LABEL, point.label)
                putExtra(PointCaptureService.EXTRA_SHOW_CONTROL, false)
            }
        )
    }

    private fun showConfigWindow() {
        if (!isConfigAdded && configView != null) {
            refreshConfigCoordinates()
            windowManager.addView(configView, configParams)
            isConfigAdded = true
        }
        tintIcon(pillView.findViewById(R.id.btn_config_float), "#E1BEE7")
    }

    private fun hideConfigWindow() {
        if (isConfigAdded && configView != null) {
            windowManager.removeView(configView)
            isConfigAdded = false
        }
        if (::pillView.isInitialized) {
            tintIcon(pillView.findViewById(R.id.btn_config_float), "#CE93D8")
        }
    }

    private fun registerPointCapturedReceiver() {
        val filter = IntentFilter(PointCaptureService.ACTION_POINT_CAPTURED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pointCapturedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pointCapturedReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pointCapturedReceiver)
        Logger.onUpdate = null
        AutoClickService.instance?.stopAutoClick()
        if (isConfigAdded) configView?.let { windowManager.removeView(it) }
        if (isLogAdded) logView?.let { windowManager.removeView(it) }
        if (::pillView.isInitialized) windowManager.removeView(pillView)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
