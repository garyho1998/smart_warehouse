package com.warehouse.ontology.api;

import com.warehouse.ontology.engine.GenericRepository;
import com.warehouse.ontology.support.NotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/objects")
public class ObjectController {

    private final GenericRepository genericRepository;

    public ObjectController(GenericRepository genericRepository) {
        this.genericRepository = genericRepository;
    }

    @GetMapping("/{type}")
    public List<Map<String, Object>> list(
            @PathVariable String type,
            @RequestParam MultiValueMap<String, String> filters
    ) {
        if (filters.size() > 1 || filters.values().stream().anyMatch(values -> values.size() > 1)) {
            throw new IllegalArgumentException("Only one filter parameter is supported");
        }
        if (filters.isEmpty()) {
            return genericRepository.findAll(type);
        }

        String propertyName = filters.keySet().iterator().next();
        String value = filters.getFirst(propertyName);
        return genericRepository.findByProperty(type, propertyName, value);
    }

    @GetMapping("/{type}/{id}")
    public Map<String, Object> getById(@PathVariable String type, @PathVariable String id) {
        return genericRepository.findById(type, id)
                .orElseThrow(() -> new NotFoundException("Unknown object instance: " + type + ":" + id));
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable String type, @RequestBody Map<String, Object> payload) {
        String id = genericRepository.insert(type, payload);
        return genericRepository.findById(type, id)
                .orElseThrow(() -> new IllegalStateException("Unable to load created object: " + type + ":" + id));
    }

    @PutMapping("/{type}/{id}")
    public Map<String, Object> update(
            @PathVariable String type,
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        if (genericRepository.findById(type, id).isEmpty()) {
            throw new NotFoundException("Unknown object instance: " + type + ":" + id);
        }
        genericRepository.update(type, id, payload);
        return genericRepository.findById(type, id)
                .orElseThrow(() -> new IllegalStateException("Unable to reload updated object: " + type + ":" + id));
    }
}
