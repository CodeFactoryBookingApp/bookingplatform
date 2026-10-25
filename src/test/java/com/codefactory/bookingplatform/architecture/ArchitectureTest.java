package com.codefactory.bookingplatform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Enforces the modular monolith + clean architecture rules (ADR-001):
 *
 * dependency rule: api -> application -> domain <- infrastructure
 * modules talk to each other only through application/domain, never infrastructure or api.
 *
 * <p>The layer rules match {@code ..domain..} and friends, so they already hold for every module.
 * The module rules come from {@link ModuleRules}, which names no module: a new one is checked as
 * soon as its package exists. {@link ModuleRulesTest} proves those rules fail on code that breaks them.
 *
 * <p>Each rule follows Arrange-Act-Assert: the production classes are imported once by
 * {@link AnalyzeClasses} and injected as {@code classes}; the method builds the rule (Arrange),
 * evaluates it against those classes (Act) and checks that the evaluation reports no violation
 * (Assert). {@code rule.evaluate(classes)} plus the empty-report check is exactly what
 * {@code rule.check(classes)} does, so the rules are as strict as they were as {@code ArchRule} fields.
 */
@AnalyzeClasses(packages = "com.codefactory.bookingplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.codefactory.bookingplatform";

    @ArchTest
    static void domainIsFreeOfFrameworks(JavaClasses classes) {
        // Arrange
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..",
                        "io.swagger.v3..")
                .because("the domain layer must stay pure and framework independent");

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void applicationDoesNotDependOnAdapters(JavaClasses classes) {
        // Arrange
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .because("use cases must depend on ports, not on adapters");

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void domainDoesNotDependOnOuterLayers(JavaClasses classes) {
        // Arrange
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..api..", "..infrastructure..")
                .because("the dependency rule points inwards");

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void infrastructureDoesNotDependOnApi(JavaClasses classes) {
        // Arrange
        ArchRule rule = noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .because("adapters must not depend on the web layer");

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void modulesMeetOnlyThroughApplicationOrDomain(JavaClasses classes) {
        // Arrange
        ArchRule rule = ModuleRules.modulesMeetOnlyThroughApplicationOrDomain(BASE);

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void modulesAreFreeOfCycles(JavaClasses classes) {
        // Arrange
        ArchRule rule = ModuleRules.modulesAreFreeOfCycles(BASE);

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void authKnowsNoBusinessModule(JavaClasses classes) {
        // Arrange
        ArchRule rule = ModuleRules.authKnowsNoBusinessModule(BASE);

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void sharedKernelIsModuleAgnostic(JavaClasses classes) {
        // Arrange
        ArchRule rule = ModuleRules.sharedKnowsNoModule(BASE);

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void moduleClassesLiveInALayer(JavaClasses classes) {
        // Arrange
        ArchRule rule = ModuleRules.moduleClassesLiveInALayer(BASE);

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void controllersLiveInApiPackages(JavaClasses classes) {
        // Arrange
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..api..")
                .because("web adapters belong to the api layer");

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void useCasesLiveInApplicationPackages(JavaClasses classes) {
        // Arrange
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage("..application..")
                .because("use cases belong to the application layer");

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    @ArchTest
    static void jpaEntitiesLiveInInfrastructure(JavaClasses classes) {
        // Arrange
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Entity")
                .and().resideOutsideOfPackage("..shared..")
                .should().resideInAPackage("..infrastructure..")
                .because("JPA entities are persistence adapters, not domain models");

        // Act
        EvaluationResult result = rule.evaluate(classes);

        // Assert
        assertNoViolations(result);
    }

    /** Same verdict as {@code ArchRule.check}: fails with ArchUnit's own report when any class breaks the rule. */
    private static void assertNoViolations(EvaluationResult result) {
        assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
    }
}
