package com.rayvolt.backend.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ReducedMotion;
import com.rayvolt.backend.model.PositionStatus;
import com.rayvolt.backend.model.TelemetryReading;
import com.rayvolt.backend.service.TelemetryService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.web.resources.static-locations=file:../frontend/dist/")
@UsePlaywright(FleetDashboardIT.NoAnimations.class)
@Tag("PT-46")
class FleetDashboardIT {

    private static final Path FRONTEND_BUILD = Path.of("../frontend/dist/index.html");

    @LocalServerPort private int port;
    @Autowired private TelemetryService telemetry;
    @Autowired private JdbcClient jdbc;

    public static class NoAnimations implements OptionsFactory {
        @Override
        public Options getOptions() {
            return new Options().setContextOptions(
                    new Browser.NewContextOptions().setReducedMotion(ReducedMotion.REDUCE));
        }
    }

    @BeforeAll
    static void frontendIsBuilt() {
        Assertions.assertThat(FRONTEND_BUILD)
                .as("Build the frontend first: cd frontend && npm ci && npm run build")
                .exists();
    }

    @BeforeEach
    void emptyFleet() {
        jdbc.sql("DELETE FROM scooters").update();
    }

    @Test
    void tableUpdatesLiveWithoutReloadingThePage(Page page) {
        Long scooter = addScooterAtMainGate();
        telemetry.record(reading(scooter, Instant.now(), 64, PositionStatus.AT_HUB, 40.612, -8.7175));
        Locator row = openFleet(page, scooter);
        assertThat(row).containsText("64%");
        assertThat(row).containsText("At Main Gate");

        page.evaluate("() => { window.sameDocument = true }");
        telemetry.record(reading(scooter, Instant.now().plusSeconds(1), 31, PositionStatus.MOVING, 40.610, -8.718));

        assertThat(row).containsText("31%");
        assertThat(row).containsText("On the move");
        Assertions.assertThat(page.evaluate("() => window.sameDocument")).as("Page was not reloaded").isEqualTo(true);
    }

    @Test
    void faultShowsToTheOperatorWithoutReloadingThePage(Page page) {
        Long scooter = addScooterAtMainGate();
        telemetry.record(reading(scooter, Instant.now(), 80, PositionStatus.AT_HUB, 40.612, -8.7175));
        Locator row = openFleet(page, scooter);
        assertThat(row).containsText("Available");

        page.evaluate("() => { window.sameDocument = true }");
        telemetry.record(faulty(reading(scooter, Instant.now().plusSeconds(1), 80, PositionStatus.AT_HUB, 40.612, -8.7175)));

        assertThat(row).containsText("Maintenance");
        assertThat(row).containsText("Fault");
        Assertions.assertThat(page.evaluate("() => window.sameDocument")).as("Page was not reloaded").isEqualTo(true);
    }

    private Locator openFleet(Page page, Long scooter) {
        page.navigate("http://localhost:" + port + "/");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Fleet").setExact(true)).click();
        assertThat(page.getByRole(AriaRole.STATUS)).hasText("Live");
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText("#" + scooter));
    }

    private Long addScooterAtMainGate() {
        jdbc.sql("INSERT INTO scooters (type, hub_id, status) VALUES ('Standard', 1, 'AVAILABLE')").update();
        return jdbc.sql("SELECT max(id) FROM scooters").query(Long.class).single();
    }

    private static TelemetryReading faulty(TelemetryReading reading) {
        return new TelemetryReading(reading.scooterId(), reading.timestamp(), reading.battery(), reading.locked(),
                reading.speedKmh(), reading.hubId(), reading.lat(), reading.lon(), reading.status(), true);
    }

    private static TelemetryReading reading(Long scooter, Instant at, int battery, PositionStatus status,
            double lat, double lon) {
        return new TelemetryReading(scooter, at, battery, status == PositionStatus.AT_HUB,
                status == PositionStatus.MOVING ? 15 : 0, status == PositionStatus.AT_HUB ? 1L : null,
                lat, lon, status, false);
    }
}
