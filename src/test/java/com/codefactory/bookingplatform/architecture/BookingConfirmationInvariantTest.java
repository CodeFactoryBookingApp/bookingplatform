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
 */
class BookingConfirmationInvariantTest {

    private static final String ROOT_PACKAGE = "com.codefactory.bookingplatform";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT_PACKAGE);

    /** Classes that would carry a booking-confirmation path, by package or by name. */
    private static Set<JavaClass> bookingConfirmationPaths() {
        return PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(ROOT_PACKAGE + ".booking")
                        || javaClass.getSimpleName()
                        .matches("^(?!BookingPlatform).*Booking.*(UseCase|Service|Controller|Handler|Facade)$"))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("HU-001 anchor: the 'only a verified client may confirm bookings' invariant is a public rule of the aggregate")
    void invariantIsPublicOnTheAggregate() throws NoSuchMethodException {
        var method = Client.class.getMethod("canConfirmBooking");
        assertEquals(boolean.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()),
                "the booking module has to be able to ask the aggregate, not re-derive the rule");
    }

    @Test
    @DisplayName("HU-001 guard: no production code outside the identity domain model reads ClientStatus.ACTIVE")
    void nobodyReimplementsTheInvariantByComparingStatus() {
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

        assertTrue(offenders.isEmpty(),
                "Comparing the status by hand bypasses Client.canConfirmBooking() and lets the two "
                        + "definitions of 'verified' drift apart. Call the aggregate instead. Offenders: " + offenders);
    }

    @Test
    @DisplayName("HU-001 guard: any booking-confirmation path must consult Client.canConfirmBooking()")
    void bookingConfirmationPathsConsultTheInvariant() {
        Set<JavaClass> paths = bookingConfirmationPaths();
        if (paths.isEmpty()) {
            // Sprint 1 has no booking flow yet; the criterion stays unverifiable by behaviour and
            // this guard stays armed for the sprint that adds it.
            return;
        }

        boolean invariantConsulted = paths.stream()
                .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                .anyMatch(call -> call.getTargetOwner().isEquivalentTo(Client.class)
                        && "canConfirmBooking".equals(call.getName()));

        assertTrue(invariantConsulted,
                "HU-001 says only a verified client may confirm a booking, but none of these classes asks "
                        + "Client.canConfirmBooking(): "
                        + paths.stream().map(JavaClass::getName).sorted().toList());
    }
}
