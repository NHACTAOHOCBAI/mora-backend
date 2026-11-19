package com.mora.backend.controller;

import com.mora.backend.service.AiSettingsService;
import com.mora.backend.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Private control-plane API consumed only by the AI service. */
@RestController @RequestMapping("/api/internal/ai-settings") @RequiredArgsConstructor
public class InternalAiSettingsController {
    private final AiSettingsService settings;
    @Value("${ai-settings.internal-key}") private String internalKey;

    @GetMapping("/runtime")
    public ResponseEntity<AiSettingsService.RuntimeConfig> runtime(@RequestHeader(value = "X-AI-Service-Key", required = false) String supplied) {
        if (internalKey == null || internalKey.isBlank() || supplied == null || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(settings.runtime());
        } catch (AppException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
