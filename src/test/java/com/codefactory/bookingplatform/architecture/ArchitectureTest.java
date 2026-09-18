package com.codefactory.bookingplatform.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular monolith + clean architecture rules (ADR-001):
 *
 * dependency rule: api -> application -> domain <- infrastructure
 * modules talk to each other only through application/domain, never infrastructure or api.
 */
@AnalyzeClasses(packages = "com.codefactory.bookingplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FREE_OF_FRAMEWORKS = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..",
                    "io.swagger.v3..")
            .because("the domain layer must stay pure and framework independent");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
            .because("use cases must depend on ports, not on adapters");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..api..", "..infrastructure..")
            .because("the dependency rule points inwards");

    @ArchTest
    static final ArchRule INFRASTRUCTURE_DOES_NOT_DEPEND_ON_API = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..api..")
            .because("adapters must not depend on the web layer");

    @ArchTest
    static final ArchRule IDENTITY_ONLY_USES_AUTH_PUBLIC_API = noClasses()
            .that().resideInAPackage("..identity..")
            .should().dependOnClassesThat().resideInAnyPackage("..auth.api..", "..auth.infrastructure..")
            .because("cross-module calls go through the auth application facade only");

    @ArchTest
    static final ArchRule AUTH_DOES_NOT_DEPEND_ON_IDENTITY = noClasses()
            .that().resideInAPackage("..auth..")
            .should().dependOnClassesThat().resideInAPackage("..identity..")
            .because("the transversal auth module must stay independent of business modules");

    @ArchTest
    static final ArchRule SHARED_KERNEL_IS_MODULE_AGNOSTIC = noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity..", "..auth..")
            .because("the shared kernel must not know any business module");

    @ArchTest
    static final ArchRule CONTROLLERS_LIVE_IN_API_PACKAGES = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..api..")
            .because("web adapters belong to the api layer");

    @ArchTest
    static final ArchRule USE_CASES_LIVE_IN_APPLICATION_PACKAGES = classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().resideInAPackage("..application..")
            .because("use cases belong to the application layer");

    @ArchTest
    static final ArchRule JPA_ENTITIES_LIVE_IN_INFRASTRUCTURE = classes()
            .that().haveSimpleNameEndingWith("Entity")
            .and().resideOutsideOfPackage("..shared..")
            .should().resideInAPackage("..infrastructure..")
            .because("JPA entities are persistence adapters, not domain models");
}
