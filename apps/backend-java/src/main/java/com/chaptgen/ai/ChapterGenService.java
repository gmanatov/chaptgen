package com.chaptgen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class ChapterGenService {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public ChapterGenService(String apiKey) {
        this(apiKey, "gemini-1.5-flash");
    }

    public ChapterGenService(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key missing. Set 'gemini.apiKey' or env 'GEMINI_API_KEY'.");
        }
        this.apiKey = apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "gemini-1.5-flash" : model.trim();
    }


    public List<Map<String, Object>> generateChaptersFromFlatText(String transcriptLines) throws Exception {
        if (transcriptLines == null) transcriptLines = "";

        String system = """
            You create YouTube-style chapter markers from transcripts.
            Output STRICT JSON array only. No prose, no markdown.
            Each item: {"start":"MM:SS or HH:MM:SS","title":"Short, descriptive"}.
            Use the earliest sensible start time for each chapter.
            5–8 chapters for 10–30 min; 3–5 if very short.
            Avoid duplicates and noise.
            """;

        String user = """
            TRANSCRIPT (each line may start with [mm:ss] or [hh:mm:ss]):

            %s

            Return ONLY the JSON array, nothing else.
            """.formatted(transcriptLines);


        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", system))),
                        Map.of("role", "user", "parts", List.of(Map.of("text", user)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "response_mime_type", "application/json"
                )
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + res.statusCode() + " " + res.body());
        }

        JsonNode root = mapper.readTree(res.body());
        String json = "";
        try {
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    json = parts.get(0).path("text").asText("");
                }
            }
        } catch (Exception ignore) {}

        if (json == null || json.isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> chapters = new ArrayList<>();
        try {
            JsonNode arr = mapper.readTree(json);
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    String start = item.has("start") ? item.get("start").asText() : "";
                    String title = item.has("title") ? item.get("title").asText() : "";
                    if (!start.isBlank() && !title.isBlank()) {
                        chapters.add(Map.of("start", start, "title", title));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ChapterGenService] Could not parse model JSON: " + e.getMessage());
            return List.of();
        }
        return chapters;
    }

    /** Utility to turn {startSec, text} segments into "[mm:ss] text" lines. */
    public static String toFlatTextFromSegments(List<Map<String, Object>> segments) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> seg : segments) {
            double sec = 0.0;
            Object v = seg.get("startSec");
            if (v instanceof Number n) sec = n.doubleValue();
            else if (v != null) {
                try { sec = Double.parseDouble(v.toString()); } catch (Exception ignore) {}
            }
            sb.append("[").append(hhmmss(sec)).append("] ").append(String.valueOf(seg.getOrDefault("text",""))).append("\n");
        }
        return sb.toString();
    }

    private static String hhmmss(double seconds) {
        int s = (int) Math.floor(seconds);
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return (h > 0) ? String.format("%02d:%02d:%02d", h, m, sec)
                : String.format("%02d:%02d", m, sec);
    }
}