package com.banklab.fdservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Shared audit/system column block mandated across every fd_db table (ERD legend:
 * "Mandated audit / system field"). Column names here are unprefixed
 * (USER_ID, WS_ID, ...) rather than per-table-prefixed (e.g. FDA_USER_ID) so the
 * block can be reused as-is by every entity that extends it.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
public abstract class FdAuditableEntity {

    @Column(name = "USER_ID", length = 32)
    private String userId;

    @Column(name = "WS_ID", length = 32)
    private String wsId;

    @Column(name = "LOCAL_TS")
    private LocalDateTime localTs;

    @Column(name = "HOST_TS")
    private LocalDateTime hostTs;

    @Column(name = "RULE_SYSTEM_ID", length = 32)
    private String ruleSystemId;

    @Column(name = "ACPT_TS")
    private LocalDateTime acptTs;

    @Column(name = "ACPT_TS_UTC_OFST", length = 6)
    private String acptTsUtcOfst;

    @Column(name = "UUID", length = 36)
    private String uuid;

    @Column(name = "CRUD_VALUE", length = 1)
    private String crudValue;
}
