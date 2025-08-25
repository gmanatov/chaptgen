package com.chaptgen.web;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
public class TranscriptController {
    public record UrlReq(String url) {}

    @PostMapping("/transcripts")
    public Map<String, Object> enqueue(@RequestBody UrlReq body) {
        System.out.println("Got URL: " + body.url());
        return Map.of("ok", true, "receivedUrl", body.url(), "jobId", UUID.randomUUID().toString());
    }
}