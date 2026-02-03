package com.nexusbuild.firewall;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RuleManager {
    private static final String TAG = "RuleManager";
    private static final String PREFS_NAME = "fire_rules";
    private static final String KEY_APP_RULES = "app_rules";
    private static final String KEY_DOMAIN_RULES = "domain_rules";
    private static final String KEY_IP_RULES = "ip_rules";

    private static RuleManager instance;
    private final Context context;
    private final List<AppRule> appRules = new ArrayList<>();
    private final List<DomainRule> domainRules = new ArrayList<>();
    private final List<IpRule> ipRules = new ArrayList<>();

    private RuleManager(Context context) {
        this.context = context.getApplicationContext();
        loadRules();
    }

    public static synchronized RuleManager getInstance(Context context) {
        if (instance == null) {
            instance = new RuleManager(context);
        }
        return instance;
    }

    private void loadRules() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // Load app rules
            String appJson = prefs.getString(KEY_APP_RULES, "[]");
            JSONArray appArray = new JSONArray(appJson);
            synchronized (appRules) {
                appRules.clear();
                for (int i = 0; i < appArray.length(); i++) {
                    AppRule rule = AppRule.fromJson(appArray.getJSONObject(i));
                    if (rule != null) {
                        appRules.add(rule);
                    }
                }
            }

            // Load domain rules
            String domainJson = prefs.getString(KEY_DOMAIN_RULES, "[]");
            JSONArray domainArray = new JSONArray(domainJson);
            synchronized (domainRules) {
                domainRules.clear();
                for (int i = 0; i < domainArray.length(); i++) {
                    DomainRule rule = DomainRule.fromJson(domainArray.getJSONObject(i));
                    if (rule != null) {
                        domainRules.add(rule);
                    }
                }
            }

            // Load IP rules
            String ipJson = prefs.getString(KEY_IP_RULES, "[]");
            JSONArray ipArray = new JSONArray(ipJson);
            synchronized (ipRules) {
                ipRules.clear();
                for (int i = 0; i < ipArray.length(); i++) {
                    IpRule rule = IpRule.fromJson(ipArray.getJSONObject(i));
                    if (rule != null) {
                        ipRules.add(rule);
                    }
                }
            }

            Log.i(TAG, "Loaded rules: " + appRules.size() + " apps, " +
                  domainRules.size() + " domains, " + ipRules.size() + " IPs");
        } catch (Exception e) {
            Log.e(TAG, "Error loading rules", e);
        }
    }

    private void saveRules() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Save app rules
            JSONArray appArray = new JSONArray();
            synchronized (appRules) {
                for (AppRule rule : appRules) {
                    appArray.put(rule.toJson());
                }
            }
            editor.putString(KEY_APP_RULES, appArray.toString());

            // Save domain rules
            JSONArray domainArray = new JSONArray();
            synchronized (domainRules) {
                for (DomainRule rule : domainRules) {
                    domainArray.put(rule.toJson());
                }
            }
            editor.putString(KEY_DOMAIN_RULES, domainArray.toString());

            // Save IP rules
            JSONArray ipArray = new JSONArray();
            synchronized (ipRules) {
                for (IpRule rule : ipRules) {
                    ipArray.put(rule.toJson());
                }
            }
            editor.putString(KEY_IP_RULES, ipArray.toString());

            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving rules", e);
        }
    }

    // App rules
    public List<AppRule> getAppRules() {
        synchronized (appRules) {
            return new ArrayList<>(appRules);
        }
    }

    public void addAppRule(AppRule rule) {
        synchronized (appRules) {
            // Remove existing rule for same package
            appRules.removeIf(r -> r.getPackageName().equals(rule.getPackageName()));
            appRules.add(rule);
        }
        saveRules();
    }

    public void removeAppRule(String packageName) {
        synchronized (appRules) {
            appRules.removeIf(r -> r.getPackageName().equals(packageName));
        }
        saveRules();
    }

    public void clearAppRules() {
        synchronized (appRules) {
            appRules.clear();
        }
        saveRules();
    }

    // Domain rules
    public List<DomainRule> getDomainRules() {
        synchronized (domainRules) {
            return new ArrayList<>(domainRules);
        }
    }

    public void addDomainRule(DomainRule rule) {
        synchronized (domainRules) {
            domainRules.removeIf(r -> r.getDomain().equals(rule.getDomain()));
            domainRules.add(rule);
        }
        saveRules();
    }

    public void removeDomainRule(String domain) {
        synchronized (domainRules) {
            domainRules.removeIf(r -> r.getDomain().equals(domain));
        }
        saveRules();
    }

    // IP rules
    public List<IpRule> getIpRules() {
        synchronized (ipRules) {
            return new ArrayList<>(ipRules);
        }
    }

    public void addIpRule(IpRule rule) {
        synchronized (ipRules) {
            ipRules.add(rule);
        }
        saveRules();
    }

    public void removeIpRule(String ip) {
        synchronized (ipRules) {
            ipRules.removeIf(r -> r.getIp().equals(ip));
        }
        saveRules();
    }
}
