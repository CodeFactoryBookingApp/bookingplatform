package com.codefactory.bookingplatform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generic module rules, run against the fixture modules in {@code architecture.fixture}. A
 * rule that is green on the production code proves nothing unless it is also red on code that
 * breaks it; these tests are that half.
 *
 * <p>Arrange-Act-Assert: the fixture classes are imported once; each test evaluates one rule
 * (Act) and checks which dependencies it reported (Assert).
 */
class ModuleRulesTest {

    private static final String FIXTURE = "com.codefactory.bookingplatform.architecture.fixture";
    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages(FIXTURE);

    private static List<String> violations(EvaluationResult result) {
        return result.getFailureReport().getDetails();
    }

    @Test
    @DisplayName("Reaching another module's adapter is reported; reaching its application facade is not")
    void crossModuleAccessGoesThroughApplicationOrDomain() {
        // Act
        List<String> violations = violations(
                ModuleRules.modulesMeetOnlyThroughApplicationOrDomain(FIXTURE).evaluate(CLASSES));

        // Assert
        assertEquals(1, violations.size(), violations::toString);
        assertTrue(violations.get(0).contains("InvoiceGateway"), violations::toString);
    }

    @Test
    @DisplayName("Two modules that depend on each other are reported as a cycle")
    void cyclesBetweenModulesAreReported() {
        // Act
        EvaluationResult result = ModuleRules.modulesAreFreeOfCycles(FIXTURE).evaluate(CLASSES);

        // Assert
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("orders"), result.getFailureReport()::toString);
    }

    @Test
    @DisplayName("The shared kernel depending on a module is reported")
    void sharedKernelReachingAModuleIsReported() {
        // Act
        List<String> violations = violations(ModuleRules.sharedKnowsNoModule(FIXTURE).evaluate(CLASSES));

        // Assert
        assertEquals(1, violations.size(), violations::toString);
        assertTrue(violations.get(0).contains("SharedClock"), violations::toString);
    }

    @Test
    @DisplayName("Auth depending on a business module is reported, even through that module's domain")
    void authReachingABusinessModuleIsReported() {
        // Act
        List<String> violations = violations(ModuleRules.authKnowsNoBusinessModule(FIXTURE).evaluate(CLASSES));

        // Assert
        assertEquals(1, violations.size(), violations::toString);
        assertTrue(violations.get(0).contains("LoginService"), violations::toString);
    }

    @Test
    @DisplayName("A class outside the four layers of its module is reported")
    void classOutsideTheLayersIsReported() {
        // Act
        List<String> violations = violations(ModuleRules.moduleClassesLiveInALayer(FIXTURE).evaluate(CLASSES));

        // Assert
        assertEquals(1, violations.size(), violations::toString);
        assertTrue(violations.get(0).contains("Stray"), violations::toString);
    }
}
