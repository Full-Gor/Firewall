package com.nexusbuild.firewall;

public class DomainMatcher {

    public boolean matches(String requestedDomain, String ruleDomain) {
        if (requestedDomain == null || ruleDomain == null) {
            return false;
        }

        String requested = requestedDomain.toLowerCase();
        String rule = ruleDomain.toLowerCase();

        // Exact match
        if (requested.equals(rule)) {
            return true;
        }

        // Wildcard match (*.example.com matches sub.example.com)
        if (rule.startsWith("*.")) {
            String baseDomain = rule.substring(2);
            return requested.endsWith("." + baseDomain) || requested.equals(baseDomain);
        }

        // Subdomain match (example.com matches sub.example.com)
        return requested.endsWith("." + rule);
    }

    public boolean matchesAny(String domain, Iterable<String> patterns) {
        for (String pattern : patterns) {
            if (matches(domain, pattern)) {
                return true;
            }
        }
        return false;
    }
}
