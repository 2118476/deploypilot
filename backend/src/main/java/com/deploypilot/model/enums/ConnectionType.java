package com.deploypilot.model.enums;

/**
 * How a provider connection was established. The GitHub App and Netlify OAuth
 * variants require the owner to register an application (external setup); the
 * token variants work with a provider-issued personal access token / API key.
 */
public enum ConnectionType {
    GITHUB_APP,
    GITHUB_PAT,
    NETLIFY_OAUTH,
    NETLIFY_PAT,
    RENDER_API_KEY,
    // Supabase Management API personal access token. OAuth is a future upgrade,
    // kept in the model so connections stay forward-compatible.
    SUPABASE_PAT,
    SUPABASE_OAUTH
}
