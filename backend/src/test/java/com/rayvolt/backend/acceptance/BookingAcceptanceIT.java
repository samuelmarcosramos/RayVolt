package com.rayvolt.backend.acceptance;

import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/trips/book_scooter.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.rayvolt.backend.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber/PT-15.json,junit:target/cucumber/PT-15.xml")
public class BookingAcceptanceIT { }
