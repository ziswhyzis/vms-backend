package com.starkgroup.vms.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starkgroup.vms.user.User;
import com.starkgroup.vms.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Self-service account creation: a fresh deployment with an empty users table
 * must be able to bootstrap itself through the public register endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private String body(String username, String fullName, String password) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("fullName", fullName);
        payload.put("password", password);
        return objectMapper.writeValueAsString(payload);
    }

    private MvcResult register(String username, String fullName, String password) throws Exception {
        return mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body(username, fullName, password)))
                .andReturn();
    }

    @Test
    void registerCreatesAnOperatorAndSignsThemIn() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body("jane.doe", "Jane Doe", "secret-passphrase")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("jane.doe"))
                .andExpect(jsonPath("$.user.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void registeredOperatorCanUseProtectedEndpoints() throws Exception {
        MvcResult created = register("fleet.lead", "Fleet Lead", "secret-passphrase");
        String token = objectMapper.readTree(created.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fleet.lead"));

        mockMvc.perform(get("/vehicles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void registeredOperatorCanSignInLater() throws Exception {
        register("shift.supervisor", "Shift Supervisor", "secret-passphrase");

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "shift.supervisor", "password", "secret-passphrase"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.fullName").value("Shift Supervisor"));
    }

    @Test
    void passwordIsStoredAsABcryptHash() throws Exception {
        register("hash.check", "Hash Check", "secret-passphrase");

        User stored = userRepository.findByUsername("hash.check").orElseThrow();
        Assertions.assertThat(stored.getPasswordHash())
                .isNotEqualTo("secret-passphrase")
                .startsWith("$2");
    }

    @Test
    void duplicateUsernameReturns409WithFieldError() throws Exception {
        register("jane.doe", "Jane Doe", "secret-passphrase");

        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body("jane.doe", "Jane Again", "another-passphrase")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already taken")))
                .andExpect(jsonPath("$.fieldErrors.username").value("Username already exists."));
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitively() throws Exception {
        register("jane.doe", "Jane Doe", "secret-passphrase");

        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body("Jane.Doe", "Jane Again", "another-passphrase")))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body("shorty", "Short Password", "abc123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")))
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    void invalidUsernameReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body("bad name!", "Bad Name", "secret-passphrase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").isNotEmpty());
    }

    @Test
    void blankFullNameReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body("no.name", "", "secret-passphrase")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty());
    }

    @Test
    void registerNeedsNoToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(body("open.signup", "Open Signup", "secret-passphrase")))
                .andExpect(status().isCreated());
    }
}
