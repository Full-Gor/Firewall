package com.nexusbuild.firewall;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class ConnectionLogger {
    private static final String TAG = "ConnectionLogger";
    private static final String PREFS_NAME = "fire_connection_logs";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_LOGS = 1000;

    private static ConnectionLogger instance;
    private final Context context;
    private final List<ConnectionLog> logs = new ArrayList<>();

    public static class ConnectionLog {
        public final long timestamp;
        public final String destIp;
        public final int destPort;
        public final int uid;
        public final String action;
        public final String packageName;

        public ConnectionLog(long timestamp, String destIp, int destPort, int uid, String action, String packageName) {
            this.timestamp = timestamp;
            this.destIp = destIp;
            this.destPort = destPort;
            this.uid = uid;
            this.action = action;
            this.packageName = packageName;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", timestamp);
                obj.put("destIp", destIp);
                obj.put("destPort", destPort);
                obj.put("uid", uid);
                obj.put("action", action);
                obj.put("packageName", packageName);
                return obj;
            } catch (Exception e) {
                return null;
            }
        }

        public static ConnectionLog fromJson(JSONObject obj) {
            try {
                return new ConnectionLog(
                    obj.getLong("timestamp"),
                    obj.getString("destIp"),
                    obj.getInt("destPort"),
                    obj.getInt("uid"),
                    obj.getString("action"),
                    obj.optString("packageName", "")
                );
            } catch (Exception e) {
                return null;
            }
        }

        public WritableMap toWritableMap() {
            WritableMap map = Arguments.createMap();
            map.putDouble("timestamp", timestamp);
            map.putString("destIp", destIp);
            map.putInt("destPort", destPort);
            map.putInt("uid", uid);
            map.putString("action", action);
            map.putString("packageName", packageName);
            return map;
        }
    }

    private ConnectionLogger(Context context) {
        this.context = context.getApplicationContext();
        loadLogs();
    }

    public static synchronized ConnectionLogger getInstance(Context context) {
        if (instance == null) {
            instance = new ConnectionLogger(context);
        }
        return instance;
    }

    private void loadLogs() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_LOGS, "[]");
            JSONArray array = new JSONArray(json);

            synchronized (logs) {
                logs.clear();
                for (int i = 0; i < array.length(); i++) {
                    ConnectionLog log = ConnectionLog.fromJson(array.getJSONObject(i));
                    if (log != null) {
                        logs.add(log);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading logs", e);
        }
    }

    private void saveLogs() {
        try {
            JSONArray array = new JSONArray();
            synchronized (logs) {
                for (ConnectionLog log : logs) {
                    JSONObject obj = log.toJson();
                    if (obj != null) {
                        array.put(obj);
                    }
                }
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LOGS, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving logs", e);
        }
    }

    public void logBlocked(int destIp, int destPort, int uid, String action) {
        String ipString = intToIpString(destIp);
        String packageName = getPackageNameForUid(uid);

        ConnectionLog log = new ConnectionLog(
            System.currentTimeMillis(),
            ipString,
            destPort,
            uid,
            action,
            packageName
        );

        synchronized (logs) {
            logs.add(0, log);
            while (logs.size() > MAX_LOGS) {
                logs.remove(logs.size() - 1);
            }
        }

        saveLogs();
        Log.d(TAG, "Logged blocked connection: " + ipString + ":" + destPort + " (" + action + ")");
    }

    public void logAllowed(int destIp, int destPort, int uid) {
        // Optional: log allowed connections for monitoring
        // Not implemented to avoid performance impact
    }

    public void logBlockedDomain(String domain) {
        ConnectionLog log = new ConnectionLog(
            System.currentTimeMillis(),
            domain,  // Use domain instead of IP
            0,
            -1,
            "DNS_BLOCKED",
            ""
        );

        synchronized (logs) {
            logs.add(0, log);
            while (logs.size() > MAX_LOGS) {
                logs.remove(logs.size() - 1);
            }
        }

        saveLogs();
        Log.d(TAG, "Logged blocked domain: " + domain);
    }

    public WritableArray getLogs(int limit) {
        WritableArray result = Arguments.createArray();
        synchronized (logs) {
            int count = Math.min(limit, logs.size());
            for (int i = 0; i < count; i++) {
                result.pushMap(logs.get(i).toWritableMap());
            }
        }
        return result;
    }

    public void clearLogs() {
        synchronized (logs) {
            logs.clear();
        }
        saveLogs();
    }

    private String intToIpString(int ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF
        );
    }

    private String getPackageNameForUid(int uid) {
        if (uid < 0) return "";
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        return packages != null && packages.length > 0 ? packages[0] : "";
    }
}
