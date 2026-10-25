package com.codefactory.bookingplatform.organization.api.dto;

import com.codefactory.bookingplatform.organization.domain.model.Category;
import com.codefactory.bookingplatform.organization.domain.model.City;

import java.util.UUID;

/** Rows of the reference catalogs, as the registration form shows them. */
public final class ReferenceDataResponses {

    private ReferenceDataResponses() {
    }

    public record CategoryResponse(UUID id, String name) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.id(), category.name());
        }
    }

    public record CityResponse(UUID id, String name, String region) {

        public static CityResponse from(City city) {
            return new CityResponse(city.id(), city.name(), city.region());
        }
    }
}
