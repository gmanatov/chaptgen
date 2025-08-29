package com.chaptgen.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.chaptgen.service.TranscriptApiService;
import com.chaptgen.ai.ChapterGenService;
import com.chaptgen.security.JwtUtil;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.stream.Collectors;

// This is the core REST controller for transcripts/chapters:
// - /transcripts/preview: fetch transcript -> call Gemini -> return chapters (NOT saved)
// - POST /transcripts: save a finished generation to DB (requires auth cookie)
// - GET /transcripts/mine: list my generations
// - GET/PUT/DELETE /transcripts/{id}: read/update/delete (ownership enforced via cookie)
@RestController
@RequestMapping("/transcripts")
public class TranscriptController {

    // Request bodies I parse
    public record PreviewReq(String url) {}
    public record SaveReq(String url, Object chaptersJson) {} // userId comes from JWT cookie now
    public record FetchReq(String url) {}
    public record UpdateReq(Object chaptersJson, String chaptersText, String title) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TranscriptApiService transcriptApi;
    private final ChapterGenService chapterGen;
    private final JwtUtil jwt;

    public TranscriptController(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${youtubeTranscript.apiKey:${YTT_API_KEY:}}") String ytApiKey,
            @Value("${gemini.apiKey:${GEMINI_API_KEY:}}") String geminiKey,
            @Value("${auth.jwtSecret:${JWT_SECRET:}}") String jwtSecret,
            @Value("${auth.jwtDays:14}") int jwtDays
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transcriptApi = new TranscriptApiService(ytApiKey);
        this.chapterGen = new ChapterGenService(geminiKey);
        this.jwt = new JwtUtil(jwtSecret, jwtDays);
    }

