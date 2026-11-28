package com.rayvolt.backend.acceptance;

import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/trips/start_trip.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.rayvolt.backend.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber/PT-16.json,junit:target/cucumber/PT-16.xml")
public class TripStartAcceptanceIT { }
