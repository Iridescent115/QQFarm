package com.example.qqfarm

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

class PointCaptureService : Service() {

    companion object {
        const val ACTION_POINT_CAPTURED = "com.example.qqfarm.ACTION_POINT_CAPTURED"
        const val EXTRA_TARGET_KEY = "target_key"
        const val EXTRA_TARGET_LABEL = "target_label"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_SHOW_CONTROL = "show_control"
    }

    private lateinit var windowManager: WindowManager
    private var controlView: View? = null
    private var captureView: View? = null
    private var markerView: View? = null
    private var targetKey = CoordinateConfig.KEY_OPEN_FRIENDS
    private var targetLabel = "打开好友列表"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetKey = intent?.getStringExtra(EXTRA_TARGET_KEY) ?: CoordinateConfig.KEY_OPEN_FRIENDS
        targetLabel = intent?.getStringExtra(EXTRA_TARGET_LABEL) ?: "打开好友列表"
        if (intent?.getBooleanExtra(EXTRA_SHOW_CONTROL, true) == false) {
            showCaptureWindow()
        } else {
            showControlWindow()
        }
        return START_NOT_STICKY
    }

    private fun showControlWindow() {
        removeControlWindow()
        removeCaptureWindow()
        removeMarkerWindow()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp, 8.dp, 8.dp, 8.dp)
            setBackgroundColor(Color.parseColor("#DD212121"))
        }

        val title = TextView(this).apply {
            text = "配置：$targetLabel"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 10.dp, 0)
        }
        val captureButton = TextView(this).apply {
            text = "抓屏幕"
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
        }
        val closeButton = TextView(this).apply {
            text = "关闭"
            setTextColor(Color.parseColor("#BDBDBD"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10.dp, 8.dp, 4.dp, 8.dp)
        }

        layout.addView(title)
        layout.addView(captureButton)
        layout.addView(closeButton)
        controlView = layout

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 360
        }

        captureButton.setOnClickListener {
            Toast.makeText(this, "请点击要记录的位置", Toast.LENGTH_SHORT).show()
            showCaptureWindow()
        }
        closeButton.setOnClickListener { stopSelf() }

        windowManager.addView(controlView, params)
    }

    private fun showCaptureWindow() {
        removeControlWindow()
        removeCaptureWindow()
        removeMarkerWindow()

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#22000000"))
            setPadding(20.dp, 40.dp, 20.dp, 0)

            val hint = TextView(this@PointCaptureService).apply {
                text = "点击屏幕任意位置记录「$targetLabel」坐标"
                setTextColor(Color.WHITE)
                textSize = 14f
                setBackgroundColor(Color.parseColor("#CC212121"))
                setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            }
            addView(hint)
        }

        overlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                saveCapturedPoint(event.rawX, event.rawY)
                true
            } else {
                true
            }
        }
        captureView = overlay

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(captureView, params)
    }

    private fun saveCapturedPoint(rawX: Float, rawY: Float) {
        val x = rawX.toInt()
        val y = rawY.toInt()
        CoordinateConfig.savePoint(this, targetKey, x.toFloat(), y.toFloat())
        Logger.log("$targetLabel 坐标已更新：($x, $y)")
        sendBroadcast(
            Intent(ACTION_POINT_CAPTURED).apply {
                putExtra(EXTRA_TARGET_KEY, targetKey)
                putExtra(EXTRA_X, x)
                putExtra(EXTRA_Y, y)
            }
        )

        removeCaptureWindow()
        showMarkerWindow(x, y)
        Toast.makeText(this, "$targetLabel：($x, $y)", Toast.LENGTH_SHORT).show()
    }

    private fun showMarkerWindow(x: Int, y: Int) {
        removeMarkerWindow()

        val marker = TextView(this).apply {
            text = "($x, $y)"
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(Color.parseColor("#DD1976D2"))
            setPadding(8.dp, 5.dp, 8.dp, 5.dp)
        }
        markerView = marker

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = max(0, x + 12.dp)
            this.y = max(0, y - 28.dp)
        }

        windowManager.addView(markerView, params)
        marker.postDelayed({ stopSelf() }, 1800)
    }

    private fun removeControlWindow() {
        controlView?.let { windowManager.removeView(it) }
        controlView = null
    }

    private fun removeCaptureWindow() {
        captureView?.let { windowManager.removeView(it) }
        captureView = null
    }

    private fun removeMarkerWindow() {
        markerView?.let { windowManager.removeView(it) }
        markerView = null
    }

    override fun onDestroy() {
        removeControlWindow()
        removeCaptureWindow()
        removeMarkerWindow()
        super.onDestroy()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
