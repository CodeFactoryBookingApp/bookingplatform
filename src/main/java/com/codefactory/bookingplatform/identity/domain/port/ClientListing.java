package com.codefactory.bookingplatform.identity.domain.port;

import com.codefactory.bookingplatform.identity.domain.model.ClientFilter;
import com.codefactory.bookingplatform.identity.domain.model.ClientSummary;
import com.codefactory.bookingplatform.shared.listing.ResultPage;

import java.util.List;

/** The client listing of the administrator (HU-003), read in SQL. */
public interface ClientListing {

    /** The largest export; a filter that matches more should be narrowed. */
    int MAX_EXPORT = 10_000;

    ResultPage<ClientSummary> find(ClientFilter filter);

    /** Every row the filter matches, ignoring its page, up to {@link #MAX_EXPORT}, for the CSV export. */
    List<ClientSummary> findAll(ClientFilter filter);
}
