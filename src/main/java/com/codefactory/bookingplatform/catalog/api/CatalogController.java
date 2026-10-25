package com.codefactory.bookingplatform.catalog.api;

import com.codefactory.bookingplatform.catalog.domain.model.CatalogCriteria;
import com.codefactory.bookingplatform.catalog.domain.model.CatalogEntry;
import com.codefactory.bookingplatform.catalog.domain.model.CatalogPage;
import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.application.SearchCatalogUseCase;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** HU-005 public catalog: browsed before signing up, so no token is needed. */
@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog", description = "HU-005 - search of active services of approved providers")
public class CatalogController {

    private final SearchCatalogUseCase searchCatalogUseCase;

    public CatalogController(SearchCatalogUseCase searchCatalogUseCase) {
        this.searchCatalogUseCase = searchCatalogUseCase;
    }

    public record ProviderCard(UUID id, String name, String category) {
    }

    public record LocationCard(UUID id, String name, String city, String region) {
    }

    public record ServiceCard(UUID serviceId, String name, String description, ProviderCard provider,
                              LocationCard location, int durationMinutes, BigDecimal price, String currency,
                              int customerCapacity, boolean group) {

        static ServiceCard from(CatalogEntry entry) {
            return new ServiceCard(entry.serviceId(), entry.serviceName(), entry.description(),
                    new ProviderCard(entry.providerId(), entry.providerName(), entry.category()),
                    new LocationCard(entry.locationId(), entry.locationName(), entry.city(), entry.region()),
                    entry.durationMinutes(), entry.price(), OfferedService.CURRENCY, entry.customerCapacity(),
                    entry.customerCapacity() > 1);
        }
    }

    /** {@code message} is present only when nothing matched (HU-005 "Sin resultados"). */
    public record CatalogResponse(List<ServiceCard> items, long total, int page, int size, String message) {

        static CatalogResponse from(CatalogPage page) {
            return new CatalogResponse(page.entries().stream().map(ServiceCard::from).toList(), page.total(),
                    page.page(), page.size(), page.total() == 0 ? SearchCatalogUseCase.NO_RESULTS : null);
        }
    }

    @GetMapping("/services")
    @Operation(summary = "Search the catalog",
            description = "Active services of approved providers, one card per location where they are provided. "
                    + "Filters combine with AND; the keyword matches the service name or description or the provider "
                    + "name, ignoring case and accents.")
    public CatalogResponse search(
            @Parameter(description = "Keyword, without caring about case or accents") @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID cityId,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "name, price, duration or provider") @RequestParam(defaultValue = "name") String sort,
            @Parameter(description = "asc or desc") @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CatalogCriteria criteria = new CatalogCriteria(q, categoryId, cityId, providerId, minPrice, maxPrice,
                sortOf(sort), descending(direction), page, size);
        return CatalogResponse.from(searchCatalogUseCase.search(criteria));
    }

    private static CatalogCriteria.Sort sortOf(String sort) {
        try {
            return CatalogCriteria.Sort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid search",
                    Map.of("sort", "must be name, price, duration or provider"));
        }
    }

    private static boolean descending(String direction) {
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> false;
            case "desc" -> true;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid search",
                    Map.of("direction", "must be asc or desc"));
        };
    }
}
