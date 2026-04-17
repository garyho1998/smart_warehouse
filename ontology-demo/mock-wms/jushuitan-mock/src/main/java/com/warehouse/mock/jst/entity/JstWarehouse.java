package com.warehouse.mock.jst.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "jst_warehouse")
public class JstWarehouse {

    @Id
    @Column(name = "wms_co_id")
    private String wmsCoId;

    @Column(name = "name")
    private String name;

    @Column(name = "modified_time")
    private Instant modifiedTime;

    public String getWmsCoId() {
        return wmsCoId;
    }

    public void setWmsCoId(String wmsCoId) {
        this.wmsCoId = wmsCoId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Instant modifiedTime) {
        this.modifiedTime = modifiedTime;
    }
}
