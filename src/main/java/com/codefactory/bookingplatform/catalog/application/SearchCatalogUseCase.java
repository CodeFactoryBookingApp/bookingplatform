package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.CatalogCriteria;
import com.codefactory.bookingplatform.catalog.domain.model.CatalogPage;
import com.codefactory.bookingplatform.catalog.domain.port.CatalogSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-005: a client searches the catalog of active services of approved providers. Public: the
 * catalog is browsed before signing up.
 *
 * <p>The "próxima franja disponible" of each card needs the availability engine (HU-008); it is
 * added when that HU is built.
 */
@Service
public class SearchCatalogUseCase {

    /** HU-005 "Sin resultados": the client is told and invited to widen the filters. */
    public static final String NO_RESULTS =
            "No hay servicios que coincidan con tu búsqueda. Prueba con otras palabras o quita algún filtro.";

    private final CatalogSearch catalog;

    public SearchCatalogUseCase(CatalogSearch catalog) {
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public CatalogPage search(CatalogCriteria criteria) {
        return catalog.search(criteria);
    }
}
