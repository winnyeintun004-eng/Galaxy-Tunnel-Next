package com.dev7dev.v2ray;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class V2rayController {

    public enum ConnectionState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED
    }

    private static String appName = "";
    private static int iconResId = 0;

    public static void init(Context context, int iconResourceId, String applicationName) {
        iconResId = iconResourceId;
        appName = applicationName;
    }

    public static String getCoreVersion() {
        return "Xray-core v1.8.4";
    }

    public static ConnectionState getConnectionState() {
        return ConnectionState.DISCONNECTED;
    }

    public static void startV2ray(Context context, String remark, String config, String packageName) {
        Intent intent = new Intent(context, V2RayVpnService.class);
        intent.setAction("START");
        intent.putExtra("REMARK", remark);
        intent.putExtra("CONFIG", config);
        intent.putExtra("PACKAGE", packageName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopV2ray(Context context) {
        Intent intent = new Intent(context, V2RayVpnService.class);
        intent.setAction("STOP");
        context.startService(intent);
    }
}
