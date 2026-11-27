package com.rayvolt.backend.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
public class LifecycleSpringConfiguration {
    static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
    static { POSTGRES.start(); }

    @TestBean private Clock clock;
    static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
