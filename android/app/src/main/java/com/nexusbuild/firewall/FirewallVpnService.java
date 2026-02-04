package com.nexusbuild.firewall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class FirewallVpnService extends VpnService {
    private static final String TAG = "FirewallVpnService";
    private static final String CHANNEL_ID = "fire_vpn_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_START = "com.nexusbuild.firewall.START";
    public static final String ACTION_STOP = "com.nexusbuild.firewall.STOP";
    public static final String ACTION_RELOAD_RULES = "com.nexusbuild.firewall.RELOAD_RULES";

    private static volatile boolean running = false;
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private final AtomicBoolean shouldRun = new AtomicBoolean(false);

    private PacketFilter packetFilter;
    private DnsInterceptor dnsInterceptor;
    private ConnectionLogger connectionLogger;
    private DataUsageTracker dataUsageTracker;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        packetFilter = new PacketFilter(this);
        dnsInterceptor = new DnsInterceptor(this);
        connectionLogger = ConnectionLogger.getInstance(this);
        dataUsageTracker = DataUsageTracker.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            startVpn();
        } else if (ACTION_STOP.equals(action)) {
            stopVpn();
        } else if (ACTION_RELOAD_RULES.equals(action)) {
            reloadRules();
        }

        return START_STICKY;
    }

    private void startVpn() {
        if (running) {
            Log.w(TAG, "VPN already running");
            return;
        }

        try {
            vpnInterface = createVpnInterface();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to create VPN interface");
                return;
            }

            startForeground(NOTIFICATION_ID, createNotification());
            shouldRun.set(true);
            running = true;

            vpnThread = new Thread(this::runVpnLoop, "FirewallVpnThread");
            vpnThread.start();

            Log.i(TAG, "VPN started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting VPN", e);
            stopVpn();
        }
    }

    private void stopVpn() {
        shouldRun.set(false);
        running = false;

        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
            vpnInterface = null;
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        Log.i(TAG, "VPN stopped");
    }

    private void reloadRules() {
        if (packetFilter != null) {
            packetFilter.reloadRules();
        }
        if (dnsInterceptor != null) {
            dnsInterceptor.reloadBlockList();
        }

        // Restart VPN to apply new app rules (required for addAllowedApplication changes)
        if (running) {
            Log.i(TAG, "Restarting VPN to apply new app rules");
            restartVpn();
        }
    }

    private void restartVpn() {
        // Stop current VPN
        shouldRun.set(false);
        if (vpnThread != null) {
            vpnThread.interrupt();
            try {
                vpnThread.join(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            vpnThread = null;
        }
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
            vpnInterface = null;
        }

        // Restart with new rules
        try {
            vpnInterface = createVpnInterface();
            if (vpnInterface != null) {
                shouldRun.set(true);
                vpnThread = new Thread(this::runVpnLoop, "FirewallVpnThread");
                vpnThread.start();
                Log.i(TAG, "VPN restarted with new rules");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restarting VPN", e);
        }
    }

    private ParcelFileDescriptor createVpnInterface() {
        TunnelBuilder builder = new TunnelBuilder(this);
        return builder.build();
    }

    private void runVpnLoop() {
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());

        ByteBuffer packet = ByteBuffer.allocate(32767);

        while (shouldRun.get()) {
            try {
                packet.clear();
                int length = in.read(packet.array());

                if (length > 0) {
                    packet.limit(length);
                    processPacket(packet, out);
                }
            } catch (IOException e) {
                if (shouldRun.get()) {
                    Log.e(TAG, "Error in VPN loop", e);
                }
                break;
            }
        }
    }

    private void processPacket(ByteBuffer packet, FileOutputStream out) throws IOException {
        // Parse IP header
        int version = (packet.get(0) >> 4) & 0xF;
        if (version != 4) {
            // Only handle IPv4 for now
            return;
        }

        int protocol = packet.get(9) & 0xFF;
        int sourceIp = packet.getInt(12);
        int destIp = packet.getInt(16);

        // Get UID of the packet (requires API 29+)
        int uid = -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // UID detection would require kernel support
        }

        // Check DNS (UDP port 53)
        if (protocol == 17) { // UDP
            int destPort = packet.getShort(22) & 0xFFFF;
            if (destPort == 53) {
                // DNS request - intercept and filter
                ByteBuffer response = dnsInterceptor.processDnsRequest(packet);
                if (response != null) {
                    // Domain was blocked, send NXDOMAIN response
                    out.write(response.array(), 0, response.limit());
                    connectionLogger.logBlocked(destIp, destPort, uid, "DNS_BLOCKED");
                    return;
                }
            }
        }

        // Check if packet should be blocked by rules
        if (packetFilter.shouldBlock(packet, uid)) {
            connectionLogger.logBlocked(destIp, 0, uid, "RULE_BLOCKED");
            dataUsageTracker.trackBlocked(uid, packet.limit());
            return;
        }

        // Allow packet through
        dataUsageTracker.trackAllowed(uid, packet.limit());

        // In a real implementation, we would forward the packet
        // For a local VPN, we need to handle the actual forwarding
        // This is simplified - full implementation would use raw sockets
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Fire Firewall VPN notification");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
