package com.nexusbuild.firewall;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.util.List;

public class TunnelBuilder {
    private static final String TAG = "TunnelBuilder";
    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final int VPN_PREFIX = 0;
    private static final String DNS_SERVER = "8.8.8.8";
    private static final int MTU = 1500;

    private final VpnService vpnService;
    private final Context context;

    public TunnelBuilder(VpnService service) {
        this.vpnService = service;
        this.context = service.getApplicationContext();
    }

    public ParcelFileDescriptor build() {
        try {
            VpnService.Builder builder = vpnService.new Builder();

            builder.setSession("Fire Firewall")
                   .setMtu(MTU)
                   .addAddress(VPN_ADDRESS, 32)
                   .addRoute(VPN_ROUTE, VPN_PREFIX)
                   .addDnsServer(DNS_SERVER)
                   .setBlocking(true);

            // Get blocked apps from RuleManager
            RuleManager ruleManager = RuleManager.getInstance(context);
            List<AppRule> appRules = ruleManager.getAppRules();

            boolean hasBlockedApps = false;
            for (AppRule rule : appRules) {
                if (rule.isBlocked()) {
                    hasBlockedApps = true;
                    break;
                }
            }

            if (hasBlockedApps) {
                // Route ONLY blocked apps through VPN (others bypass)
                for (AppRule rule : appRules) {
                    if (rule.isBlocked()) {
                        try {
                            builder.addAllowedApplication(rule.getPackageName());
                            Log.i(TAG, "Blocking app: " + rule.getPackageName());
                        } catch (PackageManager.NameNotFoundException e) {
                            Log.w(TAG, "Package not found: " + rule.getPackageName());
                        }
                    }
                }
            }
            // If no apps are blocked, VPN only does DNS filtering for all apps

            return builder.establish();
        } catch (Exception e) {
            Log.e(TAG, "Error building VPN tunnel", e);
            return null;
        }
    }
}
