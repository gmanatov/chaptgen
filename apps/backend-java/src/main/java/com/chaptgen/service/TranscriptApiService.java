package com.chaptgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class TranscriptApiService {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    public TranscriptApiService(String apiKey) {
        this.apiKey = Objects.requireNonNullElse(apiKey, "").trim();
    }

    private void ensureKey() {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "youtube-transcript.io API key is missing. " +
                            "Set application property 'youtubeTranscript.apiKey' or env 'YTT_API_KEY'."
            );
        }
    }

    private HttpRequest buildRequest(String bodyJson) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://www.youtube-transcript.io/api/transcripts"))
                .header("Authorization", "Basic " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "chaptgen/1.0 (+local-dev)")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
    }

    public String fetchRaw(String videoId) throws Exception {
        ensureKey();
        Map<String, Object> payload = Map.of(
                "ids", List.of(videoId),
                "platform", "youtube"
        );
        String bodyJson = mapper.writeValueAsString(payload);

        HttpRequest req = buildRequest(bodyJson);
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.body() == null ? "" : res.body();
    }

    public List<Map<String, Object>> fetchTranscript(String videoId) throws Exception {
        ensureKey();
        Map<String, Object> payload = Map.of(
                "ids", List.of(videoId),
                "platform", "youtube"
        );
        String bodyJson = mapper.writeValueAsString(payload);

        HttpRequest req = buildRequest(bodyJson);
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("[TranscriptApiService] HTTP error: " + e.getMessage());
            throw e;
        }

        if (res.statusCode() != 200) {
            System.err.println("[TranscriptApiService] Non-200: " + res.statusCode() + " body=" + res.body());
            return List.of();
        }

        String body = res.body();
        if (body == null || body.isBlank()) return List.of();

        try {
            JsonNode root = mapper.readTree(body);

            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);

                if (first.has("tracks") && first.get("tracks").isArray()) {
                    JsonNode tracks = first.get("tracks");
                    JsonNode chosen = chooseTrack(tracks, "en");
                    if (chosen != null) {
                        List<Map<String, Object>> segs = extractTranscriptArray(chosen.get("transcript"));
                        if (!segs.isEmpty()) return segs;
                    }
                }

                if (first.has("transcript")) {
                    List<Map<String, Object>> segs = extractTranscriptArray(first.get("transcript"));
                    if (!segs.isEmpty()) return segs;
                }

                if (first.has("segments")) {
                    List<Map<String, Object>> segs = extractGenericSegments(first.get("segments"));
                    if (!segs.isEmpty()) return segs;
                }
            }

            if (root.isObject()) {
                if (root.has("transcript")) {
                    List<Map<String, Object>> segs = extractTranscriptArray(root.get("transcript"));
                    if (!segs.isEmpty()) return segs;
                }
                if (root.has("segments")) {
                    List<Map<String, Object>> segs = extractGenericSegments(root.get("segments"));
                    if (!segs.isEmpty()) return segs;
                }
                if (root.has("data") && root.get("data").has("transcript")) {
                    List<Map<String, Object>> segs = extractTranscriptArray(root.get("data").get("transcript"));
                    if (!segs.isEmpty()) return segs;
                }
            }

            return List.of();

        } catch (Exception parseErr) {
            System.err.println("[TranscriptApiService] Parse error: " + parseErr.getMessage());
            return List.of();
        }
    }

    private JsonNode chooseTrack(JsonNode tracks, String preferredLangBase) {
        for (JsonNode t : tracks) {
            if (t.has("language") && t.get("language").asText().toLowerCase(Locale.ROOT).contains("english")) {
                if (t.has("transcript") && t.get("transcript").isArray() && t.get("transcript").size() > 0) {
                    return t;
                }
            }
        }
        for (JsonNode t : tracks) {
            if (t.has("transcript") && t.get("transcript").isArray() && t.get("transcript").size() > 0) {
                return t;
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractTranscriptArray(JsonNode transcriptArr) {
        if (transcriptArr == null || !transcriptArr.isArray()) return List.of();

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode seg : transcriptArr) {
            String text = seg.has("text") ? seg.get("text").asText() : "";
            if (text == null) text = "";
            text = text.replace("\n", " ").trim();
            if (text.isEmpty()) continue;

            double startSec = 0.0;
            if (seg.has("start")) {
                try { startSec = Double.parseDouble(seg.get("start").asText()); } catch (Exception ignored) {}
            } else if (seg.has("offset")) {

                startSec = seg.get("offset").asDouble() / 1000.0;
            }

            out.add(Map.of(
                    "startSec", startSec,
                    "start", hhmmss(startSec),
                    "text", text
            ));
        }
        return out;
    }

    private List<Map<String, Object>> extractGenericSegments(JsonNode segsArr) {
        if (segsArr == null || !segsArr.isArray()) return List.of();

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode seg : segsArr) {
            String text = seg.has("text") ? seg.get("text").asText() : "";
            if (text == null) text = "";
            text = text.replace("\n", " ").trim();
            if (text.isEmpty()) continue;

            double startSec = 0.0;
            if (seg.has("start")) {
                try { startSec = seg.get("start").asDouble(); } catch (Exception ignored) {}
            } else if (seg.has("offset")) {
                startSec = seg.get("offset").asDouble() / 1000.0;
            }

            out.add(Map.of(
                    "startSec", startSec,
                    "start", hhmmss(startSec),
                    "text", text
            ));
        }
        return out;
    }

    private static String hhmmss(double seconds) {
        int s = (int) Math.floor(seconds);
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return (h > 0) ? String.format("%02d:%02d:%02d", h, m, sec)
                : String.format("%02d:%02d", m, sec);
    }
}