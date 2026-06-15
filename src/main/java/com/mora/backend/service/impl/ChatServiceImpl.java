package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.DocumentChatRequest;
import com.mora.backend.model.dto.response.DocumentChatResponse;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.service.ChatService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final ChatLanguageModel chatLanguageModel;

    // Interface dùng cho LangChain4j AiServices để tự động hóa Prompt và Structured Outputs
    interface GeminiAssistant {
        @SystemMessage("""
            Bạn là một trợ lý học thuật nghiêm khắc.
            Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây.
            Nếu thông tin trong tài liệu không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong tài liệu.").
            """)
        @UserMessage("""
            Ngữ cảnh tài liệu:
            {{context}}
            
            Câu hỏi: {{question}}
            """)
        DocumentChatResponse chat(@V("context") String context, @V("question") String question);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentChatResponse chatWithDocument(DocumentChatRequest request) {
        log.info("Processing chat request for document ID: {}", request.getDocumentId());

        // 1. Kiểm tra tài liệu tồn tại
        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for chat", request.getDocumentId());
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        // 2. Lấy toàn bộ các trang nội dung
        List<DocumentPage> pages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(request.getDocumentId());
        if (pages.isEmpty()) {
            log.warn("No pages found for document ID: {}", request.getDocumentId());
            return DocumentChatResponse.builder()
                    .answerFound(false)
                    .answer("Tài liệu không có nội dung văn bản để phân tích.")
                    .citations(List.of())
                    .build();
        }

        // 3. Định dạng ngữ cảnh đầu vào (Context Formatting) theo Bước 2 trong PHASE_1.md
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("--- BẮT ĐẦU FILE: ").append(document.getFileName()).append(" ---\n");
        for (DocumentPage page : pages) {
            contextBuilder.append("# TRANG ").append(page.getPageNumber()).append("\n");
            contextBuilder.append(page.getContent()).append("\n\n");
        }
        contextBuilder.append("--- KẾT THÚC FILE: ").append(document.getFileName()).append(" ---");
        String context = contextBuilder.toString();

        log.info("Formatted context of size: {} characters. Invoking Gemini model...", context.length());

        // 4. Tạo AI Assistant thông qua LangChain4j AiServices
        GeminiAssistant assistant = AiServices.builder(GeminiAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // 5. Gọi Gemini và nhận kết quả cấu trúc
        try {
            DocumentChatResponse response = assistant.chat(context, request.getQuestion());
            log.info("Successfully received answer from Gemini. AnswerFound: {}", response.isAnswerFound());
            return response;
        } catch (Exception e) {
            log.error("Error occurred while calling Gemini API via LangChain4j", e);
            throw new RuntimeException("Lỗi kết nối hoặc xử lý từ AI Engine: " + e.getMessage(), e);
        }
    }
}
