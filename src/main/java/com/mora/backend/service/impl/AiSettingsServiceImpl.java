package com.mora.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.client.AiServiceClient;
import com.mora.backend.model.dto.response.PageResponse;
import com.mora.backend.model.entity.AiRuntimeConfig;
import com.mora.backend.model.entity.AiSettingsHistory;
import com.mora.backend.repository.AiRuntimeConfigRepository;
import com.mora.backend.repository.AiSettingsHistoryRepository;
import com.mora.backend.service.AiSettingsService;
import com.mora.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

@Service @RequiredArgsConstructor
public class AiSettingsServiceImpl implements AiSettingsService {
    private static final Long SINGLETON_ID = 1L;
    private static final int HISTORY_PAGE_SIZE = 20;
    private final AiRuntimeConfigRepository repository;
    private final AiSettingsHistoryRepository historyRepository;
    private final UserService users;
    private final AiServiceClient aiServiceClient;
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
        repository.lockSettingsUpdate();
        AiRuntimeConfig existing = repository.findById(SINGLETON_ID).orElse(null);
        SettingsView before = existing == null ? defaultView() : view(existing);
        AiRuntimeConfig config = existing == null
                ? AiRuntimeConfig.builder().id(SINGLETON_ID).build()
                : existing.toBuilder().build();
        String submittedKey = request.apiKey() == null ? "" : request.apiKey().trim();
        if (!submittedKey.isBlank()) {
            if (!sameKey(submittedKey, config.getEncryptedApiKey(), before.keyFingerprint())) {
                config.setEncryptedApiKey(encrypt(submittedKey));
                config.setKeyFingerprint(fingerprint(submittedKey));
            }
        } else if (config.getEncryptedApiKey() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        }
        String submittedBenchmarkKey = request.benchmarkApiKey() == null ? "" : request.benchmarkApiKey().trim();
        if (!submittedBenchmarkKey.isBlank()) {
            if (!sameKey(submittedBenchmarkKey, config.getEncryptedBenchmarkApiKey(), before.benchmarkKeyFingerprint())) {
                config.setEncryptedBenchmarkApiKey(encrypt(submittedBenchmarkKey));
                config.setBenchmarkKeyFingerprint(fingerprint(submittedBenchmarkKey));
            }
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
        List<SettingsChange> changes = changesBetween(before, view(config));
        addKeyChange(changes, ChangeGroup.CHAT, existing == null ? null : existing.getEncryptedApiKey(), config.getEncryptedApiKey());
        addKeyChange(changes, ChangeGroup.BENCHMARK, existing == null ? null : existing.getEncryptedBenchmarkApiKey(), config.getEncryptedBenchmarkApiKey());
        if (changes.isEmpty()) return before;

        var actor = users.getCurrentUser();
        config.setUpdatedById(actor.getId());
        AiRuntimeConfig saved = repository.saveAndFlush(config);
        try {
            historyRepository.save(AiSettingsHistory.builder()
                    .savedAt(Instant.now()).actorId(actor.getId()).actorName(actor.getFullName())
                    .changesJson(json.writeValueAsString(changes)).build());
        } catch (JsonProcessingException error) {
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
        return view(saved);
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<HistoryEntry> history(int page) {
        if (page < 0) throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        var result = historyRepository.findAll(PageRequest.of(page, HISTORY_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse<>(result.map(this::historyEntry).getContent(),
                new PageResponse.Meta(new PageResponse.Pagination(result.getTotalElements(), page, HISTORY_PAGE_SIZE)));
    }

    @Override @Transactional(readOnly = true)
    public ValidationResult validate() {
        var result = aiServiceClient.validateConfiguration();
        List<ValidationCheck> checks = result.checks == null ? List.of() : result.checks.stream()
                .map(check -> new ValidationCheck(check.name, check.valid, check.message)).toList();
        return new ValidationResult(result.valid && !checks.isEmpty(), checks);
    }

    private HistoryEntry historyEntry(AiSettingsHistory entry) {
        try {
            List<SettingsChange> changes = json.readValue(entry.getChangesJson(), new TypeReference<>() {});
            return new HistoryEntry(entry.getId(), entry.getSavedAt(), entry.getActorId(), entry.getActorName(), changes);
        } catch (JsonProcessingException error) {
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    private List<SettingsChange> changesBetween(SettingsView before, SettingsView after) {
        List<SettingsChange> changes = new ArrayList<>();
        addChange(changes, ChangeGroup.CHAT, "chatModel", null, before.chatModel(), after.chatModel());
        addChange(changes, ChangeGroup.CHAT, "parserModel", null, before.parserModel(), after.parserModel());
        addChange(changes, ChangeGroup.CHAT, "embeddingModel", null, before.embeddingModel(), after.embeddingModel());
        addChange(changes, ChangeGroup.BENCHMARK, "benchmarkJudgeModel", null, before.benchmarkJudgeModel(), after.benchmarkJudgeModel());
        addChange(changes, ChangeGroup.BENCHMARK, "benchmarkJudgeEmbeddingModel", null, before.benchmarkJudgeEmbeddingModel(), after.benchmarkJudgeEmbeddingModel());
        addChange(changes, ChangeGroup.PRICING, "vndPerUsd", null, before.vndPerUsd(), after.vndPerUsd());
        addChange(changes, ChangeGroup.PRICING, "pricingVersion", null, before.pricingVersion(), after.pricingVersion());

        var models = new TreeSet<>(before.modelPrices().keySet());
        models.addAll(after.modelPrices().keySet());
        for (String model : models) {
            ModelPrice oldPrice = before.modelPrices().get(model);
            ModelPrice newPrice = after.modelPrices().get(model);
            addChange(changes, ChangeGroup.PRICING, "modelPrice", model, oldPrice, newPrice);
        }
        return changes;
    }

    private void addKeyChange(List<SettingsChange> changes, ChangeGroup group, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new SettingsChange(group, "apiKey", null, null, before == null ? "configured" : "changed"));
        }
    }

    private boolean sameKey(String submittedKey, String encryptedKey, String currentFingerprint) {
        if (encryptedKey == null || !fingerprint(submittedKey).equals(currentFingerprint)) return false;
        try {
            return submittedKey.equals(decrypt(encryptedKey));
        } catch (AppException unreadableKey) {
            return false;
        }
    }

    private void addChange(List<SettingsChange> changes, ChangeGroup group, String field, String model, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new SettingsChange(group, field, model, historyValue(before), historyValue(after)));
        }
    }

    private String historyValue(Object value) {
        if (value instanceof ModelPrice price) {
            return "%s / %s / %s".formatted(price.inputPerMillionUsd(), price.cachedInputPerMillionUsd(), price.outputPerMillionUsd());
        }
        return Objects.toString(value, null);
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
