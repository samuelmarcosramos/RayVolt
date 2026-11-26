package com.rayvolt.backend.fleet.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.junit.jupiter.api.Tag;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/scooter-availability.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.rayvolt.backend.fleet.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber/PT-14.json,junit:target/cucumber/PT-14.xml")
@Tag("PT-14")
public class ScooterAvailabilityAcceptanceIT {
}
