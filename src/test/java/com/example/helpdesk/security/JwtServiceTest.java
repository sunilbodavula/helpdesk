package com.example.helpdesk.security;

import com.example.helpdesk.model.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.example.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final JwtService jwt = new JwtService(SECRET, 60L);

    @Test
    void aTokenCarriesTheUserIdAndRole() {
        AuthUser parsed = jwt.parse(jwt.generateToken(user(7L, Role.AGENT)));

        assertThat(parsed.id()).isEqualTo(7L);
        assertThat(parsed.role()).isEqualTo(Role.AGENT);
    }

    @Test
    void aTokenNeverContainsThePasswordHashOrTheEmail() {
        String token = jwt.generateToken(user(7L, Role.USER));

        assertThat(payloadOf(token))
                .contains("\"sub\":\"7\"")
                .doesNotContain("stored-hash")
                .doesNotContain("@");
    }

    @Test
    void aShortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", 60L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtService(null, 60L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anExpiredTokenIsRejected() {
        String expired = new JwtService(SECRET, -1L).generateToken(user(7L, Role.USER));

        assertThatThrownBy(() -> jwt.parse(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService("another-secret-another-secret-another", 60L);

        assertThatThrownBy(() -> jwt.parse(other.generateToken(user(7L, Role.USER))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void changingTheRoleInsideATokenBreaksTheSignature() {
        String token = jwt.generateToken(user(7L, Role.USER));
        String[] parts = token.split("\\.");
        String forgedPayload = payloadOf(token).replace("\"USER\"", "\"ADMIN\"");
        assertThat(forgedPayload).isNotEqualTo(payloadOf(token));

        String forgedToken = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThatThrownBy(() -> jwt.parse(forgedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> jwt.parse("not-a-token")).isInstanceOf(JwtException.class);
    }

    @Test
    void expiryIsReportedInSeconds() {
        assertThat(jwt.getExpiresInSeconds()).isEqualTo(3600L);
    }

    private static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }
}