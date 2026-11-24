package com.codefactory.bookingplatform.booking.domain.port;

import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Filter;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Row;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Summary;

import java.util.List;
import java.util.UUID;

public interface BookingQueries {

    List<Row> search(UUID organizationId, Filter filter, int limit, long offset);

    List<Row> export(UUID organizationId, Filter filter);

    Summary summarize(UUID organizationId, Filter filter);
}
