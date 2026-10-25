package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.Category;
import com.codefactory.bookingplatform.organization.domain.model.City;
import com.codefactory.bookingplatform.organization.domain.port.ReferenceDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The catalogs a registration form needs to offer its choices (V5). */
@Service
public class ReferenceDataQuery {

    private final ReferenceDataRepository referenceData;

    public ReferenceDataQuery(ReferenceDataRepository referenceData) {
        this.referenceData = referenceData;
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return referenceData.categories();
    }

    @Transactional(readOnly = true)
    public List<City> cities() {
        return referenceData.cities();
    }
}
