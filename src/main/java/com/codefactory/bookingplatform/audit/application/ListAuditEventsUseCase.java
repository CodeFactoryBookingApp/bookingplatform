package com.codefactory.bookingplatform.audit.application;

import com.codefactory.bookingplatform.audit.domain.model.AuditEntry;
import com.codefactory.bookingplatform.audit.domain.model.AuditFilter;
import com.codefactory.bookingplatform.audit.domain.port.AuditEvents;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import com.codefactory.bookingplatform.shared.listing.ListingParams;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ListAuditEventsUseCase {

    private final AuditEvents events;
    private final OrganizationAccess access;

    public ListAuditEventsUseCase(AuditEvents events, OrganizationAccess access) {
        this.events = events;
        this.access = access;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultPage<AuditEntry> page(AuthenticatedUser actor, UUID organizationId, AuditFilter filter,
                                      int page, int size) {
        requireAccess(actor, organizationId);
        Map<String, String> errors = new LinkedHashMap<>();
        ListingParams.checkWindow(page, size, errors);
        validate(filter, errors);
        return new ResultPage<>(events.page(organizationId, filter, size, (long) page * size),
                events.count(organizationId, filter), page, size);
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> export(AuthenticatedUser actor, UUID organizationId, AuditFilter filter) {
        requireAccess(actor, organizationId);
        validate(filter, new LinkedHashMap<>());
        return events.findAll(organizationId, filter);
    }

    private void requireAccess(AuthenticatedUser actor, UUID organizationId) {
        if (organizationId != null) {
            access.requireMember(organizationId, actor.id());
        } else if (!actor.admin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static void validate(AuditFilter filter, Map<String, String> errors) {
        if (filter.from() != null && filter.to() != null && !filter.to().isAfter(filter.from())) {
            errors.put("to", "must be after from");
        }
        if (filter.action() != null && (filter.action().isBlank() || filter.action().length() > 64)) {
            errors.put("action", "must contain between 1 and 64 characters");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid audit filters", errors);
        }
    }
}
