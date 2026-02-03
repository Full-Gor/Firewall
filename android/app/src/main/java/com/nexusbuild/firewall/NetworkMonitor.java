package com.nexusbuild.firewall;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";
    private static NetworkMonitor instance;

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private boolean isWifi = false;
    private boolean isMobile = false;
    private boolean isConnected = false;

    public interface NetworkStateListener {
        void onNetworkStateChanged(boolean isConnected, boolean isWifi, boolean isMobile);
    }

    private NetworkStateListener listener;

    private NetworkMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
    }

    public static synchronized NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkMonitor(context);
        }
        return instance;
    }

    public void setListener(NetworkStateListener listener) {
        this.listener = listener;
    }

    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateNetworkState();
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateNetworkState();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                updateNetworkState();
            }
        });
    }

    private void updateNetworkState() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            isConnected = false;
            isWifi = false;
            isMobile = false;
        } else {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (capabilities != null) {
                isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                isMobile = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            }
        }

        Log.d(TAG, "Network state: connected=" + isConnected + ", wifi=" + isWifi + ", mobile=" + isMobile);

        if (listener != null) {
            listener.onNetworkStateChanged(isConnected, isWifi, isMobile);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isWifi() {
        return isWifi;
    }

    public boolean isMobile() {
        return isMobile;
    }
}
