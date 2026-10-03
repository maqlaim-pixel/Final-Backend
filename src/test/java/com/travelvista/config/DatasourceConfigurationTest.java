package com.travelvista.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasourceConfigurationTest {
    @Test
    void convertsRailwayPostgresqlUriToJdbcAndSeparatesCredentials() {
        DatasourceConfiguration.NormalizedUrl result = DatasourceConfiguration.normalizeUrl(
                "postgresql://app%40railway:p%2Bss%40word@db.example:6543/travel?sslmode=require",
                "", "5432", "");

        assertEquals("jdbc:postgresql://db.example:6543/travel?sslmode=require", result.jdbcUrl());
        assertEquals("app@railway", result.username());
        assertEquals("p+ss@word", result.password());
        assertFalseContainsSecrets(result.jdbcUrl(), "p+ss@word", "app@railway");
    }

    @Test
    void keepsLocalJdbcUrlUnchanged() {
        DatasourceConfiguration.NormalizedUrl result = DatasourceConfiguration.normalizeUrl(
                "jdbc:postgresql://localhost:5432/travel", "", "5432", "");

        assertEquals("jdbc:postgresql://localhost:5432/travel", result.jdbcUrl());
        assertNull(result.username());
        assertNull(result.password());
    }

    @Test
    void supportsLegacyPostgresScheme() {
        assertEquals("jdbc:postgresql://db.example/travel",
                DatasourceConfiguration.toJdbcUrl("postgres://db.example/travel", "", "5432", ""));
    }

    @Test
    void constructsLocalJdbcUrlFromRailwayStyleIndividualVariables() {
        assertEquals("jdbc:postgresql://db.internal:5433/travel",
                DatasourceConfiguration.toJdbcUrl("", "db.internal", "5433", "travel"));
    }

    @Test
    void doesNotSilentlyAcceptAnUnrecognizedUrlScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> DatasourceConfiguration.toJdbcUrl("mysql://db.example/travel", "", "5432", ""));
    }

    private static void assertFalseContainsSecrets(String value, String... secrets) {
        for (String secret : secrets) {
            if (value.contains(secret)) throw new AssertionError("JDBC URL contains connection credentials");
        }
    }
}
