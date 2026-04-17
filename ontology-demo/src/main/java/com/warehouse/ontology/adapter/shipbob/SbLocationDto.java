package com.warehouse.ontology.adapter.shipbob;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SbLocationDto(
        int id,
        String name,
        String abbreviation,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("access_granted") boolean accessGranted,
        SbRegionDto region) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SbRegionDto(int id, String name) {}
}
