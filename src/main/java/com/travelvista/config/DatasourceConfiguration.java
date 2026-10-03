package com.travelvista.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
public class DatasourceConfiguration {
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(
            DataSourceProperties properties,
            @Value("${DATABASE_URL:}") String databaseUrl,
            @Value("${PGHOST:}") String pgHost,
            @Value("${PGPORT:5432}") String pgPort,
            @Value("${PGDATABASE:}") String pgDatabase,
            @Value("${PGUSER:}") String pgUsername,
            @Value("${PGPASSWORD:}") String pgPassword,
            @Value("${DB_USERNAME:}") String localUsername,
            @Value("${DB_PASSWORD:}") String localPassword) {
        String configuredUrl = selectConnectionUrl(pgHost, pgPort, pgDatabase, databaseUrl, properties.getUrl());
        NormalizedUrl normalized = normalizeUrl(configuredUrl, "", pgPort, "");

        String username = resolveUsername(pgUsername, properties.getUsername(), localUsername, normalized.username());
        String password = resolvePassword(pgPassword, properties.getPassword(), localPassword, normalized.password());
        // DataSourceProperties.build() needs a JDBC URL before the Hikari bean is created.
        properties.setUrl(normalized.jdbcUrl());
        if (username != null) properties.setUsername(username);
        if (password != null) properties.setPassword(password);

        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setJdbcUrl(normalized.jdbcUrl());
        if (username != null && !username.isBlank()) dataSource.setUsername(username);
        if (password != null) dataSource.setPassword(password);
        return dataSource;
    }

    static String toJdbcUrl(String configuredUrl, String host, String port, String database) {
        return normalizeUrl(configuredUrl, host, port, database).jdbcUrl();
    }

    static String selectConnectionUrl(String pgHost, String pgPort, String pgDatabase,
                                      String databaseUrl, String springDatasourceUrl) {
        if (pgHost != null && !pgHost.isBlank() && pgDatabase != null && !pgDatabase.isBlank()) {
            return toJdbcUrl("", pgHost, pgPort, pgDatabase);
        }
        return firstNonBlank(databaseUrl, springDatasourceUrl);
    }

    static String resolveUsername(String pgUsername, String springDatasourceUsername,
                                  String localUsername, String urlUsername) {
        return firstNonBlank(pgUsername, springDatasourceUsername, localUsername, urlUsername);
    }

    static String resolvePassword(String pgPassword, String springDatasourcePassword,
                                  String localPassword, String urlPassword) {
        return firstNonBlank(pgPassword, springDatasourcePassword, localPassword, urlPassword);
    }

    static NormalizedUrl normalizeUrl(String configuredUrl, String host, String port, String database) {
        String url = configuredUrl == null ? "" : configuredUrl.trim();
        if (url.regionMatches(true, 0, "jdbc:postgresql://", 0, "jdbc:postgresql://".length())) {
            return new NormalizedUrl(url, null, null);
        }
        if (url.regionMatches(true, 0, "postgresql://", 0, "postgresql://".length())
                || url.regionMatches(true, 0, "postgres://", 0, "postgres://".length())) {
            try {
                URI uri = URI.create(url);
                String authority = uri.getRawAuthority();
                String userInfo = uri.getRawUserInfo();
                String username = null;
                String password = null;
                if (userInfo != null) {
                    int separator = userInfo.indexOf(':');
                    username = decode(separator < 0 ? userInfo : userInfo.substring(0, separator));
                    if (separator >= 0) password = decode(userInfo.substring(separator + 1));
                    authority = authority.substring(authority.lastIndexOf('@') + 1);
                }
                String jdbcUrl = "jdbc:postgresql://" + authority + uri.getRawPath()
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                return new NormalizedUrl(jdbcUrl, username, password);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid PostgreSQL connection URL");
            }
        }
        if (!url.isBlank()) {
            throw new IllegalArgumentException("PostgreSQL URL must use postgresql:// or jdbc:postgresql://");
        }
        if (host == null || host.isBlank() || database == null || database.isBlank()) {
            throw new IllegalStateException("Configure DATABASE_URL or PGHOST and PGDATABASE for PostgreSQL");
        }
        String effectivePort = port == null || port.isBlank() ? "5432" : port.trim();
        return new NormalizedUrl("jdbc:postgresql://" + host.trim() + ":" + effectivePort + "/" + database.trim(), null, null);
    }

    private static String decode(String value) {
        // URLDecoder treats '+' as a form-encoding space; in URI user-info it is literal unless escaped.
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    record NormalizedUrl(String jdbcUrl, String username, String password) {}
}
