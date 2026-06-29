package com.galaxy.tunnel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.galaxy.tunnel.MainActivity

class V2RayService : VpnService() {

    private var mInterface: ParcelFileDescriptor? = null

    companion object {
        const val CHANNEL_ID = "galaxy_vpn_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.galaxy.tunnel.action.START"
        const val ACTION_STOP = "com.galaxy.tunnel.action.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra("V2RAY_CONFIG") ?: ""
                startVpn(configJson)
            }
            ACTION_STOP -> {
                stopVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn(configJson: String) {
        // 1. Establish TUN Interface (Android VPN Framework)
        try {
            val builder = Builder()
                .setSession("Galaxy Tunnel")
                .addAddress("172.19.0.1", 30) // Local VPN address
                .addRoute("0.0.0.0", 0)       // Route all traffic
                .addDnsServer("1.1.1.1")      // Cloudflare DNS
                .setMtu(1500)
            
            mInterface = builder.establish()
            
            // 2. Start V2Ray Core (libv2ray.aar)
            // Note: In actual implementation, you would call:
            // LibV2ray.startV2ray(this, configJson, mInterface?.fd ?: -1)
            
            Log.d("V2RayService", "VPN Interface established: ${mInterface?.fd}")
            
            startForeground(NOTIFICATION_ID, createNotification("Galaxy Tunnel Connected"))
        } catch (e: Exception) {
            Log.e("V2RayService", "Failed to start VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            mInterface?.close()
            mInterface = null
            // LibV2ray.stopV2ray()
        } catch (e: Exception) {
            Log.e("V2RayService", "Error stopping VPN", e)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Galaxy Tunnel")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
