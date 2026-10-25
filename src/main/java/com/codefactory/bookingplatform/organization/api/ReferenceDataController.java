package com.codefactory.bookingplatform.organization.api;

import com.codefactory.bookingplatform.organization.api.dto.ReferenceDataResponses.CategoryResponse;
import com.codefactory.bookingplatform.organization.api.dto.ReferenceDataResponses.CityResponse;
import com.codefactory.bookingplatform.organization.application.ReferenceDataQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public catalogs the registration form needs. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference Data", description = "Categories and cities a provider picks from (HU-002)")
public class ReferenceDataController {

    private final ReferenceDataQuery referenceDataQuery;

    public ReferenceDataController(ReferenceDataQuery referenceDataQuery) {
        this.referenceDataQuery = referenceDataQuery;
    }

    @GetMapping("/organization-categories")
    @Operation(summary = "Business categories")
    public List<CategoryResponse> categories() {
        return referenceDataQuery.categories().stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/cities")
    @Operation(summary = "Cities, with their department")
    public List<CityResponse> cities() {
        return referenceDataQuery.cities().stream().map(CityResponse::from).toList();
    }
}
