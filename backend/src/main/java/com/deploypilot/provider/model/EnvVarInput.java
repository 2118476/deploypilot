package com.deploypilot.provider.model;

/**
 * An environment variable to set on a hosting resource. {@code secret} marks
 * values that must never be logged; the value itself is used only to make the
 * provider API call and is not retained.
 */
public record EnvVarInput(String key, String value, boolean secret) {}
