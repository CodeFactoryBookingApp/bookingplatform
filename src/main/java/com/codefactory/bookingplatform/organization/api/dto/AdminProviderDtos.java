package com.codefactory.bookingplatform.organization.api.dto;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.model.ProviderSummary;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bodies of the HU-003 provider administration. */
public final class AdminProviderDtos {

    private AdminProviderDtos() {
    }

    public record ProviderRow(UUID id, String name, IdentificationType identificationType,
                              String identificationNumber, String category, String contactEmail, String contactPhone,
                              String cities, OrganizationStatus status, Instant registeredAt, Instant approvedAt) {

        static ProviderRow from(ProviderSummary summary) {
            return new ProviderRow(summary.id(), summary.name(), summary.identificationType(),
                    summary.identificationNumber(), summary.category(), summary.contactEmail(),
                    summary.contactPhone(), summary.cities(), summary.status(), summary.registeredAt(),
                    summary.approvedAt());
        }
    }

    public record ProviderPage(List<ProviderRow> items, long total, int page, int size) {

        public static ProviderPage from(ResultPage<ProviderSummary> page) {
            ResultPage<ProviderRow> rows = page.map(ProviderRow::from);
            return new ProviderPage(rows.items(), rows.total(), rows.page(), rows.size());
        }
    }

    /** The CSV columns of the export, in order. */
    public static final List<String> CSV_HEADER = List.of("id", "nombre", "tipo_identificacion",
            "numero_identificacion", "categoria", "correo_contacto", "telefono_contacto", "ciudades", "estado",
            "registrado", "aprobado");

    public static List<String> csvRow(ProviderSummary summary) {
        return List.of(summary.id().toString(), summary.name(), summary.identificationType().name(),
                summary.identificationNumber(), summary.category(), summary.contactEmail(), summary.contactPhone(),
                Objects.toString(summary.cities(), ""), summary.status().name(), summary.registeredAt().toString(),
                Objects.toString(summary.approvedAt(), ""));
    }

    @Schema(description = "HU-003: suspending demands a reason, and a confirmation when the provider has future bookings")
    public record SuspensionRequest(

            @NotBlank(message = "reason is required")
            @Size(max = 500, message = "reason must be at most 500 characters")
            String reason,

            @Schema(description = "Must be true to suspend a provider with future bookings; the first answer says how many")
            Boolean confirm) {

        public boolean confirmed() {
            return Boolean.TRUE.equals(confirm);
        }
    }
}
