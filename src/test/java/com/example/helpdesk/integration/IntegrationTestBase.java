package com.example.helpdesk.integration;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "password123";
    public static final String AUTH = HttpHeaders.AUTHORIZATION;

    public record Account(Long id, String email, String token) {}

    // One database container shared by every test class. Testcontainers removes it when the JVM ends.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        // The Postgres driver sends the JVM time zone when it connects. Some machines report the old name
        // "Asia/Calcutta", which the container rejects, so the tests pin the zone to UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected JdbcTemplate jdbc;

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected static String bearer(Account account) {
        return "Bearer " + account.token();
    }

    protected String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@test.com";
    }

    protected ResultActions performRegister(String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "email", email, "password", password))));
    }

    protected ResultActions performLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    protected Account register(String label) throws Exception {
        String email = uniqueEmail(label);
        String body = performRegister(label, email, PASSWORD)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return new Account(node.at("/user/id").asLong(), email, node.get("accessToken").asText());
    }

    protected Account login(String email) throws Exception {
        String body = performLogin(email, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return new Account(node.at("/user/id").asLong(), email, node.get("accessToken").asText());
    }

    // The role is stored inside the token, so log in again after changing it.
    protected Account registerWithRole(String label, Role role) throws Exception {
        Account account = register(label);
        User user = userRepository.findById(account.id()).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return login(account.email());
    }

    protected long createTicket(Account owner, String title) throws Exception {
        String body = mockMvc.perform(post("/api/tickets")
                        .header(AUTH, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", title, "priority", "HIGH"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    // PUT carries the whole ticket, so every call sends title, status, priority and assignee.
    protected ResultActions updateTicket(Account actor, long ticketId, String title, String status,
                                         Long assignedToId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("status", status);
        body.put("priority", "HIGH");
        body.put("assignedToId", assignedToId);
        return mockMvc.perform(put("/api/tickets/" + ticketId)
                .header(AUTH, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    protected List<Long> visibleTicketIds(Account actor) throws Exception {
        String body = mockMvc.perform(get("/api/tickets").param("size", "100").header(AUTH, bearer(actor)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(body).get("data").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    protected ResultActions patchRole(Account actor, long targetUserId, String role) throws Exception {
        return mockMvc.perform(patch("/api/users/" + targetUserId + "/role")
                .header(AUTH, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\"}"));
    }
}