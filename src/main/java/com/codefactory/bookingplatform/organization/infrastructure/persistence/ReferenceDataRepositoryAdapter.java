package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.organization.domain.model.Category;
import com.codefactory.bookingplatform.organization.domain.model.City;
import com.codefactory.bookingplatform.organization.domain.port.ReferenceDataRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ReferenceDataRepositoryAdapter implements ReferenceDataRepository {

    private final OrganizationCategoryJpaRepository categories;
    private final CityJpaRepository cities;

    public ReferenceDataRepositoryAdapter(OrganizationCategoryJpaRepository categories, CityJpaRepository cities) {
        this.categories = categories;
        this.cities = cities;
    }

    @Override
    public List<Category> categories() {
        return categories.findAll(Sort.by("name")).stream()
                .map(category -> new Category(category.getId(), category.getName()))
                .toList();
    }

    @Override
    public List<City> cities() {
        return cities.findAll(Sort.by("name", "region")).stream()
                .map(city -> new City(city.getId(), city.getName(), city.getRegion()))
                .toList();
    }

    @Override
    public boolean categoryExists(UUID id) {
        return id != null && categories.existsById(id);
    }

    @Override
    public boolean cityExists(UUID id) {
        return id != null && cities.existsById(id);
    }
}
