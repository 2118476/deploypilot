package com.deploypilot.dto;

import java.time.Instant;

/**
 * Public view of a provider connection. Deliberately carries no token — only
 * status, the connected account label and the granted permissions.
 */
public class ConnectionResponse {
    private String provider;
    private boolean connected;
    private String connectionType;
    private String accountLabel;
    private String scopes;
    private String status;
    private String lastError;
    private Instant connectedAt;
    private Instant lastUsedAt;

    public String getProvider() { return provider; } public void setProvider(String p) { this.provider = p; }
    public boolean isConnected() { return connected; } public void setConnected(boolean c) { this.connected = c; }
    public String getConnectionType() { return connectionType; } public void setConnectionType(String c) { this.connectionType = c; }
    public String getAccountLabel() { return accountLabel; } public void setAccountLabel(String a) { this.accountLabel = a; }
    public String getScopes() { return scopes; } public void setScopes(String s) { this.scopes = s; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getLastError() { return lastError; } public void setLastError(String l) { this.lastError = l; }
    public Instant getConnectedAt() { return connectedAt; } public void setConnectedAt(Instant c) { this.connectedAt = c; }
    public Instant getLastUsedAt() { return lastUsedAt; } public void setLastUsedAt(Instant l) { this.lastUsedAt = l; }
}
