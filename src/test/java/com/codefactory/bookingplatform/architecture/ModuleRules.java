package com.codefactory.bookingplatform.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Module rules that hold for every module, present or future (ADR-0001). A module is the first
 * package below the base package; {@code shared} is the shared kernel, not a module. Nothing here
 * names a module, so {@code organization}, {@code catalog} or {@code scheduling} are covered the
 * moment their packages exist.
 *
 * <p>The base package is a parameter so {@link ModuleRulesTest} can run the same rules against
 * fixture classes that break them on purpose.
 */
final class ModuleRules {

    static final String SHARED = "shared";
    static final String AUTH = "auth";
    private static final Set<String> LAYERS = Set.of("api", "application", "domain", "infrastructure");
    private static final Set<String> PUBLIC_LAYERS = Set.of("application", "domain");

    private ModuleRules() {
    }

    /** The first package below {@code base}, or {@code null} for classes outside it or in the base itself. */
    static String moduleOf(String base, JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(base + ".")) {
            return null;
        }
        return packageName.substring(base.length() + 1).split("\\.")[0];
    }

    /** The package right below the module: api, application, domain or infrastructure. */
    static String layerOf(String base, JavaClass javaClass) {
        String[] segments = javaClass.getPackageName().substring(base.length() + 1).split("\\.");
        return segments.length > 1 ? segments[1] : null;
    }

    private static boolean isModule(String module) {
        return module != null && !SHARED.equals(module);
    }

    /** Set.of rejects null lookups, and a class right under its module has no layer. */
    private static boolean isIn(Set<String> layers, String layer) {
        return layer != null && layers.contains(layer);
    }

    /** Another module is reached through its application facade or its domain, never its api or adapters. */
    static ArchRule modulesMeetOnlyThroughApplicationOrDomain(String base) {
        return classes().that().resideInAPackage(base + ".*..")
                .should(new ArchCondition<JavaClass>("reach other modules only through their application or domain layer") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        String from = moduleOf(base, origin);
                        if (!isModule(from)) {
                            return;
                        }
                        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            String to = moduleOf(base, target);
                            if (isModule(to) && !to.equals(from) && !isIn(PUBLIC_LAYERS, layerOf(base, target))) {
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                            }
                        }
                    }
                })
                .because("a module's api and adapters are its own business; the rest of the monolith "
                        + "talks to its use cases");
    }

    /** No module may depend, even indirectly, on a module that depends on it. */
    static ArchRule modulesAreFreeOfCycles(String base) {
        return slices().matching(base + ".(*)..").should().beFreeOfCycles()
                .because("a cycle between modules makes them impossible to evolve or extract separately");
    }

    /** The shared kernel knows no module. */
    static ArchRule sharedKnowsNoModule(String base) {
        return dependsOnNoOtherModule(base, SHARED, "the shared kernel must not know any business module");
    }

    /** Authentication is transversal: it serves every module and depends on none. */
    static ArchRule authKnowsNoBusinessModule(String base) {
        return dependsOnNoOtherModule(base, AUTH, "the transversal auth module must stay independent of business modules");
    }

    private static ArchRule dependsOnNoOtherModule(String base, String module, String reason) {
        return classes().that().resideInAPackage(base + "." + module + "..")
                .should(new ArchCondition<JavaClass>("depend on no module other than itself and shared") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                            String to = moduleOf(base, dependency.getTargetClass());
                            if (isModule(to) && !to.equals(module)) {
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                            }
                        }
                    }
                })
                .because(reason);
    }

    /** Every class of a module sits in one of the four layers, so the layer rules can see it. */
    static ArchRule moduleClassesLiveInALayer(String base) {
        return classes().that().resideInAPackage(base + ".*..")
                .should(new ArchCondition<JavaClass>("reside in the api, application, domain or infrastructure package of its module") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        if (isModule(moduleOf(base, javaClass)) && !isIn(LAYERS, layerOf(base, javaClass))) {
                            events.add(SimpleConditionEvent.violated(javaClass,
                                    javaClass.getName() + " is outside the four layers of its module"));
                        }
                    }
                })
                .because("a class outside the layers escapes every dependency rule");
    }
}
