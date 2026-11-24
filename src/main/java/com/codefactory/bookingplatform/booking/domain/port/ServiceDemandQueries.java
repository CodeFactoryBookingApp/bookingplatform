package com.codefactory.bookingplatform.booking.domain.port;

import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Bucket;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Group;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Window;

import java.util.List;
import java.util.UUID;

public interface ServiceDemandQueries {

    String timezone(UUID organizationId);

    List<Group> metrics(UUID organizationId, Window period);

    List<Bucket> demand(UUID organizationId, Window period, String timezone);
}
