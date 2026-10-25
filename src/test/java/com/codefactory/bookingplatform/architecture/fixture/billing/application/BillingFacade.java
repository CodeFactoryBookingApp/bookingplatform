package com.codefactory.bookingplatform.architecture.fixture.billing.application;

import com.codefactory.bookingplatform.architecture.fixture.orders.domain.Order;

/** Allowed on its own, but together with PlaceOrder it closes a cycle orders -> billing -> orders. */
public class BillingFacade {
    Order order;
}
