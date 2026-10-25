package com.codefactory.bookingplatform.organization.domain.model;

/**
 * Role of a person inside one organization (ADR-0008). The platform role in the JWT says what
 * kind of user someone is; this one says what they may do in a given provider:
 *
 * <ul>
 *   <li>OWNER: the provider who registered the business (HU-022 "proveedor");</li>
 *   <li>MANAGER: the operations manager (HU-022 "gerente");</li>
 *   <li>STAFF: the receptionist and other staff (HU-022 "recepcionista").</li>
 * </ul>
 */
public enum MemberRole {
    OWNER,
    MANAGER,
    STAFF
}
