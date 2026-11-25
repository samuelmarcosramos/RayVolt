package com.rayvolt.backend;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.rayvolt.backend", importOptions = ImportOption.DoNotIncludeTests.class)
@ArchTag("PT-46")
class MqttBoundaryTest {

    @ArchTest
    static final ArchRule onlyTheAdapterUsesMqttLibraries = noClasses()
            .that().resideOutsideOfPackage("..mqtt..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.eclipse.paho..", "org.springframework.integration.mqtt..");

    @ArchTest
    static final ArchRule nothingDependsOnTheAdapter = noClasses()
            .that().resideOutsideOfPackage("..mqtt..")
            .should().dependOnClassesThat().resideInAPackage("..mqtt..");
}
