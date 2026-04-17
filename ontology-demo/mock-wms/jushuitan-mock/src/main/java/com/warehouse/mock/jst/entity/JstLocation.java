package com.warehouse.mock.jst.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "jst_location")
public class JstLocation {

    @Id
    @Column(name = "slot_id")
    private String slotId;

    @Column(name = "wms_co_id")
    private String wmsCoId;

    @Column(name = "area")
    private String area;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "modified_time")
    private Instant modifiedTime;

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public String getWmsCoId() {
        return wmsCoId;
    }

    public void setWmsCoId(String wmsCoId) {
        this.wmsCoId = wmsCoId;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public Integer getFloor() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Instant modifiedTime) {
        this.modifiedTime = modifiedTime;
    }
}
