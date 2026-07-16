package com.deploypilot.provider.model;

/**
 * Assembled connection details for a database project. The password, JDBC URL
 * and service-role key are backend-only secrets; the REST URL and anon key are
 * frontend-safe (public). This value is used to build environment variables and
 * is never persisted in run outputs or logs.
 */
public record DatabaseConnectionInfo(
    String host,
    int port,
    String database,
    String user,
    String password,        // backend-only secret
    String jdbcUrl,         // backend-only secret (embeds the password)
    String restUrl,         // frontend-safe (public)
    String anonKey,         // frontend-safe (public)
    String serviceRoleKey   // backend-only secret
) {}
