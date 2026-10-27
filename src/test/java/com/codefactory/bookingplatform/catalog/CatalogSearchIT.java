package com.codefactory.bookingplatform.catalog;

import com.codefactory.bookingplatform.catalog.application.SearchCatalogUseCase;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HU-005 end to end, without a token: the catalog is public. Other test classes share the
 * database, so every search here is narrowed by a word unique to the test or by its provider.
 *
 * <p>Arrange-Act-Assert: the providers and services are the Arrange; the Act is the search.
 */
class CatalogSearchIT extends JwtIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private ProviderFixture fixture;
    private String word;

    @BeforeEach
    void setUp() {
        fixture = new ProviderFixture(jdbc);
        word = "zq" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MockHttpServletResponse search(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder catalog() {
        return get("/api/v1/catalog/services");
    }

    private static List<String> names(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.items[*].name");
    }

    private static int total(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.total");
    }

    @Test
    @DisplayName("HU-005: a keyword typed in capitals and without accents finds the service written with them")
    void keywordIgnoresCaseAndAccents() throws Exception {
        // Arrange
        Provider provider = fixture.activeProvider();
        fixture.service(provider, "Corte clásico " + word, null, 35000, "ACTIVE");

        // Act
        MockHttpServletResponse response = search(catalog().param("q", "CLASICO " + word.toUpperCase()));

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals(List.of("Corte clásico " + word), names(response));
    }

    @Test
    @DisplayName("HU-005: the keyword also matches the description and the name of the provider")
    void keywordMatchesDescriptionAndProvider() throws Exception {
        // Arrange
        Provider provider = fixture.provider("Peluquería Única " + word, "ACTIVE", null, null);
        fixture.service(provider, "Corte", null, 30000, "ACTIVE");
        fixture.service(fixture.activeProvider(), "Barba", "Perfilado con navaja " + word, 20000, "ACTIVE");

        // Act
        MockHttpServletResponse response = search(catalog().param("q", word).param("sort", "price"));

        // Assert
        assertEquals(List.of("Barba", "Corte"), names(response));
    }

    @Test
    @DisplayName("HU-005: services of pending or suspended providers, and inactive services, are not shown")
    void onlyActiveServicesOfActiveProvidersAreShown() throws Exception {
        // Arrange
        fixture.service(fixture.provider("Pendiente " + word, "PENDING_APPROVAL", null, null), "Visible no", null, 1000, "ACTIVE");
        fixture.service(fixture.provider("Suspendido " + word, "SUSPENDED", null, null), "Visible no", null, 1000, "ACTIVE");
        Provider active = fixture.provider("Activo " + word, "ACTIVE", null, null);
        fixture.service(active, "Inactivo", null, 1000, "INACTIVE");
        fixture.service(active, "Activo", null, 1000, "ACTIVE");

        // Act
        MockHttpServletResponse response = search(catalog().param("q", word));

        // Assert
        assertEquals(List.of("Activo"), names(response));
    }

    @Test
    @DisplayName("HU-005: category, city and price range combine: only services meeting all of them are shown")
    void filtersCombine() throws Exception {
        // Arrange
        Provider clinic = fixture.provider("Clínica " + word, "ACTIVE", "Salud", "Medellín");
        Provider salon = fixture.provider("Salón " + word, "ACTIVE", "Belleza", "Medellín");
        Provider clinicInCali = fixture.provider("Clínica Cali " + word, "ACTIVE", "Salud", "Cali");
        fixture.service(clinic, "Limpieza dental", null, 90000, "ACTIVE");
        fixture.service(clinic, "Blanqueamiento", null, 250000, "ACTIVE");
        fixture.service(salon, "Manicure", null, 60000, "ACTIVE");
        fixture.service(clinicInCali, "Ortodoncia", null, 80000, "ACTIVE");
        String salud = jdbc.queryForObject("select id::text from organization_categories where name = 'Salud'", String.class);
        String medellin = jdbc.queryForObject("select id::text from cities where name = 'Medellín'", String.class);

        // Act
        MockHttpServletResponse response = search(catalog().param("q", word).param("categoryId", salud)
                .param("cityId", medellin).param("minPrice", "50000").param("maxPrice", "100000"));

        // Assert
        assertEquals(List.of("Limpieza dental"), names(response));
    }

    @Test
    @DisplayName("Sorting by price descending, two per page: the first page has the two dearest and the total counts all")
    void sortsAndPaginates() throws Exception {
        // Arrange
        Provider provider = fixture.activeProvider();
        fixture.service(provider, "Barato", null, 1000, "ACTIVE");
        fixture.service(provider, "Caro", null, 3000, "ACTIVE");
        fixture.service(provider, "Medio", null, 2000, "ACTIVE");

        // Act
        MockHttpServletResponse response = search(catalog().param("providerId", provider.organizationId().toString())
                .param("sort", "price").param("direction", "desc").param("size", "2"));

        // Assert
        assertEquals(List.of("Caro", "Medio"), names(response));
        assertEquals(3, total(response));
    }

    @Test
    @DisplayName("A service provided in two locations is two cards, one per location")
    void oneCardPerLocation() throws Exception {
        // Arrange
        Provider provider = fixture.activeProvider();
        UUID service = fixture.service(provider, "Corte " + word, null, 30000, "ACTIVE");
        fixture.offerAt(provider, service, fixture.addLocation(provider.organizationId(), "Envigado"));

        // Act
        MockHttpServletResponse response = search(catalog().param("q", word));

        // Assert
        assertEquals(2, total(response));
        List<String> serviceIds = JsonPath.read(response.getContentAsString(), "$.items[*].serviceId");
        assertEquals(List.of(service.toString(), service.toString()), serviceIds);
        List<String> locationIds = JsonPath.read(response.getContentAsString(), "$.items[*].location.id");
        assertEquals(2, new HashSet<>(locationIds).size(), "each card is a different location");
    }

    @Test
    @DisplayName("HU-005: with no match the answer is empty and suggests widening the filters")
    void noResultsSuggestsWideningTheFilters() throws Exception {
        // Act
        MockHttpServletResponse response = search(catalog().param("q", word));

        // Assert
        assertEquals(200, response.getStatus());
        assertEquals(0, total(response));
        assertEquals(SearchCatalogUseCase.NO_RESULTS, JsonPath.read(response.getContentAsString(), "$.message"));
    }

    @Test
    @DisplayName("A % in the keyword is searched literally, not as 'anything'")
    void percentIsLiteral() throws Exception {
        // Arrange
        Provider provider = fixture.activeProvider();
        fixture.service(provider, "Corte", null, 30000, "ACTIVE");

        // Act
        MockHttpServletResponse response = search(catalog().param("q", "%")
                .param("providerId", provider.organizationId().toString()));

        // Assert
        assertEquals(0, total(response));
    }

    @Test
    @DisplayName("With results there is no message")
    void resultsCarryNoMessage() throws Exception {
        // Arrange
        fixture.service(fixture.activeProvider(), "Corte " + word, null, 30000, "ACTIVE");

        // Act
        MockHttpServletResponse response = search(catalog().param("q", word));

        // Assert
        assertNull(JsonPath.read(response.getContentAsString(), "$.message"));
    }

    @Test
    @DisplayName("An unknown sort column or an inverted price range is a 400")
    void invalidParametersAreRejected() throws Exception {
        // Act
        MockHttpServletResponse response = search(catalog().param("sort", "drop table"));

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals(400, search(catalog().param("minPrice", "10").param("maxPrice", "5")).getStatus());
    }
}
