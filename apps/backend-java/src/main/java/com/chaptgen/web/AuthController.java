package com.chaptgen.web;

import com.chaptgen.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public record SignupReq(String email, String password) {}
    public record LoginReq(String email, String password) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    @Value("${auth.cookieName:chaptgen_token}")
    private String cookieName;

    @Value("${auth.cookieDomain:localhost}")
    private String cookieDomain;

    @Value("${auth.jwtDays:14}")
    private int jwtDays;

    public AuthController(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            @Value("${auth.jwtSecret:${JWT_SECRET:}}") String jwtSecret,
            @Value("${auth.jwtDays:14}") int jwtDays
    ) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = new JwtUtil(jwtSecret, jwtDays);
        this.jwtDays = jwtDays;
    }

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody SignupReq req, HttpServletResponse res) {
        if (req.email() == null || req.email().isBlank() ||
                req.password() == null || req.password().length() < 6) {
            return Map.of("ok", false, "error", "Invalid email or password too short (min 6).");
        }
        String email = req.email().trim().toLowerCase();
        String hash = encoder.encode(req.password());
        try {
            Long id = jdbc.queryForObject(
                    "INSERT INTO users (email, password_hash) VALUES (?, ?) RETURNING id",
                    Long.class, email, hash
            );
            setAuthCookie(res, jwt.createToken(id, email));
            return Map.of("ok", true, "id", id, "email", email);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Map.of("ok", false, "error", "Email already registered.");
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req, HttpServletResponse res) {
        if (req.email() == null || req.password() == null) {
            return Map.of("ok", false, "error", "Email and password required.");
        }
        String email = req.email().trim().toLowerCase();
        try {
            var row = jdbc.queryForMap("SELECT id, password_hash FROM users WHERE email = ?", email);
            long id = ((Number) row.get("id")).longValue();
            String hash = (String) row.get("password_hash");
            if (!encoder.matches(req.password(), hash)) {
                return Map.of("ok", false, "error", "Invalid credentials.");
            }
            setAuthCookie(res, jwt.createToken(id, email));
            return Map.of("ok", true, "id", id, "email", email);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of("ok", false, "error", "Invalid credentials.");
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .domain(cookieDomain)
                .maxAge(0)
                .sameSite("Lax")
                .build();
        res.addHeader("Set-Cookie", cookie.toString());
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@CookieValue(name = "chaptgen_token", required = false) String token) {
        if (token == null || token.isBlank()) return Map.of("ok", false);
        try {
            long userId = jwt.verifyAndGetUserId(token);
            var row = jdbc.queryForMap("SELECT id, email FROM users WHERE id = ?", userId);
            return Map.of("ok", true, "id", row.get("id"), "email", row.get("email"));
        } catch (Exception e) {
            return Map.of("ok", false);
        }
    }

    private void setAuthCookie(HttpServletResponse res, String token) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .domain(cookieDomain)
                .maxAge(Duration.ofDays(jwtDays))
                .sameSite("Lax")
                .build();
        res.addHeader("Set-Cookie", cookie.toString());
    }
}