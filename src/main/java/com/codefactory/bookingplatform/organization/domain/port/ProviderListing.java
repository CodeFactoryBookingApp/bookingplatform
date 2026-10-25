package com.codefactory.bookingplatform.organization.domain.port;

import com.codefactory.bookingplatform.organization.domain.model.ProviderFilter;
import com.codefactory.bookingplatform.organization.domain.model.ProviderSummary;
import com.codefactory.bookingplatform.shared.listing.ResultPage;

import java.util.List;

/** The provider listing of the administrator (HU-003), read in SQL. */
public interface ProviderListing {

    /** The largest export; a filter that matches more should be narrowed. */
    int MAX_EXPORT = 10_000;

    ResultPage<ProviderSummary> find(ProviderFilter filter);

    /** Every row the filter matches, ignoring its page, up to {@link #MAX_EXPORT}, for the CSV export. */
    List<ProviderSummary> findAll(ProviderFilter filter);
}
