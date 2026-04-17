package com.warehouse.ontology.adapter.jushuitan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
// wms_co_id is Integer in real JST sandbox — Object accepts both int and string
public record JstWarehouseDto(Object wmsCoId, String name, String status) {

    /** Always returns wms_co_id as String regardless of JSON type (int or string). */
    public String wmsCoIdStr() {
        return wmsCoId == null ? null : String.valueOf(wmsCoId);
    }
}
