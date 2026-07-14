package com.bizlink.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Neon (and other providers) supply {@code DATABASE_URL} as {@code postgresql://...}.
 * Spring Boot needs a JDBC URL and separate credentials.
 */
@Slf4j
@Configuration
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${DATABASE_URL:}') || T(org.springframework.util.StringUtils).hasText('${DB_URL:}')")
public class NeonDatabaseConfig {

    @Bean
    @Primary
    public DataSource neonDataSource(@Value("${DATABASE_URL:${DB_URL:}}") String databaseUrl) {
        if (!StringUtils.hasText(databaseUrl)) {
            throw new IllegalArgumentException("DATABASE_URL or DB_URL must be configured");
        }

        ParsedUrl parsed = parsePostgresUrl(databaseUrl);

        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName("org.postgresql.Driver")
                .url(parsed.jdbcUrl())
                .username(parsed.username())
                .password(parsed.password())
                .build();

        ds.setMaximumPoolSize(5);
        ds.addDataSourceProperty("sslmode", "require");
        log.info("Connected to Neon PostgreSQL at {}", maskHost(parsed.jdbcUrl()));
        return ds;
    }

    static ParsedUrl parsePostgresUrl(String databaseUrl) {
        String normalized = databaseUrl
                .replaceFirst("^jdbc:", "")
                .replaceFirst("^postgres://", "postgresql://");

        URI uri = URI.create(normalized);
        String username = "";
        String password = "";
        if (uri.getUserInfo() != null) {
            String[] parts = uri.getUserInfo().split(":", 2);
            username = decode(parts[0]);
            if (parts.length > 1) {
                password = decode(parts[1]);
            }
        }

        String path = uri.getPath() != null && !uri.getPath().isBlank() ? uri.getPath() : "/neondb";
        String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
        if (!query.contains("sslmode=")) {
            query = query.isEmpty() ? "?sslmode=require" : query + "&sslmode=require";
        }

        String host = uri.getHost();
        int port = uri.getPort();
        String hostPart = port > 0 ? host + ":" + port : host;
        String jdbcWithoutCreds = String.format("jdbc:postgresql://%s%s%s", hostPart, path, query);

        return new ParsedUrl(jdbcWithoutCreds, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String maskHost(String jdbcUrl) {
        try {
            URI uri = URI.create(jdbcUrl.replace("jdbc:", ""));
            return uri.getHost();
        } catch (Exception e) {
            return "neon";
        }
    }

    static record ParsedUrl(String jdbcUrl, String username, String password) {}
}
