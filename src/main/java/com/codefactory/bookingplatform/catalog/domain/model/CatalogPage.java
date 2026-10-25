package com.codefactory.bookingplatform.catalog.domain.model;

import java.util.List;

/** A page of the catalog and how many cards match in total, for the client to paginate. */
public record CatalogPage(List<CatalogEntry> entries, long total, int page, int size) {

    public CatalogPage {
        entries = List.copyOf(entries);
    }
}
