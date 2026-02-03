package com.nexusbuild.firewall;

import android.content.Context;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class DnsInterceptor {
    private static final String TAG = "DnsInterceptor";
    private final Context context;
    private final BlockListManager blockListManager;
    private final DomainMatcher domainMatcher;

    public DnsInterceptor(Context context) {
        this.context = context;
        this.blockListManager = BlockListManager.getInstance(context);
        this.domainMatcher = new DomainMatcher();
    }

    public void reloadBlockList() {
        blockListManager.reload();
    }

    public ByteBuffer processDnsRequest(ByteBuffer packet) {
        try {
            // Skip IP header (20 bytes) and UDP header (8 bytes)
            int dnsOffset = 28;
            if (packet.limit() < dnsOffset + 12) {
                return null; // Invalid DNS packet
            }

            // Parse DNS header
            int transactionId = packet.getShort(dnsOffset) & 0xFFFF;
            int flags = packet.getShort(dnsOffset + 2) & 0xFFFF;
            int questions = packet.getShort(dnsOffset + 4) & 0xFFFF;

            if (questions < 1) {
                return null;
            }

            // Parse domain name from question
            String domain = parseDomainName(packet, dnsOffset + 12);
            if (domain == null) {
                return null;
            }

            // Check if domain should be blocked
            if (shouldBlockDomain(domain)) {
                Log.i(TAG, "Blocking DNS request for: " + domain);
                return createNxdomainResponse(packet, transactionId);
            }

            return null; // Allow the request
        } catch (Exception e) {
            Log.e(TAG, "Error processing DNS request", e);
            return null;
        }
    }

    private String parseDomainName(ByteBuffer packet, int offset) {
        StringBuilder domain = new StringBuilder();
        int pos = offset;

        while (pos < packet.limit()) {
            int labelLength = packet.get(pos) & 0xFF;
            if (labelLength == 0) {
                break;
            }

            if (domain.length() > 0) {
                domain.append('.');
            }

            pos++;
            for (int i = 0; i < labelLength && pos < packet.limit(); i++) {
                domain.append((char) (packet.get(pos++) & 0xFF));
            }
        }

        return domain.length() > 0 ? domain.toString().toLowerCase() : null;
    }

    private boolean shouldBlockDomain(String domain) {
        // Check custom domain rules
        RuleManager ruleManager = RuleManager.getInstance(context);
        for (DomainRule rule : ruleManager.getDomainRules()) {
            if (domainMatcher.matches(domain, rule.getDomain()) && rule.isBlocked()) {
                return true;
            }
        }

        // Check block list
        return blockListManager.isBlocked(domain);
    }

    private ByteBuffer createNxdomainResponse(ByteBuffer request, int transactionId) {
        // Create a minimal NXDOMAIN response
        ByteBuffer response = ByteBuffer.allocate(request.limit());
        response.put(request.array(), 0, request.limit());

        // Swap source and destination in IP header
        int srcIp = response.getInt(12);
        int dstIp = response.getInt(16);
        response.putInt(12, dstIp);
        response.putInt(16, srcIp);

        // Swap ports in UDP header
        int srcPort = response.getShort(20) & 0xFFFF;
        int dstPort = response.getShort(22) & 0xFFFF;
        response.putShort(20, (short) dstPort);
        response.putShort(22, (short) srcPort);

        // Set DNS response flags (NXDOMAIN)
        int dnsOffset = 28;
        response.putShort(dnsOffset + 2, (short) 0x8183); // Response + NXDOMAIN

        return response;
    }
}
