package com.nexusbuild.firewall;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DataUsageTracker {
    private static final String TAG = "DataUsageTracker";
    private static final String PREFS_NAME = "fire_data_usage";
    private static final String KEY_USAGE = "usage";

    private static DataUsageTracker instance;
    private final Context context;
    private final Map<Integer, UsageStats> usageByUid = new HashMap<>();

    public static class UsageStats {
        public long bytesAllowed = 0;
        public long bytesBlocked = 0;
        public long packetsAllowed = 0;
        public long packetsBlocked = 0;

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("bytesAllowed", bytesAllowed);
                obj.put("bytesBlocked", bytesBlocked);
                obj.put("packetsAllowed", packetsAllowed);
                obj.put("packetsBlocked", packetsBlocked);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static UsageStats fromJson(JSONObject obj) {
            UsageStats stats = new UsageStats();
            stats.bytesAllowed = obj.optLong("bytesAllowed", 0);
            stats.bytesBlocked = obj.optLong("bytesBlocked", 0);
            stats.packetsAllowed = obj.optLong("packetsAllowed", 0);
            stats.packetsBlocked = obj.optLong("packetsBlocked", 0);
            return stats;
        }
    }

    private DataUsageTracker(Context context) {
        this.context = context.getApplicationContext();
        loadUsage();
    }

    public static synchronized DataUsageTracker getInstance(Context context) {
        if (instance == null) {
            instance = new DataUsageTracker(context);
        }
        return instance;
    }

    private void loadUsage() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_USAGE, "{}");
            JSONObject obj = new JSONObject(json);

            synchronized (usageByUid) {
                usageByUid.clear();
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    int uid = Integer.parseInt(key);
                    UsageStats stats = UsageStats.fromJson(obj.getJSONObject(key));
                    usageByUid.put(uid, stats);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading usage", e);
        }
    }

    private void saveUsage() {
        try {
            JSONObject obj = new JSONObject();
            synchronized (usageByUid) {
                for (Map.Entry<Integer, UsageStats> entry : usageByUid.entrySet()) {
                    obj.put(String.valueOf(entry.getKey()), entry.getValue().toJson());
                }
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_USAGE, obj.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving usage", e);
        }
    }

    public void trackAllowed(int uid, int bytes) {
        if (uid < 0) return;

        synchronized (usageByUid) {
            UsageStats stats = usageByUid.get(uid);
            if (stats == null) {
                stats = new UsageStats();
                usageByUid.put(uid, stats);
            }
            stats.bytesAllowed += bytes;
            stats.packetsAllowed++;
        }

        // Save periodically, not on every packet
        if (System.currentTimeMillis() % 100 == 0) {
            saveUsage();
        }
    }

    public void trackBlocked(int uid, int bytes) {
        if (uid < 0) return;

        synchronized (usageByUid) {
            UsageStats stats = usageByUid.get(uid);
            if (stats == null) {
                stats = new UsageStats();
                usageByUid.put(uid, stats);
            }
            stats.bytesBlocked += bytes;
            stats.packetsBlocked++;
        }

        saveUsage();
    }

    public WritableMap getUsageStats() {
        WritableMap result = Arguments.createMap();
        long totalBytesAllowed = 0;
        long totalBytesBlocked = 0;
        long totalPacketsAllowed = 0;
        long totalPacketsBlocked = 0;

        WritableMap byApp = Arguments.createMap();

        synchronized (usageByUid) {
            for (Map.Entry<Integer, UsageStats> entry : usageByUid.entrySet()) {
                int uid = entry.getKey();
                UsageStats stats = entry.getValue();

                totalBytesAllowed += stats.bytesAllowed;
                totalBytesBlocked += stats.bytesBlocked;
                totalPacketsAllowed += stats.packetsAllowed;
                totalPacketsBlocked += stats.packetsBlocked;

                String packageName = getPackageNameForUid(uid);
                if (!packageName.isEmpty()) {
                    WritableMap appStats = Arguments.createMap();
                    appStats.putDouble("bytesAllowed", stats.bytesAllowed);
                    appStats.putDouble("bytesBlocked", stats.bytesBlocked);
                    appStats.putDouble("packetsAllowed", stats.packetsAllowed);
                    appStats.putDouble("packetsBlocked", stats.packetsBlocked);
                    byApp.putMap(packageName, appStats);
                }
            }
        }

        result.putDouble("totalBytesAllowed", totalBytesAllowed);
        result.putDouble("totalBytesBlocked", totalBytesBlocked);
        result.putDouble("totalPacketsAllowed", totalPacketsAllowed);
        result.putDouble("totalPacketsBlocked", totalPacketsBlocked);
        result.putMap("byApp", byApp);

        return result;
    }

    public void resetStats() {
        synchronized (usageByUid) {
            usageByUid.clear();
        }
        saveUsage();
    }

    private String getPackageNameForUid(int uid) {
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        return packages != null && packages.length > 0 ? packages[0] : "";
    }
}
