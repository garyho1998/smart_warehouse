package com.warehouse.mock.jst.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "jst_inventory")
public class JstInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id")
    private String skuId;

    @Column(name = "slot_id")
    private String slotId;

    @Column(name = "wms_co_id")
    private String wmsCoId;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "lock_qty")
    private Integer lockQty;

    @Column(name = "modified_time")
    private Instant modifiedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

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

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Integer getLockQty() {
        return lockQty;
    }

    public void setLockQty(Integer lockQty) {
        this.lockQty = lockQty;
    }

    public Instant getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Instant modifiedTime) {
        this.modifiedTime = modifiedTime;
    }
}
