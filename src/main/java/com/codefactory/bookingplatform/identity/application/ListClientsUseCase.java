package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.identity.domain.model.ClientFilter;
import com.codefactory.bookingplatform.identity.domain.model.ClientSummary;
import com.codefactory.bookingplatform.identity.domain.port.ClientListing;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** HU-003 client listing of the administrator: a page to browse, or every match to export. */
@Service
public class ListClientsUseCase {

    private final ClientListing listing;

    public ListClientsUseCase(ClientListing listing) {
        this.listing = listing;
    }

    @Transactional(readOnly = true)
    public ResultPage<ClientSummary> page(ClientFilter filter) {
        return listing.find(filter);
    }

    /** HU-003 "Exportar el listado filtrado": the same filter, every page. */
    @Transactional(readOnly = true)
    public List<ClientSummary> export(ClientFilter filter) {
        return listing.findAll(filter);
    }
}
