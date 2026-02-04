package com.nexusbuild.firewall;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockListManager {
    private static final String TAG = "BlockListManager";
    private static final String PREFS_NAME = "fire_blocklist";
    private static final String KEY_BLOCKED_DOMAINS = "blocked_domains";

    // Whitelist: CDN légitimes à ne JAMAIS bloquer
    private static final Set<String> WHITELIST = new HashSet<String>() {{
        // YouTube
        add("googlevideo.com");
        add("ytimg.com");
        add("youtube.com");
        add("youtu.be");
        add("yt3.ggpht.com");
        add("youtube-nocookie.com");
        // Google Play Store
        add("play.google.com");
        add("googleusercontent.com");
        add("ggpht.com");
        add("gstatic.com");
        add("googleapis.com");
        add("google.com");
        // Images/CDN généraux
        add("cloudflare.com");
        add("cloudfront.net");
        add("akamaized.net");
        add("fastly.net");
        add("jsdelivr.net");
        add("cdnjs.cloudflare.com");
        // AliExpress / Alibaba
        add("alicdn.com");
        add("aliexpress.com");
        add("alibaba.com");
        add("aliimg.com");
        // Amazon
        add("amazon.com");
        add("amazonaws.com");
        add("media-amazon.com");
        // Social media images
        add("fbcdn.net");
        add("instagram.com");
        add("cdninstagram.com");
        add("twimg.com");
        add("pbs.twimg.com");
        // Autres
        add("imgur.com");
        add("i.imgur.com");
        add("redd.it");
        add("redditmedia.com");
        add("pinimg.com");
    }};

    private static BlockListManager instance;
    private final Context context;
    private final Set<String> blockedDomains = new HashSet<>();
    private final DomainMatcher domainMatcher = new DomainMatcher();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface LoadCallback {
        void onSuccess(int count);
        void onError(String error);
    }

    private BlockListManager(Context context) {
        this.context = context.getApplicationContext();
        loadFromPrefs();
    }

    public static synchronized BlockListManager getInstance(Context context) {
        if (instance == null) {
            instance = new BlockListManager(context);
        }
        return instance;
    }

    public void reload() {
        loadFromPrefs();
    }

    private void loadFromPrefs() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_BLOCKED_DOMAINS, new HashSet<>());
        synchronized (blockedDomains) {
            blockedDomains.clear();
            blockedDomains.addAll(saved);
        }
        Log.i(TAG, "Loaded " + blockedDomains.size() + " blocked domains from preferences");
    }

    private void saveToPrefs() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        synchronized (blockedDomains) {
            prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, new HashSet<>(blockedDomains)).apply();
        }
    }

    public boolean isBlocked(String domain) {
        // Check whitelist FIRST - never block these domains
        if (isWhitelisted(domain)) {
            return false;
        }

        synchronized (blockedDomains) {
            // Check exact match first
            if (blockedDomains.contains(domain)) {
                return true;
            }

            // Check parent domains
            String[] parts = domain.split("\\.");
            for (int i = 1; i < parts.length; i++) {
                StringBuilder parent = new StringBuilder();
                for (int j = i; j < parts.length; j++) {
                    if (parent.length() > 0) parent.append(".");
                    parent.append(parts[j]);
                }
                if (blockedDomains.contains(parent.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWhitelisted(String domain) {
        // Check exact match
        if (WHITELIST.contains(domain)) {
            return true;
        }

        // Check if domain ends with a whitelisted domain
        for (String whitelisted : WHITELIST) {
            if (domain.equals(whitelisted) || domain.endsWith("." + whitelisted)) {
                return true;
            }
        }
        return false;
    }

    public void addDomain(String domain) {
        synchronized (blockedDomains) {
            blockedDomains.add(domain.toLowerCase());
        }
        saveToPrefs();
    }

    public void removeDomain(String domain) {
        synchronized (blockedDomains) {
            blockedDomains.remove(domain.toLowerCase());
        }
        saveToPrefs();
    }

    public void loadFromUrl(String urlString, LoadCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    callback.onError("HTTP error: " + responseCode);
                    return;
                }

                Set<String> newDomains = new HashSet<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String domain = parseHostsLine(line);
                        if (domain != null) {
                            newDomains.add(domain);
                        }
                    }
                }

                synchronized (blockedDomains) {
                    blockedDomains.addAll(newDomains);
                }
                saveToPrefs();

                Log.i(TAG, "Loaded " + newDomains.size() + " domains from URL");
                callback.onSuccess(newDomains.size());

            } catch (Exception e) {
                Log.e(TAG, "Error loading block list", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private String parseHostsLine(String line) {
        // Skip comments and empty lines
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }

        // Parse hosts file format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            String ip = parts[0];
            String domain = parts[1].toLowerCase();

            // Only accept blocking entries
            if (ip.equals("0.0.0.0") || ip.equals("127.0.0.1")) {
                // Skip localhost entries
                if (!domain.equals("localhost") && !domain.equals("localhost.localdomain")) {
                    return domain;
                }
            }
        }

        return null;
    }

    public int getBlockedCount() {
        synchronized (blockedDomains) {
            return blockedDomains.size();
        }
    }
}
