package com.deploypilot.provider.model;

/** The outcome of applying one migration. The message is sanitised and safe to show. */
public record MigrationResult(String name, boolean applied, String message) {}
