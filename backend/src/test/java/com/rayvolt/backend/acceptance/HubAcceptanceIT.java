package com.rayvolt.backend.acceptance;

import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/fleet/manage_hubs.feature")
@ConfigurationParameter(key = JUNIT_PLATFORM_NAMING_STRATEGY_PROPERTY_NAME, value = "long")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.rayvolt.backend.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber/PT-33.json,junit:target/cucumber/PT-33.xml")
public class HubAcceptanceIT { }
