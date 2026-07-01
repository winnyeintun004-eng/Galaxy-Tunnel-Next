package com.dev7dev.v2ray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.core.app.NotificationCompat;

public class V2RayVpnService extends VpnService {
    
    private static final String CHANNEL_ID = "v2ray_vpn_channel";
    private static final int NOTIFICATION_ID = 1;
    private ParcelFileDescriptor vpnInterface;
    
    @Override
    public IBinder onBind(Intent intent) { return null; }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        
        String action = intent.getAction();
        if ("START".equals(action)) {
            String config = intent.getStringExtra("CONFIG");
            String remark = intent.getStringExtra("REMARK");
            startVpn(config, remark);
        } else if ("STOP".equals(action)) {
            stopVpn();
        }
        return START_NOT_STICKY;
    }
    
    private void startVpn(String config, String remark) {
        Builder builder = new Builder();
        builder.addAddress("10.0.0.2", 32);
        builder.addRoute("0.0.0.0", 0);
        builder.addDnsServer("1.1.1.1");
        builder.setMtu(1500);
        builder.setSession(remark != null ? remark : "GalaxyTunnel");
        
        vpnInterface = builder.establish();
        
        Notification notification = createNotification(remark);
        startForeground(NOTIFICATION_ID, notification);
    }
    
    private void stopVpn() {
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
    }
    
    private Notification createNotification(String remark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, new Intent(this, V2RayVpnService.class),
            PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GalaxyTunnel VPN")
            .setContentText(remark != null ? "Connected: " + remark : "VPN Active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
}
