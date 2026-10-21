package com.example.helpdesk.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registerReturnsATokenAndNeverThePasswordHash() throws Exception {
        String email = uniqueEmail("asha");

        performRegister("Asha", email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void registeringTheSameEmailTwiceIsAConflict() throws Exception {
        String email = uniqueEmail("dup");
        performRegister("First", email, PASSWORD).andExpect(status().isCreated());
        performRegister("Second", email, PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void emailsAreCaseInsensitive() throws Exception {
        String email = uniqueEmail("case");
        performRegister("Lower", email, PASSWORD).andExpect(status().isCreated());
        performRegister("Upper", email.toUpperCase(), PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void aWeakPasswordIsRejectedWithAFieldError() throws Exception {
        performRegister("Weak", uniqueEmail("weak"), "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        Account account = register("login");

        String wrongPassword = performLogin(account.email(), "not-the-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = performLogin(uniqueEmail("ghost"), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(wrongPassword).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknownEmail).get("message").asText());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/api/tickets").header(AUTH, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        Account account = register("me");

        mockMvc.perform(get("/api/auth/me").header(AUTH, bearer(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.email()));
    }
}