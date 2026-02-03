package com.nexusbuild.firewall;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class TunnelBuilder {
    private static final String TAG = "TunnelBuilder";
    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final int VPN_PREFIX = 0;
    private static final String DNS_SERVER = "8.8.8.8";
    private static final int MTU = 1500;

    private final VpnService vpnService;

    public TunnelBuilder(VpnService service) {
        this.vpnService = service;
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

            // Allow apps to bypass VPN if needed
            // builder.allowBypass();

            return builder.establish();
        } catch (Exception e) {
            Log.e(TAG, "Error building VPN tunnel", e);
            return null;
        }
    }
}
