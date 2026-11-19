package com.mora.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.AiRuntimeConfig;
import com.mora.backend.repository.AiRuntimeConfigRepository;
import com.mora.backend.service.AiSettingsService;
import com.mora.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service @RequiredArgsConstructor
public class AiSettingsServiceImpl implements AiSettingsService {
    private static final Long SINGLETON_ID = 1L;
    private final AiRuntimeConfigRepository repository;
    private final UserService users;
    // This project uses Jackson directly in several services; do not require a
    // framework-provided mapper bean, which is not available in this setup.
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    @Value("${ai-settings.encryption-key}") private String encryptionKey;

    @Override @Transactional(readOnly = true)
    public SettingsView get() {
        return repository.findById(SINGLETON_ID).map(this::view).orElse(defaultView());
    }

    @Override @Transactional
    public SettingsView update(UpdateRequest request) {
        validate(request);
        AiRuntimeConfig config = repository.findById(SINGLETON_ID).orElseGet(() -> AiRuntimeConfig.builder().id(SINGLETON_ID).build());
        String submittedKey = request.apiKey() == null ? "" : request.apiKey().trim();
        if (!submittedKey.isBlank()) {
            config.setEncryptedApiKey(encrypt(submittedKey));
            config.setKeyFingerprint(fingerprint(submittedKey));
        } else if (config.getEncryptedApiKey() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        }
        String submittedBenchmarkKey = request.benchmarkApiKey() == null ? "" : request.benchmarkApiKey().trim();
        if (!submittedBenchmarkKey.isBlank()) {
            config.setEncryptedBenchmarkApiKey(encrypt(submittedBenchmarkKey));
            config.setBenchmarkKeyFingerprint(fingerprint(submittedBenchmarkKey));
        } else if (config.getEncryptedBenchmarkApiKey() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        }
        config.setChatModel(request.chatModel().trim());
        config.setParserModel(request.parserModel().trim());
        config.setEmbeddingModel(request.embeddingModel().trim());
        config.setBenchmarkJudgeModel(request.benchmarkJudgeModel().trim());
        config.setBenchmarkJudgeEmbeddingModel(request.benchmarkJudgeEmbeddingModel().trim());
        config.setVndPerUsd(request.vndPerUsd());
        config.setPricingVersion(request.pricingVersion().trim());
        try { config.setModelPricesJson(json.writeValueAsString(request.modelPrices())); }
        catch (Exception error) { throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER); }
        config.setUpdatedById(users.getCurrentUser().getId());
        return view(repository.save(config));
    }

    @Override @Transactional(readOnly = true)
    public RuntimeConfig runtime() {
        AiRuntimeConfig config = repository.findById(SINGLETON_ID).orElseThrow(() -> new AppException(ErrorCode.AI_ENGINE_ERROR));
        if (!complete(config)) throw new AppException(ErrorCode.AI_CONFIGURATION_REQUIRED);
        return new RuntimeConfig(decrypt(config.getEncryptedApiKey()), decrypt(config.getEncryptedBenchmarkApiKey()),
                config.getChatModel(), config.getParserModel(), config.getEmbeddingModel(), config.getBenchmarkJudgeModel(),
                config.getBenchmarkJudgeEmbeddingModel(), config.getVndPerUsd(), config.getPricingVersion(), prices(config));
    }

    private void validate(UpdateRequest request) {
        if (request == null || blank(request.chatModel()) || blank(request.parserModel()) || blank(request.embeddingModel()) ||
                blank(request.benchmarkJudgeModel()) || blank(request.benchmarkJudgeEmbeddingModel()) || blank(request.pricingVersion()) ||
                request.vndPerUsd() <= 0 || request.modelPrices() == null || request.modelPrices().isEmpty())
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        request.modelPrices().forEach((model, price) -> {
            if (blank(model) || price == null || price.inputPerMillionUsd() < 0 || price.cachedInputPerMillionUsd() < 0 || price.outputPerMillionUsd() < 0)
                throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        });
        if (!request.modelPrices().containsKey(request.chatModel()) || !request.modelPrices().containsKey(request.parserModel()) ||
                !request.modelPrices().containsKey(request.embeddingModel()) || !request.modelPrices().containsKey(request.benchmarkJudgeModel()) ||
                !request.modelPrices().containsKey(request.benchmarkJudgeEmbeddingModel()))
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
    }
    private boolean complete(AiRuntimeConfig config) { return config.getEncryptedApiKey() != null && config.getEncryptedBenchmarkApiKey() != null && !blank(config.getChatModel()) && !blank(config.getParserModel()) && !blank(config.getEmbeddingModel()) && !blank(config.getBenchmarkJudgeModel()) && !blank(config.getBenchmarkJudgeEmbeddingModel()) && config.getVndPerUsd() > 0 && !blank(config.getPricingVersion()) && config.getModelPricesJson() != null; }
    private SettingsView view(AiRuntimeConfig config) { return new SettingsView(config.getEncryptedApiKey() != null, config.getKeyFingerprint(), config.getEncryptedBenchmarkApiKey() != null, config.getBenchmarkKeyFingerprint(), config.getChatModel(), config.getParserModel(), config.getEmbeddingModel(), config.getBenchmarkJudgeModel(), config.getBenchmarkJudgeEmbeddingModel(), config.getVndPerUsd(), config.getPricingVersion(), prices(config), config.getUpdatedAt()); }
    private SettingsView defaultView() { return new SettingsView(false, null, false, null, "gpt-5-mini", "gpt-5-mini", "text-embedding-3-small", "gpt-5-mini", "text-embedding-3-small", 26000, "2026-11", Map.of("gpt-5-mini", new ModelPrice(.25, .025, 2), "text-embedding-3-small", new ModelPrice(.02, 0, 0)), null); }
    private Map<String, ModelPrice> prices(AiRuntimeConfig config) { try { return json.readValue(config.getModelPricesJson(), new TypeReference<Map<String, ModelPrice>>() {}); } catch (Exception error) { throw new AppException(ErrorCode.AI_ENGINE_ERROR); } }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String fingerprint(String value) { return sha256(value).substring(0, 12); }
    private String encrypt(String plaintext) { try { byte[] iv = new byte[12]; random.nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv)); byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted); } catch (Exception error) { throw new AppException(ErrorCode.AI_ENGINE_ERROR); } }
    private String decrypt(String payload) { try { String[] chunks = payload.split(":", 2); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(chunks[0]))); return new String(cipher.doFinal(Base64.getDecoder().decode(chunks[1])), StandardCharsets.UTF_8); } catch (Exception error) { throw new AppException(ErrorCode.AI_ENGINE_ERROR); } }
    private byte[] key() { if (blank(encryptionKey)) throw new AppException(ErrorCode.AI_ENGINE_ERROR); return sha256Bytes(encryptionKey); }
    private String sha256(String value) { return Base64.getEncoder().encodeToString(sha256Bytes(value)).replace("/", "").replace("+", "").substring(0, 12); }
    private byte[] sha256Bytes(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
}
