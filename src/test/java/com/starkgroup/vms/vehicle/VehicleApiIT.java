package com.starkgroup.vms.vehicle;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starkgroup.vms.user.User;
import com.starkgroup.vms.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        vehicleRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("admin", passwordEncoder.encode("admin123"), "Stark Group Administrator", "ADMIN"));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "admin123")))
                )
                .andExpect(status().isOk())
                .andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private String body(String number, String company, Object type, String brand, String model, String status)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleNumber", number);
        payload.put("company", company);
        payload.put("type", type);
        payload.put("brand", brand);
        payload.put("model", model);
        payload.put("status", status);
        return objectMapper.writeValueAsString(payload);
    }

    private String validBody(String number) throws Exception {
        return body(number, "Stark Construction", "PICKUP", "Toyota", "Hilux", "ACTIVE");
    }

    private long createVehicle(String number) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content(validBody(number)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void seedFleet(int count) {
        String[] companies = {"Stark Group", "Stark Construction", "Stark Logistics"};
        for (int i = 0; i < count; i++) {
            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleNumber(String.format("DHA-GA-11-%04d", 1001 + i));
            vehicle.setCompany(companies[i % companies.length]);
            vehicle.setType(VehicleType.PICKUP);
            vehicle.setBrand("Toyota");
            vehicle.setModel("Hilux");
            vehicle.setStatus(VehicleStatus.ACTIVE);
            vehicleRepository.save(vehicle);
        }
        vehicleRepository.flush();
    }

    // ---------- Authentication ----------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/vehicles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---------- Paginated list ----------

    @Test
    void listReturnsRequiredSpringPageShape() throws Exception {
        seedFleet(24);

        mockMvc.perform(authed(get("/vehicles").param("page", "0").param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(24))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].vehicleNumber").value("DHA-GA-11-1001"))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.numberOfElements").doesNotExist());
    }

    @Test
    void lastPageFlagsLastTrue() throws Exception {
        seedFleet(24);

        mockMvc.perform(authed(get("/vehicles").param("page", "2").param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void pageBeyondRangeReturnsEmptyContentAndLastTrue() throws Exception {
        seedFleet(24);

        mockMvc.perform(authed(get("/vehicles").param("page", "5").param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(24))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void oversizedPageSizeIsClamped() throws Exception {
        seedFleet(24);

        mockMvc.perform(authed(get("/vehicles").param("page", "0").param("size", "1000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listRejectsUnknownSortField() throws Exception {
        mockMvc.perform(authed(get("/vehicles").param("sort", "passwordHash,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported sort field")));
    }

    @Test
    void listSortsByCompanyWhenRequested() throws Exception {
        seedFleet(24);

        mockMvc.perform(authed(get("/vehicles").param("sort", "company,asc").param("size", "5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].company").value("Stark Construction"));
    }

    // ---------- Create / read ----------

    @Test
    void createVehicleReturns201WithNormalizedNumber() throws Exception {
        mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content(body("dha-ga-11-2001", "Stark Construction", "PICKUP", "Toyota", "Hilux", "ACTIVE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.vehicleNumber").value("DHA-GA-11-2001"))
                .andExpect(jsonPath("$.company").value("Stark Construction"))
                .andExpect(jsonPath("$.type").value("PICKUP"))
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.model").value("Hilux"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void statusDefaultsToActiveWhenOmitted() throws Exception {
        mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content(body("DHA-GA-11-2002", "Stark Group", "SUV", "Toyota", "Corolla Cross", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getSingleVehicleReturnsRecord() throws Exception {
        long id = createVehicle("DHA-GA-11-2003");

        mockMvc.perform(authed(get("/vehicles/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.vehicleNumber").value("DHA-GA-11-2003"));
    }

    @Test
    void getMissingVehicleReturns404() throws Exception {
        mockMvc.perform(authed(get("/vehicles/{id}", 999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("was not found")));
    }

    @Test
    void getVehicleWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(authed(get("/vehicles/{id}", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid value")));
    }

    // ---------- Duplicate vehicle number ----------

    @Test
    void duplicateVehicleNumberReturns409WithMessage() throws Exception {
        createVehicle("DHA-GA-11-2004");

        mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content(validBody("DHA-GA-11-2004")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already exists")))
                .andExpect(jsonPath("$.fieldErrors.vehicleNumber").value("Vehicle number already exists."));
    }

    @Test
    void duplicateDetectionIgnoresCase() throws Exception {
        createVehicle("dha-ga-11-2005");

        mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content(validBody("dha-ga-11-2005")))
                .andExpect(status().isConflict());
    }

    @Test
    void updateToAnotherVehiclesNumberReturns409() throws Exception {
        createVehicle("DHA-GA-11-2006");
        long secondId = createVehicle("DHA-GA-11-2007");

        mockMvc.perform(authed(put("/vehicles/{id}", secondId))
                        .contentType(APPLICATION_JSON)
                        .content(validBody("DHA-GA-11-2006")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    // ---------- Validation ----------

    @Test
    void invalidVehicleReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content(body("DHA-GA-11-2008", "", null, "", "", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Validation failed")))
                .andExpect(jsonPath("$.fieldErrors.company").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.type").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.brand").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.model").isNotEmpty());
    }

    @Test
    void invalidVehicleNumberPatternReturns400() throws Exception {
        mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content(body("DHA GA 11 2009", "Stark Group", "PICKUP", "Toyota", "Hilux", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleNumber").isNotEmpty());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(authed(post("/vehicles"))
                        .contentType(APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Malformed JSON")));
    }

    // ---------- Update / delete ----------

    @Test
    void updateVehiclePersistsChanges() throws Exception {
        long id = createVehicle("DHA-GA-11-2010");

        mockMvc.perform(authed(put("/vehicles/{id}", id))
                        .contentType(APPLICATION_JSON)
                        .content(body("DHA-GA-11-2010", "Stark Logistics", "SUV", "Toyota",
                                "Land Cruiser", "MAINTENANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Stark Logistics"))
                .andExpect(jsonPath("$.type").value("SUV"))
                .andExpect(jsonPath("$.model").value("Land Cruiser"))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void deleteVehicleReturns204AndRemovesRecord() throws Exception {
        long id = createVehicle("DHA-GA-11-2011");

        mockMvc.perform(authed(delete("/vehicles/{id}", id)))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/vehicles/{id}", id)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMissingVehicleReturns404() throws Exception {
        mockMvc.perform(authed(delete("/vehicles/{id}", 999999)))
                .andExpect(status().isNotFound());
    }
}
