package com.codefactory.bookingplatform.architecture.fixture.orders.application;

import com.codefactory.bookingplatform.architecture.fixture.billing.application.BillingFacade;
import com.codefactory.bookingplatform.architecture.fixture.billing.infrastructure.InvoiceGateway;

/** Allowed: another module's application facade. Forbidden: its adapter. */
public class PlaceOrder {
    BillingFacade billing;
    InvoiceGateway invoices;
}
