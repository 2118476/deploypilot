package com.deploypilot.provider.model;

/**
 * A database project (a Supabase project). Carries only non-secret metadata —
 * never a password or service-role key.
 */
public record DatabaseProject(
    String ref,
    String name,
    String organizationId,
    String region,
    DatabaseStatus status,
    String host,
    String restUrl
) {}
