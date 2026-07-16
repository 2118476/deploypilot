package com.deploypilot.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Migration destructive-statement detection and deterministic checksums (tests 13, 14). */
class MigrationSafetyTest {

    @Test
    void detectsDestructiveStatements() {
        assertTrue(MigrationSafety.detect("DROP TABLE users;").destructive());
        assertTrue(MigrationSafety.detect("drop database app;").destructive());
        assertTrue(MigrationSafety.detect("DROP SCHEMA public CASCADE;").destructive());
        assertTrue(MigrationSafety.detect("TRUNCATE orders;").destructive());
        assertTrue(MigrationSafety.detect("ALTER TABLE users DROP COLUMN email;").destructive());
        assertTrue(MigrationSafety.detect("ALTER TABLE users DROP CONSTRAINT pk;").destructive());
        assertTrue(MigrationSafety.detect("DELETE FROM users;").destructive(), "DELETE without WHERE is destructive");
    }

    @Test
    void allowsSafeStatements() {
        assertFalse(MigrationSafety.detect("CREATE TABLE users (id serial primary key);").destructive());
        assertFalse(MigrationSafety.detect("ALTER TABLE users ADD COLUMN age int;").destructive());
        assertFalse(MigrationSafety.detect("DELETE FROM users WHERE id = 5;").destructive());
        assertFalse(MigrationSafety.detect("INSERT INTO users (id) VALUES (1);").destructive());
        assertFalse(MigrationSafety.detect("CREATE INDEX idx ON users(email);").destructive());
    }

    @Test
    void ignoresCommentedOutDestructiveStatements() {
        assertFalse(MigrationSafety.detect("-- DROP TABLE users;\nCREATE TABLE t (id int);").destructive());
        assertFalse(MigrationSafety.detect("/* TRUNCATE t; */ CREATE TABLE t (id int);").destructive());
    }

    @Test
    void checksumIsDeterministicAndSensitive() {
        String sql = "CREATE TABLE a (id int);";
        assertEquals(MigrationSafety.checksum(sql), MigrationSafety.checksum(sql), "same SQL -> same checksum");
        // Line-ending normalisation: CRLF vs LF must not change the checksum.
        assertEquals(MigrationSafety.checksum("a\nb"), MigrationSafety.checksum("a\r\nb"));
        assertNotEquals(MigrationSafety.checksum(sql), MigrationSafety.checksum(sql + " -- changed"));
        assertEquals(64, MigrationSafety.checksum(sql).length());
    }
}
