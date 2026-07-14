package com.deploypilot.repoaccess;

/** One file in a repository listing. Size may be -1 when the provider does not report it. */
public record RepositoryFileEntry(String path, long size) {}
