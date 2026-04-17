package com.warehouse.ontology.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.warehouse.ontology.OntologyDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = OntologyDemoApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void servesSchemaAndObjectReadEndpoints() throws Exception {
        mockMvc.perform(get("/api/schema/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("Task")))
                .andExpect(jsonPath("$[*].id", hasItem("Warehouse")));

        mockMvc.perform(get("/api/schema/history").queryParam("table", "object_type_def"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tableName", hasItem("object_type_def")));

        mockMvc.perform(get("/api/objects/Task").queryParam("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("TSK-005"));

        mockMvc.perform(get("/api/objects/Task")
                        .queryParam("status", "FAILED")
                        .queryParam("priority", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only one filter parameter is supported"));
    }

    @Test
    void servesGraphEndpoints() throws Exception {
        mockMvc.perform(get("/api/graph/traverse/Task/TSK-005").queryParam("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[*].type", hasItem("Robot")))
                .andExpect(jsonPath("$.nodes[*].type", hasItem("WarehouseOrder")));

        mockMvc.perform(get("/api/graph/trace-anomaly/Task/TSK-005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights[0].message", containsString("batteryPct=15")));
    }

    @Test
    @Transactional
    void executesActionEndpointAndWritesAudit() throws Exception {
        mockMvc.perform(post("/api/actions/completeTask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": "TSK-001",
                                  "actor": "ops-user"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("Task"))
                .andExpect(jsonPath("$.objectId").value("TSK-001"))
                .andExpect(jsonPath("$.updatedObject.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sideEffects[0].targetType").value("Robot"))
                .andExpect(jsonPath("$.sideEffects[0].afterState.status").value("IDLE"));

        assertThatCount(
                "SELECT COUNT(*) FROM audit_trail WHERE action_name = 'completeTask' AND object_id = 'TSK-001'",
                1
        );
        assertThatCount(
                "SELECT COUNT(*) FROM robot WHERE id = 'R-HIK-001' AND status = 'IDLE'",
                1
        );
    }

    @Test
    void supportsSchemaMutationEndpointsAndImmediateCrud() throws Exception {
        mockMvc.perform(post("/api/schema/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "CarrierApi",
                                  "description": "Runtime carrier type",
                                  "primaryKey": "id",
                                  "properties": [
                                    { "name": "id", "type": "string", "required": true },
                                    { "name": "warehouseId", "type": "string", "required": true },
                                    { "name": "code", "type": "string", "required": true, "uniqueCol": true },
                                    { "name": "name", "type": "string", "required": true }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("CarrierApi"));

        mockMvc.perform(post("/api/schema/types/CarrierApi/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "contactNumber",
                                  "type": "string"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("contactNumber"));

        mockMvc.perform(post("/api/schema/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "warehouse_has_carrier_api",
                                  "fromType": "Warehouse",
                                  "toType": "CarrierApi",
                                  "foreignKey": "warehouseId",
                                  "cardinality": "one_to_many",
                                  "description": "Warehouse contains carriers"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("warehouse_has_carrier_api"));

        mockMvc.perform(post("/api/schema/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "renameCarrierApi",
                                  "description": "Rename carrier",
                                  "objectTypeId": "CarrierApi",
                                  "parameters": {
                                    "actor": { "type": "string", "required": true }
                                  },
                                  "preconditions": [],
                                  "mutations": [
                                    { "set": { "name": "Renamed Carrier" } }
                                  ],
                                  "sideEffects": [],
                                  "audit": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("renameCarrierApi"));

        mockMvc.perform(post("/api/objects/CarrierApi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "CAR-API-001",
                                  "warehouseId": "WH-SZ-001",
                                  "code": "SF",
                                  "name": "SF Express",
                                  "contactNumber": "1234"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("CAR-API-001"))
                .andExpect(jsonPath("$.contactNumber").value("1234"));

        mockMvc.perform(get("/api/objects/CarrierApi/CAR-API-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value("WH-SZ-001"))
                .andExpect(jsonPath("$.contactNumber").value("1234"));

        mockMvc.perform(get("/api/schema/history").queryParam("table", "object_type_def"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].recordId", hasItem("CarrierApi")));

        assertThatCount(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE LOWER(TABLE_NAME) = 'carrier_api' "
                        + "AND LOWER(CONSTRAINT_NAME) = 'fk_warehouse_has_carrier_api'",
                1
        );
        assertThatCount(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'carrier_api' "
                        + "AND LOWER(COLUMN_NAME) = 'contact_number'",
                1
        );
    }

    private void assertThatCount(String sql, long expected) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        if (count == null || count != expected) {
            throw new AssertionError("Expected count " + expected + " but got " + count + " for SQL: " + sql);
        }
    }
}
