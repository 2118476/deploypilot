package com.deploypilot.provider.model;

/** Identity of the connected account, used to label a connection. No secrets. */
public record ProviderAccount(String externalId, String label, String scopes) {}
