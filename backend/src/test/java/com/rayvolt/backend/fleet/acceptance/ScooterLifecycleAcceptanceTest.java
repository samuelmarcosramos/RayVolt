package com.rayvolt.backend.fleet.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/fleet/scooter_lifecycle.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.rayvolt.backend.fleet.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber/PT-34.json,junit:target/cucumber/PT-34.xml")
public class ScooterLifecycleAcceptanceTest {
}
