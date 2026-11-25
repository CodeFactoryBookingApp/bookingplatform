package com.codefactory.bookingplatform.e2e.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/** Thin entry point to the backend under test: the base URI and the JSON content type live here. */
public final class Api {

    private Api() {
    }

    /** A request to the public API, without credentials. */
    public static RequestSpecification anonymous() {
        return RestAssured.given().baseUri(E2eConfig.API_URL).contentType(ContentType.JSON);
    }

    /** A request to the API on behalf of the owner of {@code accessToken}. */
    public static RequestSpecification as(String accessToken) {
        return anonymous().auth().oauth2(accessToken);
    }

    public static Response login(String email, String password) {
        return anonymous().body(Map.of("email", email, "password", password))
                .post("/api/v1/auth/login");
    }
}
