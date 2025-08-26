package com.chaptgen.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transcripts")
public class TranscriptController {

    // DTO for create request
    public record CreateReq(Long userId, String url) {}

    private final JdbcTemplate jdbc;

    public TranscriptController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Create a new transcript record (for now just stores the URL)
    @PostMapping
    public Map<String, Object> create(@RequestBody CreateReq body) {
        if (body.userId() == null || body.url() == null || body.url().isBlank()) {
            throw new IllegalArgumentException("userId and url are required");
        }

        Long id = jdbc.queryForObject(
                "INSERT INTO generations (user_id, url) VALUES (?, ?) RETURNING id",
                Long.class, body.userId(), body.url()
        );

        return Map.of("id", id, "status", "queued");
    }

    // Fetch one transcript record by ID
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return jdbc.queryForMap("SELECT * FROM generations WHERE id=?", id);
    }

    // List all transcript records for a user
    @GetMapping("/user/{userId}")
    public List<Map<String, Object>> listByUser(@PathVariable long userId) {
        return jdbc.queryForList(
                "SELECT id, url, status, created_at, updated_at " +
                        "FROM generations " +
                        "WHERE user_id=? " +
                        "ORDER BY created_at DESC",
                userId
        );
    }

    // Delete a transcript record by ID
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        int rows = jdbc.update("DELETE FROM generations WHERE id=?", id);
        return Map.of("deleted", rows > 0, "id", id);
    }
}