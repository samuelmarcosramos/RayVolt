package com.rayvolt.backend.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import com.rayvolt.backend.service.FleetService;
import com.rayvolt.backend.service.HubService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.web.resources.static-locations=file:../frontend/dist/")
@UsePlaywright(FleetDashboardIT.NoAnimations.class)
@Tag("PT-33")
class HubPageIT {
    @LocalServerPort int port;
    @Autowired HubService hubs;
    @Autowired FleetService fleet;

    @Test
    void operatorCreatesAndEditsHubFromNavbarWithOccupancyValidation(Page page) {
        page.navigate("http://localhost:" + port + "/");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Hubs").setExact(true)).click();
        assertThat(page).hasURL("http://localhost:" + port + "/hubs");
        page.getByLabel("Name", new Page.GetByLabelOptions().setExact(true)).fill("Browser gate");
        page.getByLabel("Department").fill("Security");
        page.getByLabel("Capacity").fill("3");
        page.getByLabel("Latitude").fill("40.6");
        page.getByLabel("Longitude").fill("-8.7");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save hub")).click();
        assertThat(page.getByRole(AriaRole.STATUS)).hasText("Hub saved.");
        assertThat(page.getByText("Browser gate", new Page.GetByTextOptions().setExact(true))).isVisible();
        Long id = hubs.list().stream().filter(h -> h.getName().equals("Browser gate")).findFirst().orElseThrow().getId();
        fleet.add("Standard", id);
        fleet.add("Standard", id);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Edit Browser gate")).click();
        page.getByLabel("Capacity").fill("1");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save hub")).click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("current scooter count (2)");
        assertThat(page.getByLabel("Capacity")).hasValue("1");
        page.getByLabel("Capacity").fill("2");
        page.getByLabel("Name", new Page.GetByLabelOptions().setExact(true)).fill("Updated browser gate");
        page.getByLabel("Department").fill("Logistics");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save hub")).click();
        assertThat(page.getByRole(AriaRole.STATUS)).hasText("Hub saved.");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Refresh hubs")).click();
        assertThat(page.getByText("Updated browser gate", new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText("Logistics · Capacity: 2")).isVisible();
    }
}
