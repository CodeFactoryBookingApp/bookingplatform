package com.codefactory.bookingplatform.organization.domain.port;

import com.codefactory.bookingplatform.organization.domain.model.Category;
import com.codefactory.bookingplatform.organization.domain.model.City;

import java.util.List;
import java.util.UUID;

/** The read-only catalogs a provider picks from when it registers (V5). */
public interface ReferenceDataRepository {

    List<Category> categories();

    List<City> cities();

    boolean categoryExists(UUID id);

    boolean cityExists(UUID id);
}
