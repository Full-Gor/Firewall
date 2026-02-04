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
            // Stratégie: utiliser addAllowedApplication pour les apps NON bloquées
            // Les apps bloquées ne sont pas ajoutées = pas de route = pas d'internet
            List<AppRule> appRules = ruleManager.getAppRules();
            int blockedCount = 0;

            // Récupérer toutes les apps installées
            List<android.content.pm.ApplicationInfo> installedApps =
                context.getPackageManager().getInstalledApplications(0);

            for (android.content.pm.ApplicationInfo appInfo : installedApps) {
                String pkg = appInfo.packageName;
                if (pkg.equals(context.getPackageName())) continue; // Skip notre app

                // Vérifier si l'app est bloquée
                boolean isBlocked = false;
                for (AppRule rule : appRules) {
                    if (rule.getPackageName().equals(pkg) && rule.isBlocked()) {
                        isBlocked = true;
                        blockedCount++;
                        Log.i(TAG, "Blocking app: " + pkg);
                        break;
                    }
                }

                // Si non bloquée, l'ajouter au VPN (elle aura internet via VPN)
                if (!isBlocked) {
                    try {
                        builder.addAllowedApplication(pkg);
                    } catch (PackageManager.NameNotFoundException e) {
                        // App désinstallée entre temps
                    }
                }
                // Si bloquée: pas ajoutée = pas de route réseau = pas d'internet
            }

            Log.i(TAG, "VPN configured - " + blockedCount + " apps blocked, using AdGuard DNS");

            return builder.establish();
        } catch (Exception e) {
            Log.e(TAG, "Error building VPN tunnel", e);
            return null;
        }
    }
}
