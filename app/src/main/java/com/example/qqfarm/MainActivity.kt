package com.example.qqfarm

import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btn_launch)
        btnStart.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        // 从权限设置页返回后，如果已授权则自动启动
        if (Settings.canDrawOverlays(this)) {
            val btnStart = findViewById<Button>(R.id.btn_launch)
            btnStart.isEnabled = true
        }
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
