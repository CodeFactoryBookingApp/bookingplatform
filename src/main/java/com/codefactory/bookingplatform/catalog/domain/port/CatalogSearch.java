package com.codefactory.bookingplatform.catalog.domain.port;

import com.codefactory.bookingplatform.catalog.domain.model.CatalogCriteria;
import com.codefactory.bookingplatform.catalog.domain.model.CatalogPage;

/**
 * The catalog as clients see it (HU-005). A read model: it joins the tables of the organization
 * module too, because "active provider, active location" is part of what a card is.
 */
public interface CatalogSearch {

    CatalogPage search(CatalogCriteria criteria);
}
