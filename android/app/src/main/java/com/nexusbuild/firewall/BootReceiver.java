package com.nexusbuild.firewall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "fire_settings";
    private static final String KEY_START_ON_BOOT = "start_on_boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Boot completed, checking auto-start setting");

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, false);

            if (startOnBoot) {
                Log.i(TAG, "Auto-starting VPN service");
                Intent serviceIntent = new Intent(context, FirewallVpnService.class);
                serviceIntent.setAction(FirewallVpnService.ACTION_START);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