    // Ensuring user is logged in and return userId ---
    private long requireUser(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        try {
            return jwt.verifyAndGetUserId(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }

    private boolean existsForUser(long id, long userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generations WHERE id=? AND user_id=?",
                Integer.class, id, userId
        );
        return n != null && n > 0;
    }

    // Returning raw provider JSON to inspect title/tracks
    @PostMapping("/fetch/raw")
    public Map<String, Object> fetchRaw(@RequestBody FetchReq body,
                                        @CookieValue(name = "chaptgen_token", required = false) String token) throws Exception {
        long userId = requireUser(token);
        if (body.url() == null || body.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        String videoId = extractVideoId(body.url());
        String raw = transcriptApi.fetchRaw(videoId);
        return Map.of("ok", true, "userId", userId, "videoId", videoId, "raw", raw);
    }

    // Fetching a normalized transcript (no DB write)
    @PostMapping("/fetch")
    public Map<String, Object> fetchOnly(@RequestBody FetchReq body,
                                         @CookieValue(name = "chaptgen_token", required = false) String token) throws Exception {
        requireUser(token);
        if (body.url() == null || body.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        String videoId = extractVideoId(body.url());
        List<Map<String, Object>> segments = transcriptApi.fetchTranscript(videoId);

        return Map.of(
                "ok", true,
                "url", body.url(),
                "hasTranscript", !segments.isEmpty(),
                "segments", segments
        );
    }

    // Preview: transcript -> Gemini -> chapters (NOT saved)
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody PreviewReq body,
                                       @CookieValue(name = "chaptgen_token", required = false) String token) throws Exception {
        requireUser(token);
        if (body.url() == null || body.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        String videoId = extractVideoId(body.url());
        String title = safeExtractTitle(videoId);

        List<Map<String, Object>> segments = transcriptApi.fetchTranscript(videoId);
        if (segments.isEmpty()) {
            return Map.of(
                    "ok", true,
                    "url", body.url(),
                    "title", title,
                    "chapters", List.of(),
                    "chapters_text", "",
                    "note", "No transcript available for this video."
            );
        }

        String flat = ChapterGenService.toFlatTextFromSegments(segments);
        List<Map<String, Object>> chapters = chapterGen.generateChaptersFromFlatText(flat);
        String chaptersText = toChaptersText(chapters);

        return Map.of(
                "ok", true,
                "url", body.url(),
                "title", title,
                "chapters", chapters,
                "chapters_text", chaptersText,
                "note", chapters.isEmpty()
                        ? "Model returned no chapters (try again, or use a longer video)."
                        : "Generated from transcript."
        );
    }

    // Save final generation for the logged-in user
    @PostMapping
    public Map<String, Object> save(@RequestBody SaveReq body,
                                    @CookieValue(name = "chaptgen_token", required = false) String token) throws Exception {
        long userId = requireUser(token);
        if (body.url() == null || body.url().isBlank()) throw new IllegalArgumentException("url is required");
        if (body.chaptersJson() == null) throw new IllegalArgumentException("chaptersJson is required");

        String jsonString = objectMapper.writeValueAsString(body.chaptersJson());
        String videoId = extractVideoId(body.url());
        String title = safeExtractTitle(videoId);

        try {
            Long id = jdbc.queryForObject(
                    "INSERT INTO generations (user_id, url, title, status, chapters_json) " +
                            "VALUES (?, ?, ?, 'completed', ?::jsonb) RETURNING id",
                    Long.class,
                    userId, body.url(), title, jsonString
            );

            return Map.of("ok", true, "id", id, "status", "completed");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Map.of("ok", false, "error", "Generation for this video already exists.");
        }
    }

    // Updating chapters/title (ownership enforced)
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id,
                                      @RequestBody UpdateReq body,
                                      @CookieValue(name = "chaptgen_token", required = false) String token) throws Exception {
        long userId = requireUser(token);

        if ((body.chaptersJson() == null || (body.chaptersJson() instanceof String s && s.isBlank()))
                && (body.chaptersText() == null || body.chaptersText().isBlank())
                && (body.title() == null)) {
            throw new IllegalArgumentException("Provide chaptersJson or chaptersText or title to update.");
        }

        if (!existsForUser(id, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }

        String chaptersJsonString = null;
        if (body.chaptersJson() != null) {
            chaptersJsonString = objectMapper.writeValueAsString(body.chaptersJson());
        } else if (body.chaptersText() != null && !body.chaptersText().isBlank()) {
            // Convert text lines to array objects
            List<Map<String, Object>> arr = new ArrayList<>();
            String[] lines = body.chaptersText().split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                int space = trimmed.indexOf(' ');
                if (space <= 0) {
                    arr.add(Map.of("start", "", "title", trimmed));
                } else {
                    String start = trimmed.substring(0, space).trim();
                    String title = trimmed.substring(space + 1).trim();
                    if (title.isEmpty()) title = start;
                    arr.add(Map.of("start", start, "title", title));
                }
            }
            chaptersJsonString = objectMapper.writeValueAsString(arr);
        }

        // Dynamic update based on what fields are present
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE generations SET updated_at=now()");
        if (chaptersJsonString != null) {
            sql.append(", chapters_json=?::jsonb");
            params.add(chaptersJsonString);
        }
        if (body.title() != null) {
            sql.append(", title=?");
            params.add(body.title());
        }
        sql.append(" WHERE id=? AND user_id=?");
        params.add(id);
        params.add(userId);

        int rows = jdbc.update(sql.toString(), params.toArray());
        return Map.of("ok", rows > 0, "updated", rows > 0);
    }

    // Read one (ownership enforced)
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id,
                                   @CookieValue(name = "chaptgen_token", required = false) String token) throws Exception {
        long userId = requireUser(token);

        var rows = jdbc.queryForList(
                "SELECT id, url, title, status, created_at, updated_at, " +
                        "       chapters_json::text AS chapters_json, transcript, model, error " +
                        "FROM generations WHERE id = ? AND user_id = ?",
                id, userId
        );
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");

        Map<String, Object> row = rows.get(0);

        // Converting chapters_json text column back into native structure + build a text block
        Object chapters = null;
        String chaptersText = "";
        Object raw = row.get("chapters_json");
        if (raw instanceof String s && !s.isBlank()) {
            chapters = objectMapper.readValue(s, Object.class);
            try {
                JsonNode node = objectMapper.readTree(s);
                if (node.isArray()) {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (JsonNode it : node) {
                        String start = it.has("start") ? it.get("start").asText("") : "";
                        String title = it.has("title") ? it.get("title").asText("") : "";
                        if (!start.isBlank() && !title.isBlank()) {
                            list.add(Map.of("start", start, "title", title));
                        }
                    }
                    chaptersText = toChaptersText(list);
                }
            } catch (Exception ignore) {}
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", row.get("id"));
        resp.put("url", row.get("url"));
        resp.put("title", row.get("title"));
        resp.put("status", row.get("status"));
        resp.put("created_at", row.get("created_at"));
        resp.put("updated_at", row.get("updated_at"));
        resp.put("chapters_json", chapters);
        resp.put("chapters_text", chaptersText);
        resp.put("transcript", row.get("transcript"));
        resp.put("model", row.get("model"));
        resp.put("error", row.get("error"));
        return resp;
    }

    // List my generations (using cookie to find user)
    @GetMapping("/mine")
    public List<Map<String, Object>> listMine(
            @CookieValue(name = "chaptgen_token", required = false) String token) {
        long userId = requireUser(token);
        return jdbc.queryForList(
                "SELECT id, url, title, status, created_at, updated_at " +
                        "FROM generations WHERE user_id=? ORDER BY created_at DESC",
                userId
        );
    }

    // Delete (ownership enforced)
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id,
                                      @CookieValue(name = "chaptgen_token", required = false) String token) {
        long userId = requireUser(token);
        int rows = jdbc.update("DELETE FROM generations WHERE id=? AND user_id=?", id, userId);
        return Map.of("deleted", rows > 0, "id", id);
    }

    // Helpers
    private String extractVideoId(String input) {
        if (input.contains("youtu.be/")) {
            return input.substring(input.indexOf("youtu.be/") + 9).split("[?&#/]")[0];
        }
        if (input.contains("youtube.com") && input.contains("v=")) {
            String after = input.substring(input.indexOf("v=") + 2);
            return after.split("[?&#/]")[0];
        }
        return input.trim();
    }

    private static String toChaptersText(List<Map<String, Object>> chapters) {
        if (chapters == null || chapters.isEmpty()) return "";
        return chapters.stream()
                .map(c -> (c.getOrDefault("start","") + " " + c.getOrDefault("title","")).trim())
                .collect(Collectors.joining("\n"));
    }

    // Pulling the YouTube title from the providers raw JSON
    private String safeExtractTitle(String videoId) {
        try {
            String raw = transcriptApi.fetchRaw(videoId);
            if (raw != null && !raw.isBlank()) {
                JsonNode arr = objectMapper.readTree(raw);
                JsonNode root = arr.isArray() && arr.size() > 0 ? arr.get(0) : objectMapper.readTree(raw);
                JsonNode mfTitle = root
                        .path("microformat")
                        .path("playerMicroformatRenderer")
                        .path("title")
                        .path("simpleText");
                if (mfTitle.isTextual() && !mfTitle.asText("").isBlank()) {
                    return mfTitle.asText();
                }
                JsonNode direct = root.path("title");
                if (direct.isTextual() && !direct.asText("").isBlank()) {
                    return direct.asText();
                }
            }
        } catch (Exception ignore) {}
        return "";
    }
}