package com.starkgroup.vms.journey;

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
import com.starkgroup.vms.vehicle.VehicleRepository;
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

/**
 * End-to-end journey at the API level: sign in, register a vehicle, find it in the
 * paginated list, edit it, and delete it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullJourneyIT {

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

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "admin123")))
                )
                .andExpect(status().isOk())
                .andReturn();
        token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void loginRegisterListEditDeleteJourney() throws Exception {
        // 1. Register a vehicle.
        MvcResult created = mockMvc.perform(post("/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleNumber", "DHA-GA-11-4001",
                                "company", "Stark Construction",
                                "type", "PICKUP",
                                "brand", "Toyota",
                                "model", "Hilux",
                                "status", "ACTIVE"
                        )))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleNumber").value("DHA-GA-11-4001"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // 2. It appears in the paginated list.
        mockMvc.perform(get("/vehicles").header("Authorization", "Bearer " + token).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].vehicleNumber").value("DHA-GA-11-4001"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // 3. Duplicate registration is rejected by the business rule.
        mockMvc.perform(post("/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleNumber", "DHA-GA-11-4001",
                                "company", "Stark Construction",
                                "type", "PICKUP",
                                "brand", "Toyota",
                                "model", "Hilux"
                        )))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));

        // 4. Edit the vehicle and confirm the change.
        mockMvc.perform(put("/vehicles/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleNumber", "DHA-GA-11-4001",
                                "company", "Stark Logistics",
                                "type", "SUV",
                                "brand", "Toyota",
                                "model", "Land Cruiser",
                                "status", "MAINTENANCE"
                        )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Land Cruiser"))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        mockMvc.perform(get("/vehicles/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Land Cruiser"));

        // 5. Delete the vehicle; it disappears from the register.
        mockMvc.perform(delete("/vehicles/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/vehicles/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/vehicles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }
}
