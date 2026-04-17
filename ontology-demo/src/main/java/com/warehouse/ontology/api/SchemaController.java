package com.warehouse.ontology.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.ontology.schema.ActionTypeDef;
import com.warehouse.ontology.schema.LinkTypeDef;
import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.PropertyDef;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import com.warehouse.ontology.service.SchemaService;
import com.warehouse.ontology.support.NotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final SchemaMetaRepository schemaMetaRepository;
    private final SchemaService schemaService;
    private final ObjectMapper objectMapper;

    public SchemaController(
            SchemaMetaRepository schemaMetaRepository,
            SchemaService schemaService,
            ObjectMapper objectMapper
    ) {
        this.schemaMetaRepository = schemaMetaRepository;
        this.schemaService = schemaService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/types")
    public List<ObjectTypeDef> getTypes() {
        return schemaMetaRepository.getSchema().objectTypes().values().stream().toList();
    }

    @GetMapping("/types/{name}")
    public ObjectTypeDef getType(@PathVariable String name) {
        ObjectTypeDef typeDef = schemaMetaRepository.getSchema().objectTypes().get(name);
        if (typeDef == null) {
            throw new NotFoundException("Unknown object type: " + name);
        }
        return typeDef;
    }

    @PostMapping("/types")
    @ResponseStatus(HttpStatus.CREATED)
    public ObjectTypeDef createType(@RequestBody CreateTypeRequest request) {
        return schemaService.createType(
                new ObjectTypeDef(request.id(), request.description(), request.primaryKey(), Map.of()),
                request.properties() == null
                        ? List.of()
                        : request.properties().stream()
                                .map(property -> new PropertyDef(
                                        null,
                                        request.id(),
                                        property.name(),
                                        property.type(),
                                        property.required() != null && property.required(),
                                        property.uniqueCol() != null && property.uniqueCol(),
                                        property.defaultValue(),
                                        property.enumValues()
                                ))
                                .toList()
        );
    }

    @PostMapping("/types/{name}/properties")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyDef addProperty(@PathVariable String name, @RequestBody PropertyRequest request) {
        return schemaService.addProperty(name, new PropertyDef(
                null,
                name,
                request.name(),
                request.type(),
                request.required() != null && request.required(),
                request.uniqueCol() != null && request.uniqueCol(),
                request.defaultValue(),
                request.enumValues()
        ));
    }

    @GetMapping("/links")
    public List<LinkTypeDef> getLinks() {
        return schemaMetaRepository.getSchema().linkTypes().values().stream().toList();
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkTypeDef createLink(@RequestBody CreateLinkTypeRequest request) {
        return schemaService.createLinkType(new LinkTypeDef(
                request.id(),
                request.fromType(),
                request.toType(),
                request.foreignKey(),
                request.cardinality(),
                request.description()
        ));
    }

    @GetMapping("/actions")
    public List<ActionTypeDef> getActions() {
        return schemaMetaRepository.getSchema().actionTypes().values().stream().toList();
    }

    @PostMapping("/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public ActionTypeDef createAction(@RequestBody CreateActionTypeRequest request) {
        return schemaService.createActionType(new ActionTypeDef(
                request.id(),
                request.description(),
                request.objectTypeId(),
                toJson(request.parameters()),
                toJson(request.preconditions()),
                toJson(request.mutations()),
                toJson(request.sideEffects()),
                request.audit() == null || request.audit()
        ));
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory(@RequestParam(name = "table", required = false) String tableName) {
        return schemaService.getHistory(tableName);
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid schema action payload", exception);
        }
    }

    public record CreateTypeRequest(
            String id,
            String description,
            String primaryKey,
            List<PropertyRequest> properties
    ) {
    }

    public record PropertyRequest(
            String name,
            String type,
            Boolean required,
            Boolean uniqueCol,
            String defaultValue,
            List<String> enumValues
    ) {
    }

    public record CreateLinkTypeRequest(
            String id,
            String fromType,
            String toType,
            String foreignKey,
            String cardinality,
            String description
    ) {
    }

    public record CreateActionTypeRequest(
            String id,
            String description,
            String objectTypeId,
            Map<String, Object> parameters,
            List<Object> preconditions,
            List<Object> mutations,
            List<Object> sideEffects,
            Boolean audit
    ) {
    }
}
