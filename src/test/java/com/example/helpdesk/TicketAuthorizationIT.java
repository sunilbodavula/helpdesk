package com.example.helpdesk;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class TicketAuthorizationIT extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void creatingATicketWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.example.helpdesk.dto.TicketCreateRequest("Title", null, null,
                                        null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenCreateTicket() throws Exception {
        String registerBody = """
            {"name":"Tester","email":"tester@test.com","password":"password123"}
        """;
        String response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Printer jam", "priority":"HIGH"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByName").value("Tester"));

    }
}
