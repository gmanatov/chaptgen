package com.chaptgen.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.util.Date;

public class JwtUtil {
    private final Algorithm alg;
    private final long ttlMillis;

    public JwtUtil(String secret, int days) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret missing. Set 'auth.jwtSecret' or env JWT_SECRET.");
        }
        this.alg = Algorithm.HMAC256(secret);
        this.ttlMillis = days * 24L * 60L * 60L * 1000L;
    }

    public String createToken(long userId, String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlMillis);
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("email", email)
                .withIssuedAt(now)
                .withExpiresAt(exp)
                .sign(alg);
    }

    public long verifyAndGetUserId(String token) {
        var verifier = JWT.require(alg).build();
        var decoded = verifier.verify(token);
        return Long.parseLong(decoded.getSubject());
    }
}