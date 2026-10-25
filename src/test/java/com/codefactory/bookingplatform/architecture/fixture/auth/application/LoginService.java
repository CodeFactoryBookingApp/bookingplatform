package com.codefactory.bookingplatform.architecture.fixture.auth.application;

import com.codefactory.bookingplatform.architecture.fixture.orders.domain.Order;

/** Authentication reaching into a business module, even through its domain. */
public class LoginService {
    Order order;
}
