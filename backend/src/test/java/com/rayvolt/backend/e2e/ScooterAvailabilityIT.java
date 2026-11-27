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
import java.nio.file.Path;
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
@UsePlaywright(ScooterAvailabilityIT.NoAnimations.class)
@Tag("PT-14")
class ScooterAvailabilityIT {

    private static final Path FRONTEND_BUILD = Path.of("../frontend/dist/index.html");

    @LocalServerPort private int port;
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
    void userCanFindAvailableScootersAndFilterByType(Page page) {
        Long standard = addScooter("Standard", 1, "AVAILABLE", 10);
        Long patrol = addScooter("Patrol", 1, "AVAILABLE", 50);
        Long belowThreshold = addScooter("Standard", 1, "AVAILABLE", 9);
        Long inUse = addScooter("Patrol", 1, "IN_USE", 90);
        Long otherHub = addScooter("Standard", 2, "AVAILABLE", 90);

        page.navigate("http://localhost:" + port + "/");
        findAvailabilityPage(page);
        page.getByLabel("Hub").selectOption("1");

        Locator scooters = page.getByRole(AriaRole.LIST,
                new Page.GetByRoleOptions().setName("Available scooters"));
        assertThat(scooters).containsText("#" + standard);
        assertThat(scooters).containsText("#" + patrol);
        assertThat(scooters).not().containsText("#" + belowThreshold);
        assertThat(scooters).not().containsText("#" + inUse);
        assertThat(scooters).not().containsText("#" + otherHub);

        page.getByLabel("Scooter type").selectOption("Patrol");
        assertThat(scooters).containsText("#" + patrol);
        assertThat(scooters).containsText("50%");
        assertThat(scooters).not().containsText("#" + standard);
    }

    @Test
    void userSeesEmptyStateWhenNoScootersMatch(Page page) {
        addScooter("Standard", 2, "AVAILABLE", 100);

        page.navigate("http://localhost:" + port + "/");
        findAvailabilityPage(page);
        page.getByLabel("Hub").selectOption("2");
        page.getByLabel("Scooter type").selectOption("Cargo");

        assertThat(page.getByText("No scooters available at this hub.", new Page.GetByTextOptions().setExact(true)))
                .isVisible();
    }

    private void findAvailabilityPage(Page page) {
        page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Main"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Find a scooter").setExact(true))
                .click();
    }

    private Long addScooter(String type, int hubId, String status, int batteryPercent) {
        jdbc.sql("INSERT INTO scooters (type, hub_id, status, battery_percent) "
                        + "VALUES (:type, :hubId, :status, :batteryPercent)")
                .param("type", type)
                .param("hubId", hubId)
                .param("status", status)
                .param("batteryPercent", batteryPercent)
                .update();
        return jdbc.sql("SELECT MAX(id) FROM scooters").query(Long.class).single();
    }
}
