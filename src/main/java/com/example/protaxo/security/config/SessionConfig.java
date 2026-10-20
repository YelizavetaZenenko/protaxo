package com.example.protaxo.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

@Configuration
public class SessionConfig {

    /**
     * Два одночасні запити однієї сесії (вхід + пульс /session/ping, кілька вкладок) обидва
     * вважали атрибут новим і робили INSERT у SPRING_SESSION_ATTRIBUTES — другий падав з
     * duplicate key, а людина бачила сторінку помилки (інцидент 2026-10-20 11:44). Стандартний
     * кастомайзер Spring Session для PostgreSQL замінює INSERT на upsert (ON CONFLICT DO UPDATE).
     */
    @Bean
    public PostgreSqlJdbcIndexedSessionRepositoryCustomizer postgreSqlSessionRepositoryCustomizer() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }
}
