package com.nexusbuild.firewall;

import org.json.JSONObject;

public class AppRule {
    private final String packageName;
    private final boolean blockWifi;
    private final boolean blockMobile;

    public AppRule(String packageName, boolean blockWifi, boolean blockMobile) {
        this.packageName = packageName;
        this.blockWifi = blockWifi;
        this.blockMobile = blockMobile;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isBlockWifi() {
        return blockWifi;
    }

    public boolean isBlockMobile() {
        return blockMobile;
    }

    public boolean isBlocked() {
        return blockWifi || blockMobile;
    }

    public boolean shouldBlock(boolean isWifi) {
        return isWifi ? blockWifi : blockMobile;
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("packageName", packageName);
            obj.put("blockWifi", blockWifi);
            obj.put("blockMobile", blockMobile);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static AppRule fromJson(JSONObject obj) {
        try {
            return new AppRule(
                obj.getString("packageName"),
                obj.getBoolean("blockWifi"),
                obj.getBoolean("blockMobile")
            );
        } catch (Exception e) {
            return null;
        }
    }
}
