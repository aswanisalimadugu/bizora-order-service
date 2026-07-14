package com.bizlink.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NeonDatabaseConfigTest {

    @Test
    void parsePostgresUrlConvertsNeonStyleConnectionString() {
        NeonDatabaseConfig.ParsedUrl parsed = NeonDatabaseConfig.parsePostgresUrl(
                "postgresql://user:secret@ep-abc123.us-east-2.aws.neon.tech/neondb?sslmode=require"
        );

        assertEquals("jdbc:postgresql://ep-abc123.us-east-2.aws.neon.tech/neondb?sslmode=require", parsed.jdbcUrl());
        assertEquals("user", parsed.username());
        assertEquals("secret", parsed.password());
    }
}
