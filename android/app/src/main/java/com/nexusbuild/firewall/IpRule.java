package com.nexusbuild.firewall;

import org.json.JSONObject;

public class IpRule {
    private final String ip;
    private final int prefixLength;
    private final boolean blocked;

    // For simple IP
    public IpRule(String ip, boolean blocked) {
        this.ip = ip;
        this.prefixLength = 32;
        this.blocked = blocked;
    }

    // For CIDR notation (e.g., 192.168.1.0/24)
    public IpRule(String ip, int prefixLength, boolean blocked) {
        this.ip = ip;
        this.prefixLength = prefixLength;
        this.blocked = blocked;
    }

    public String getIp() {
        return ip;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean matches(int targetIp) {
        try {
            int ruleIp = ipToInt(ip);
            int mask = prefixLength == 0 ? 0 : (0xFFFFFFFF << (32 - prefixLength));
            return (targetIp & mask) == (ruleIp & mask);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean matches(String targetIp) {
        try {
            return matches(ipToInt(targetIp));
        } catch (Exception e) {
            return false;
        }
    }

    private int ipToInt(String ipString) {
        String[] parts = ipString.split("\\.");
        if (parts.length != 4) return 0;

        int result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (Integer.parseInt(parts[i]) & 0xFF);
        }
        return result;
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("ip", ip);
            obj.put("prefixLength", prefixLength);
            obj.put("blocked", blocked);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static IpRule fromJson(JSONObject obj) {
        try {
            return new IpRule(
                obj.getString("ip"),
                obj.getInt("prefixLength"),
                obj.getBoolean("blocked")
            );
        } catch (Exception e) {
            return null;
        }
    }
}
