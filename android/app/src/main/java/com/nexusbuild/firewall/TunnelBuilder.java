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
    private static final int VPN_PREFIX = 32;
    private static final int MTU = 1500;

    // DNS servers bloquants (StevenBlack/AdGuard style)
    private static final String DNS_PRIMARY = "94.140.14.14";    // AdGuard DNS
    private static final String DNS_SECONDARY = "94.140.15.15";  // AdGuard DNS 2

    private final VpnService vpnService;
    private final Context context;
    private final RuleManager ruleManager;

    public TunnelBuilder(VpnService service) {
        this.vpnService = service;
        this.context = service.getApplicationContext();
        this.ruleManager = RuleManager.getInstance(context);
    }

    public ParcelFileDescriptor build() {
        try {
            VpnService.Builder builder = vpnService.new Builder();

            builder.setSession("Fire Firewall")
                   .setMtu(MTU)
                   .addAddress(VPN_ADDRESS, VPN_PREFIX)
                   .addRoute(VPN_ROUTE, 0)           // Route tout le trafic
                   .addDnsServer(DNS_PRIMARY)        // AdGuard DNS (bloque pubs/trackers)
                   .addDnsServer(DNS_SECONDARY)
                   .setBlocking(true);

            // Exclure notre app
            try {
                builder.addDisallowedApplication(context.getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Could not exclude own package");
            }

            // BLOQUER les apps marquées dans RuleManager
            List<AppRule> blockedApps = ruleManager.getAppRules();
            int blockedCount = 0;
            for (AppRule rule : blockedApps) {
                if (rule.isBlocked()) {
                    try {
                        // addDisallowedApplication = app exclue du VPN = PAS d'internet
                        // car route 0.0.0.0/0 envoie tout dans le VPN qui drop le trafic
                        builder.addDisallowedApplication(rule.getPackageName());
                        blockedCount++;
                        Log.i(TAG, "Blocking app: " + rule.getPackageName());
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "App not found: " + rule.getPackageName());
                    }
                }
            }

            Log.i(TAG, "VPN configured - " + blockedCount + " apps blocked, using AdGuard DNS");

            return builder.establish();
        } catch (Exception e) {
            Log.e(TAG, "Error building VPN tunnel", e);
            return null;
        }
    }
}
