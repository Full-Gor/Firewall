package com.nexusbuild.firewall;

import org.json.JSONObject;

public class DomainRule {
    private final String domain;
    private final boolean blocked;

    public DomainRule(String domain, boolean blocked) {
        this.domain = domain.toLowerCase();
        this.blocked = blocked;
    }

    public String getDomain() {
        return domain;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("domain", domain);
            obj.put("blocked", blocked);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static DomainRule fromJson(JSONObject obj) {
        try {
            return new DomainRule(
                obj.getString("domain"),
                obj.getBoolean("blocked")
            );
        } catch (Exception e) {
            return null;
        }
    }
}
