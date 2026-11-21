package com.codefactory.bookingplatform.architecture;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
 *   <li>{@code ClientStatus.ACTIVE} must not be read outside the identity domain model - that is
 *       the shape an ad-hoc reimplementation of the rule would take;</li>
 *   <li>the day a booking-confirmation path appears, it must consult the invariant.</li>
 * </ol>
 *
 * The second rule starts applying when the application adds a booking write path. Searches,
 * reports and consumers of booking events can already exist without confirming any reservation.
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
    private static final List<String> BOOKING_WRITE_VERBS = List.of("create", "confirm", "reserve", "reschedule");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT_PACKAGE);

    /** A booking write is located by its action or by a writable transaction in the booking module. */
    private static Set<JavaClass> bookingConfirmationPaths() {
        return PRODUCTION_CLASSES.stream()
                .filter(BookingConfirmationInvariantTest::canWriteBooking)
                .collect(Collectors.toSet());
    }

    private static boolean canWriteBooking(JavaClass javaClass) {
        String name = javaClass.getSimpleName();
        String bookingPackage = ROOT_PACKAGE + ".booking.";
        if (name.startsWith("BookingPlatform") || CONFIRMATION_PATH_SUFFIXES.stream().noneMatch(name::endsWith)) {
            return false;
        }
        boolean inBookingModule = javaClass.getPackageName().startsWith(bookingPackage);
        if (!inBookingModule && !name.contains("Booking")) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        return BOOKING_WRITE_VERBS.stream().anyMatch(lowerName::startsWith)
                || javaClass.getMethods().stream().anyMatch(method ->
                        BOOKING_WRITE_VERBS.stream().anyMatch(method.getName()::startsWith)
                                || (inBookingModule && writesInTransaction(method)));
    }

    private static boolean writesInTransaction(JavaMethod method) {
        return method.isAnnotatedWith(Transactional.class) && !method.getAnnotationOfType(Transactional.class).readOnly();
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
        // Read-side booking features do not exercise the application invariant. The database
        // integration tests cover confirmation at the PostgreSQL boundary in the meantime.
        assertTrue(paths.isEmpty() || invariantConsulted,
                "HU-001 says only a verified client may confirm a booking, but none of these classes asks "
                        + "Client.canConfirmBooking(): "
                        + paths.stream().map(JavaClass::getName).sorted().toList());
    }
}
