package com.deploypilot.verify;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Validates that a URL is safe to fetch from the server, guarding against SSRF.
 *
 * A URL is only accepted when:
 *  - the scheme is https (or http when allowHttp is set, for local dev only),
 *  - it carries no embedded username/password,
 *  - the host is not an IP literal that is loopback/private/link-local/reserved,
 *  - every DNS-resolved address is a routable public address.
 *
 * The resolved addresses are returned so the caller can pin the connection to a
 * vetted IP and avoid TOCTOU DNS-rebinding between validation and connect.
 */
@Component
public class SafeUrlValidator {

    public record Validated(URI uri, String host, int port, List<InetAddress> addresses) {}

    /** Validates a production URL: HTTPS required, no private hosts. */
    public Validated validateProduction(String rawUrl) {
        return validate(rawUrl, false);
    }

    /**
     * @param allowHttp permit http and localhost — only for explicitly local development targets
     */
    public Validated validate(String rawUrl, boolean allowHttp) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new SafeUrlException("URL is required");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new SafeUrlException("Not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
            throw new SafeUrlException(allowHttp
                ? "Only http and https URLs are allowed"
                : "Production URLs must use HTTPS");
        }
        if (uri.getUserInfo() != null) {
            throw new SafeUrlException("URLs must not contain a username or password");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SafeUrlException("URL has no host");
        }
        host = host.toLowerCase();
        if (!allowHttp && isLocalHostname(host)) {
            throw new SafeUrlException("Localhost is not a valid production target");
        }

        List<InetAddress> addresses = resolve(host);
        for (InetAddress addr : addresses) {
            if (isBlockedAddress(addr) && !(allowHttp && addr.isLoopbackAddress())) {
                throw new SafeUrlException(
                    "Host resolves to a blocked address (" + addr.getHostAddress()
                        + "). Private, loopback, link-local and cloud-metadata addresses are not allowed.");
            }
        }
        int port = uri.getPort() != -1 ? uri.getPort() : (scheme.equals("https") ? 443 : 80);
        return new Validated(uri, host, port, addresses);
    }

    /** Revalidates a redirect Location, blocking downgrade to http and unsafe hosts. */
    public Validated validateRedirect(String location, boolean allowHttp) {
        Validated v = validate(location, allowHttp);
        if (!allowHttp && !"https".equalsIgnoreCase(v.uri().getScheme())) {
            throw new SafeUrlException("Refusing to follow a redirect that downgrades to http");
        }
        return v;
    }

    private List<InetAddress> resolve(String host) {
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            if (all.length == 0) throw new SafeUrlException("Host did not resolve to any address");
            return List.of(all);
        } catch (UnknownHostException e) {
            throw new SafeUrlException("Host could not be resolved by DNS: " + host);
        }
    }

    private boolean isLocalHostname(String host) {
        return host.equals("localhost") || host.endsWith(".localhost")
            || host.equals("ip6-localhost") || host.endsWith(".local");
    }

    /** True for any address that must never be reached by a server-side fetch. */
    boolean isBlockedAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
            || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
            || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int o0 = b[0] & 0xFF, o1 = b[1] & 0xFF;
            // 169.254.0.0/16 link-local incl. 169.254.169.254 cloud metadata
            if (o0 == 169 && o1 == 254) return true;
            // 100.64.0.0/10 carrier-grade NAT
            if (o0 == 100 && o1 >= 64 && o1 <= 127) return true;
            // 192.0.0.0/24, 192.0.2.0/24, 198.18.0.0/15, 198.51.100.0/24, 203.0.113.0/24 special-use
            if (o0 == 192 && o1 == 0) return true;
            if (o0 == 198 && (o1 == 18 || o1 == 19 || o1 == 51)) return true;
            if (o0 == 203 && o1 == 0) return true;
            // 0.0.0.0/8 and 240.0.0.0/4 reserved
            if (o0 == 0 || o0 >= 240) return true;
        } else if (b.length == 16) {
            // fc00::/7 unique local, plus ::/128 handled by isAnyLocalAddress
            int first = b[0] & 0xFE;
            if (first == 0xFC) return true;
            // IPv4-mapped/compatible addresses: re-check the embedded IPv4
            boolean mapped = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) { mapped = false; break; }
            if (mapped && (b[10] == 0 || (b[10] & 0xFF) == 0xFF)) {
                try {
                    byte[] v4 = new byte[]{b[12], b[13], b[14], b[15]};
                    return isBlockedAddress(InetAddress.getByAddress(v4));
                } catch (UnknownHostException ignored) { /* fall through */ }
            }
        }
        return false;
    }
}
