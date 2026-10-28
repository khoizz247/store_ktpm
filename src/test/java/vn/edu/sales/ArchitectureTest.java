package vn.edu.sales;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("vn.edu.sales");

    @Test
    void applicationUsesOnlyJavaDomainAndApplicationContracts() {
        classes().that().resideInAPackage("vn.edu.sales.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..", "vn.edu.sales.domain..", "vn.edu.sales.application..")
                .because("business rules and ports must not depend on web, ORM, JDBC or Spring adapters")
                .check(PRODUCTION);
    }

    @Test
    void domainDoesNotDependOnFrameworksOrOuterLayers() {
        classes().that().resideInAPackage("vn.edu.sales.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "vn.edu.sales.domain..")
                .because("domain models and exceptions must remain framework independent")
                .check(PRODUCTION);
    }

    @Test
    void apiDoesNotBypassPortsToReachInfrastructureOrWiring() {
        noClasses().that().resideInAPackage("vn.edu.sales.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "vn.edu.sales.infrastructure..", "vn.edu.sales.config..")
                .because("HTTP adapters use application services/contracts, not concrete persistence or wiring")
                .check(PRODUCTION);
    }

    @Test
    void infrastructureDoesNotDependOnHttpAdaptersOrWiring() {
        noClasses().that().resideInAPackage("vn.edu.sales.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "vn.edu.sales.api..", "vn.edu.sales.config..")
                .because("database and security adapters implement inner contracts without importing controllers")
                .check(PRODUCTION);
    }
}
