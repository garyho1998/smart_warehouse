package com.warehouse.ontology.adapter.jushuitan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JstLocationDto(String slotId, String wmsCoId, String area, Integer floor, Boolean enabled, Instant modifiedTime) {}
