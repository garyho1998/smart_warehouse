package com.warehouse.ontology.adapter.shipbob;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SbProductDto(
        int id,
        String name,
        List<SbVariantDto> variants) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SbVariantDto(
            int id,
            String sku,
            String name,
            String status,
            @JsonProperty("inventory_id") Integer inventoryId,
            SbInventoryRef inventory) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SbInventoryRef(
            @JsonProperty("inventory_id") int inventoryId,
            @JsonProperty("on_hand_qty") int onHandQty) {}
}
