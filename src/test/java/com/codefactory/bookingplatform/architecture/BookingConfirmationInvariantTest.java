package com.codefactory.bookingplatform.architecture;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-001 - guard for the acceptance criterion "cliente no verificado no puede confirmar reserva".
 *
 * <p>Status today: the invariant lives in {@link Client#canConfirmBooking()} and is unit tested,
 * but <strong>no production code calls it</strong>, because Sprint 1 has no booking flow. The
 * criterion therefore cannot be verified through behaviour, and inventing a booking endpoint just
 * to test it would be inventing the feature.
 *
 * <p>What this class does instead is fence the invariant so it cannot be bypassed later:
 *
 * <ol>
 *   <li>the invariant must keep existing as a public method on the aggregate (anchor);</li>
 *   <li>{@code ClientStatus.ACTIVE} must not be read outside the identity domain model - that is
 *       the shape an ad-hoc reimplementation of the rule would take;</li>
 *   <li>the day a booking-confirmation path appears, it must consult the invariant.</li>
 * </ol>
 *
 * Rules 2 and 3 pass vacuously today and start biting the moment the booking module lands.
 *
 * <p>Arrange-Act-Assert in ArchUnit terms: the production classes are imported once
 * ({@link #PRODUCTION_CLASSES}) and the relevant subset is selected (Arrange), the classes are
 * queried for the accesses or calls the rule is about (Act), and the result is checked (Assert).
 */
class BookingConfirmationInvariantTest {

    private static final String ROOT_PACKAGE = "com.codefactory.bookingplatform";

    /** Simple-name suffixes of the classes that would host a booking-confirmation path. */
    private static final List<String> CONFIRMATION_PATH_SUFFIXES =
            List.of("UseCase", "Service", "Controller", "Handler", "Facade");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT_PACKAGE);

    /** Classes that would carry a booking-confirmation path, by package or by name. */
    private static Set<JavaClass> bookingConfirmationPaths() {
        return PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(ROOT_PACKAGE + ".booking")
                        || looksLikeBookingConfirmationPath(javaClass.getSimpleName()))
                .collect(Collectors.toSet());
    }

    /**
     * Linear-time equivalent of {@code simpleName.matches("^(?!BookingPlatform).*Booking.*(UseCase|Service|Controller|Handler|Facade)$")}.
     *
     * <p>The regex needed two unbounded {@code .*} around {@code Booking}, which backtracks
     * super-linearly (Sonar java:S8786). The same language, spelled out: the name does not start
     * with {@code BookingPlatform}, ends with one of the suffixes, and contains {@code Booking}
     * somewhere before that suffix. No suffix is a suffix of another, so at most one can match.
     */
    static boolean looksLikeBookingConfirmationPath(String simpleName) {
        if (simpleName.startsWith("BookingPlatform")) {
            return false;
        }
        return CONFIRMATION_PATH_SUFFIXES.stream()
                .filter(simpleName::endsWith)
                .anyMatch(suffix -> simpleName.substring(0, simpleName.length() - suffix.length()).contains("Booking"));
    }

    @Test
    @DisplayName("HU-001 anchor: the 'only a verified client may confirm bookings' invariant is a public rule of the aggregate")
    void invariantIsPublicOnTheAggregate() throws NoSuchMethodException {
        // Act
        Method method = Client.class.getMethod("canConfirmBooking");

        // Assert
        assertEquals(boolean.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()),
                "the booking module has to be able to ask the aggregate, not re-derive the rule");
    }

    @Test
    @DisplayName("HU-001 guard: no production code outside the identity domain model reads ClientStatus.ACTIVE")
    void nobodyReimplementsTheInvariantByComparingStatus() {
        // Act
        List<String> offenders = PRODUCTION_CLASSES.stream()
                .flatMap(javaClass -> javaClass.getFieldAccessesFromSelf().stream())
                .filter(access -> access.getTargetOwner().isEquivalentTo(ClientStatus.class))
                .filter(access -> "ACTIVE".equals(access.getTarget().getName()))
                .filter(access -> access.getAccessType() == JavaFieldAccess.AccessType.GET)
                // the aggregate itself owns the rule, and the enum's own <clinit>/$values() are noise
                .filter(access -> !access.getOriginOwner().isEquivalentTo(Client.class))
                .filter(access -> !access.getOriginOwner().isEquivalentTo(ClientStatus.class))
                .map(JavaFieldAccess::getDescription)
                .toList();

        // Assert
        assertTrue(offenders.isEmpty(),
                "Comparing the status by hand bypasses Client.canConfirmBooking() and lets the two "
                        + "definitions of 'verified' drift apart. Call the aggregate instead. Offenders: " + offenders);
    }

    @Test
    @DisplayName("HU-001 guard: any booking-confirmation path must consult Client.canConfirmBooking()")
    void bookingConfirmationPathsConsultTheInvariant() {
        // Arrange
        Set<JavaClass> paths = bookingConfirmationPaths();

        // Act
        boolean invariantConsulted = paths.stream()
                .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                .anyMatch(call -> call.getTargetOwner().isEquivalentTo(Client.class)
                        && "canConfirmBooking".equals(call.getName()));

        // Assert
        // With no path at all (Sprint 1 has no booking flow yet) the criterion stays unverifiable by
        // behaviour and the guard passes vacuously, staying armed for the sprint that adds it.
        assertTrue(paths.isEmpty() || invariantConsulted,
                "HU-001 says only a verified client may confirm a booking, but none of these classes asks "
                        + "Client.canConfirmBooking(): "
                        + paths.stream().map(JavaClass::getName).sorted().toList());
    }

    /**
     * Equivalence partitions of the name filter, plus the boundaries of each condition: the
     * {@code BookingPlatform} exclusion only applies as a prefix, {@code Booking} has to appear
     * before the suffix, the suffix has to close the name, and matching is case sensitive.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "BookingService, true",
            "ConfirmBookingUseCase, true",
            "BookingController, true",
            "BookingConfirmationHandler, true",
            "BookingFacade, true",
            "BookingUseCase, true",
            "XBookingPlatformService, true",
            "BookingPlatformService, false",
            "BookingPlatformBookingService, false",
            "BookingPlatformApplication, false",
            "RegisterClientUseCase, false",
            "BookingRepository, false",
            "BookingServiceImpl, false",
            "ServiceBooking, false",
            "Booking, false",
            "UseCase, false",
            "bookingService, false"
    })
    @DisplayName("HU-001 guard: the name filter recognises exactly the booking-confirmation candidates")
    void nameFilterRecognisesBookingConfirmationPaths(String simpleName, boolean expected) {
        // Act
        boolean detected = looksLikeBookingConfirmationPath(simpleName);

        // Assert
        assertEquals(expected, detected);
    }
}
