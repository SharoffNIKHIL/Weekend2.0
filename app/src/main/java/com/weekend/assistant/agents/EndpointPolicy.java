package com.weekend.assistant.agents;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Which URLs a remote agent may have (P9, SSRF defence): https only, except plain http on loopback for local
 * testing; no user-info, query or fragment; and host (or host:port) must be on weekend.agents.allowed-hosts.
 */
public final class EndpointPolicy {

    private final List<String> allowedHosts;

    public EndpointPolicy(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts.stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).toList();
    }

    /** Returns the normalised endpoint (no trailing slash) or throws IllegalArgumentException with the reason. */
    public String check(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 300) {
            throw new IllegalArgumentException("endpoint is required (max 300 characters)");
        }
        URI uri;
        try {
            uri = new URI(endpoint.strip());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("endpoint is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("endpoint must be a plain URL with a host and no user-info, query or fragment");
        }
        boolean loopback = isLoopback(host);
        if (!"https".equals(scheme) && !("http".equals(scheme) && loopback)) {
            throw new IllegalArgumentException("endpoint must use https (plain http only on loopback)");
        }
        String hostPort = uri.getPort() == -1 ? host : host + ":" + uri.getPort();
        if (!allowedHosts.contains(host) && !allowedHosts.contains(hostPort)) {
            throw new IllegalArgumentException("host is not on weekend.agents.allowed-hosts (add it after a security checkpoint)");
        }
        String s = uri.toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public boolean anyAllowed() {
        return !allowedHosts.isEmpty();
    }

    private static boolean isLoopback(String host) {
        if ("localhost".equals(host)) {
            return true;
        }
        if (!host.matches("[0-9.]+") && !host.startsWith("[")) {
            return false; // never resolve DNS here
        }
        try {
            return InetAddress.getByName(host.replace("[", "").replace("]", "")).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
