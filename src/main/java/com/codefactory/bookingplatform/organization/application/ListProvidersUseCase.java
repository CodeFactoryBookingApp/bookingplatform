package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.ProviderFilter;
import com.codefactory.bookingplatform.organization.domain.model.ProviderSummary;
import com.codefactory.bookingplatform.organization.domain.port.ProviderListing;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** HU-003 provider listing of the administrator: a page to browse, or every match to export. */
@Service
public class ListProvidersUseCase {

    private final ProviderListing listing;

    public ListProvidersUseCase(ProviderListing listing) {
        this.listing = listing;
    }

    @Transactional(readOnly = true)
    public ResultPage<ProviderSummary> page(ProviderFilter filter) {
        return listing.find(filter);
    }

    /** HU-003 "Exportar el listado filtrado": the same filter, every page. */
    @Transactional(readOnly = true)
    public List<ProviderSummary> export(ProviderFilter filter) {
        return listing.findAll(filter);
    }
}
