package com.warehouse.ontology.adapter.jushuitan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.deser.std.ToStringDeserializer;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
// wms_co_id is Integer in real JST sandbox — ToStringDeserializer accepts any JSON scalar
public record JstWarehouseDto(
        @JsonDeserialize(using = ToStringDeserializer.class) String wmsCoId,
        String name,
        String status) {}
