package com.example.helpdesk.security;


import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final JwtParser parser;
    private final Long ttlMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes:60}") Long ttlMinutes
    ){
        if(secret == null || secret.length() < 32){
            throw new IllegalStateException("JWT_SECRET must be kept and at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.ttlMinutes = ttlMinutes;
    }

    public String generateToken(User user){
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AuthUser parse(String token){
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                Role.valueOf(claims.get("role", String.class))
        );
    }

    public long getExpiresInSeconds(){
        return ttlMinutes * 60;
    }
}
