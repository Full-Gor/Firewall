package com.nexusbuild.firewall;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class PacketFilter {
    private static final String TAG = "PacketFilter";
    private final Context context;
    private final RuleManager ruleManager;
    private Set<Integer> blockedUids = new HashSet<>();

    public PacketFilter(Context context) {
        this.context = context;
        this.ruleManager = RuleManager.getInstance(context);
        loadBlockedUids();
    }

    public void reloadRules() {
        loadBlockedUids();
    }

    private void loadBlockedUids() {
        blockedUids.clear();
        PackageManager pm = context.getPackageManager();

        for (AppRule rule : ruleManager.getAppRules()) {
            if (rule.isBlocked()) {
                try {
                    int uid = pm.getApplicationInfo(rule.getPackageName(), 0).uid;
                    blockedUids.add(uid);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Package not found: " + rule.getPackageName());
                }
            }
        }

        Log.i(TAG, "Loaded " + blockedUids.size() + " blocked UIDs");
    }

    public boolean shouldBlock(ByteBuffer packet, int uid) {
        // Block if UID is in blocked list
        if (uid >= 0 && blockedUids.contains(uid)) {
            return true;
        }

        // Check IP rules
        int destIp = packet.getInt(16);
        for (IpRule rule : ruleManager.getIpRules()) {
            if (rule.matches(destIp) && rule.isBlocked()) {
                return true;
            }
        }

        return false;
    }
}
