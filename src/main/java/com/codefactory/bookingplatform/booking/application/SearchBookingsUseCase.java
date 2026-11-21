package com.codefactory.bookingplatform.booking.application;

import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Filter;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Page;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Row;
import com.codefactory.bookingplatform.booking.domain.port.BookingQueries;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import com.codefactory.bookingplatform.shared.listing.ListingParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchBookingsUseCase {

    private final OrganizationAccess access;
    private final BookingQueries bookings;

    public SearchBookingsUseCase(OrganizationAccess access, BookingQueries bookings) {
        this.access = access;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page search(UUID organizationId, UUID actor, Filter filter, int page, int size) {
        access.requireMember(organizationId, actor);
        Map<String, String> errors = new LinkedHashMap<>();
        ListingParams.checkWindow(page, size, errors);
        validate(filter, errors);
        return new Page(bookings.search(organizationId, filter, size, (long) page * size),
                bookings.summarize(organizationId, filter), page, size);
    }

    @Transactional(readOnly = true)
    public List<Row> export(UUID organizationId, UUID actor, Filter filter) {
        access.requireMember(organizationId, actor);
        validate(filter, new LinkedHashMap<>());
        return bookings.export(organizationId, filter);
    }

    @Transactional(readOnly = true)
    public Row byCode(UUID organizationId, UUID actor, String code) {
        access.requireMember(organizationId, actor);
        Filter filter = Filter.byCode(code);
        validate(filter, new LinkedHashMap<>());
        return bookings.search(organizationId, filter, 1, 0).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Booking not found"));
    }

    private static void validate(Filter filter, Map<String, String> errors) {
        if (filter.from() != null && filter.to() != null && !filter.to().isAfter(filter.from())) {
            errors.put("to", "must be after from");
        }
        if (filter.code() != null && (filter.code().isBlank() || filter.code().length() > 64)) {
            errors.put("code", "must contain between 1 and 64 characters");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid booking filters", errors);
        }
    }
}
