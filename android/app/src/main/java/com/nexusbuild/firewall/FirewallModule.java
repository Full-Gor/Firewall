package com.nexusbuild.firewall;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.List;

public class FirewallModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    private static final String TAG = "FirewallModule";
    private static final int VPN_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "fire_settings";
    private static final String KEY_START_ON_BOOT = "start_on_boot";
    private static final String KEY_BLOCK_SYSTEM_APPS = "block_system_apps";
    private Promise vpnPromise;
    private final ReactApplicationContext reactContext;

    public FirewallModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
        context.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "FirewallModule";
    }

    @ReactMethod
    public void requestVpnPermission(Promise promise) {
        Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No activity available");
            return;
        }

        Intent intent = VpnService.prepare(activity);
        if (intent != null) {
            vpnPromise = promise;
            activity.startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            promise.resolve(true);
        }
    }

    @ReactMethod
    public void startVpn(Promise promise) {
        try {
            Intent intent = new Intent(reactContext, FirewallVpnService.class);
            intent.setAction(FirewallVpnService.ACTION_START);
            reactContext.startForegroundService(intent);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("VPN_START_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void stopVpn(Promise promise) {
        try {
            Intent intent = new Intent(reactContext, FirewallVpnService.class);
            intent.setAction(FirewallVpnService.ACTION_STOP);
            reactContext.startService(intent);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("VPN_STOP_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void isVpnRunning(Promise promise) {
        promise.resolve(FirewallVpnService.isRunning());
    }

    @ReactMethod
    public void getInstalledApps(Promise promise) {
        try {
            // Check if we should include system apps
            SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean includeSystemApps = prefs.getBoolean(KEY_BLOCK_SYSTEM_APPS, false);

            PackageManager pm = reactContext.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            WritableArray result = Arguments.createArray();

            for (ApplicationInfo app : apps) {
                boolean isSystemApp = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isUpdatedSystemApp = (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

                // Include app if:
                // - It's a user app (not system), OR
                // - It's an updated system app, OR
                // - includeSystemApps is enabled
                if (!isSystemApp || isUpdatedSystemApp || includeSystemApps) {
                    WritableMap appInfo = Arguments.createMap();
                    appInfo.putString("packageName", app.packageName);
                    appInfo.putString("appName", pm.getApplicationLabel(app).toString());
                    appInfo.putInt("uid", app.uid);
                    appInfo.putBoolean("isSystemApp", isSystemApp);
                    result.pushMap(appInfo);
                }
            }

            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("GET_APPS_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setBlockedApps(ReadableArray packageNames, Promise promise) {
        try {
            RuleManager ruleManager = RuleManager.getInstance(reactContext);
            ruleManager.clearAppRules();

            for (int i = 0; i < packageNames.size(); i++) {
                String packageName = packageNames.getString(i);
                ruleManager.addAppRule(new AppRule(packageName, true, true));
            }

            // Notify VPN service of rule changes
            Intent intent = new Intent(reactContext, FirewallVpnService.class);
            intent.setAction(FirewallVpnService.ACTION_RELOAD_RULES);
            reactContext.startService(intent);

            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("SET_BLOCKED_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void addBlockedDomain(String domain, Promise promise) {
        try {
            RuleManager ruleManager = RuleManager.getInstance(reactContext);
            ruleManager.addDomainRule(new DomainRule(domain, true));
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("ADD_DOMAIN_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void removeBlockedDomain(String domain, Promise promise) {
        try {
            RuleManager ruleManager = RuleManager.getInstance(reactContext);
            ruleManager.removeDomainRule(domain);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("REMOVE_DOMAIN_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getConnectionLogs(int limit, Promise promise) {
        try {
            ConnectionLogger logger = ConnectionLogger.getInstance(reactContext);
            WritableArray logs = logger.getLogs(limit);
            promise.resolve(logs);
        } catch (Exception e) {
            promise.reject("GET_LOGS_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getDataUsage(Promise promise) {
        try {
            DataUsageTracker tracker = DataUsageTracker.getInstance(reactContext);
            WritableMap usage = tracker.getUsageStats();
            promise.resolve(usage);
        } catch (Exception e) {
            promise.reject("GET_USAGE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void loadBlockList(String url, Promise promise) {
        try {
            BlockListManager manager = BlockListManager.getInstance(reactContext);
            manager.loadFromUrl(url, new BlockListManager.LoadCallback() {
                @Override
                public void onSuccess(int count) {
                    promise.resolve(count);
                }

                @Override
                public void onError(String error) {
                    promise.reject("BLOCKLIST_ERROR", error);
                }
            });
        } catch (Exception e) {
            promise.reject("LOAD_BLOCKLIST_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getBlockedDomainsCount(Promise promise) {
        try {
            BlockListManager manager = BlockListManager.getInstance(reactContext);
            promise.resolve(manager.getBlockedCount());
        } catch (Exception e) {
            promise.reject("GET_COUNT_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void clearConnectionLogs(Promise promise) {
        try {
            ConnectionLogger logger = ConnectionLogger.getInstance(reactContext);
            logger.clearLogs();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("CLEAR_LOGS_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getLogsCount(Promise promise) {
        try {
            ConnectionLogger logger = ConnectionLogger.getInstance(reactContext);
            WritableArray logs = logger.getLogs(1000);
            promise.resolve(logs.size());
        } catch (Exception e) {
            promise.reject("GET_LOGS_COUNT_ERROR", e.getMessage());
        }
    }

    // Settings methods
    @ReactMethod
    public void setStartOnBoot(boolean enabled, Promise promise) {
        try {
            SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("SET_SETTING_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getStartOnBoot(Promise promise) {
        try {
            SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean(KEY_START_ON_BOOT, false);
            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("GET_SETTING_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setBlockSystemApps(boolean enabled, Promise promise) {
        try {
            SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_BLOCK_SYSTEM_APPS, enabled).apply();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("SET_SETTING_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getBlockSystemApps(Promise promise) {
        try {
            SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean(KEY_BLOCK_SYSTEM_APPS, false);
            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("GET_SETTING_ERROR", e.getMessage());
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE && vpnPromise != null) {
            if (resultCode == Activity.RESULT_OK) {
                vpnPromise.resolve(true);
            } else {
                vpnPromise.reject("VPN_DENIED", "User denied VPN permission");
            }
            vpnPromise = null;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // Not used
    }
}
