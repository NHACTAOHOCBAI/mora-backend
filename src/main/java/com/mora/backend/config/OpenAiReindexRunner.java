package com.mora.backend.config;

import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.service.DocumentReindexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai-service.reindex-on-startup", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OpenAiReindexRunner implements ApplicationRunner {
    private final DocumentRepository documents;
    private final DocumentReindexService reindexService;

    @Override
    public void run(ApplicationArguments args) {
        int success = 0;
        int failed = 0;
        for (var document : documents.findAll()) {
            if (document.getStatus() != DocumentStatus.READY
                    && document.getStatus() != DocumentStatus.INDEXING
                    && document.getStatus() != DocumentStatus.FAILED) {
                log.info("OpenAI reindex skipped document {} (status {})", document.getId(), document.getStatus());
                continue;
            }
            try {
                reindexService.reindex(document.getId());
                success++;
                log.info("OpenAI reindex succeeded for document {}", document.getId());
            } catch (Exception error) {
                failed++;
                log.error("OpenAI reindex failed for document {}", document.getId(), error);
            }
        }
        log.info("OpenAI reindex complete: success={}, failed={}", success, failed);
        if (failed > 0) {
            throw new IllegalStateException("OpenAI reindex incomplete. Keep RETRIEVAL_ENABLED=false and retry.");
        }
    }
}
