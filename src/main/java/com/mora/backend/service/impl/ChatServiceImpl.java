package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.DocumentChatRequest;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.request.ChatMessageDto;
import com.mora.backend.model.dto.response.DocumentChatResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.ChatMessage;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.ChatMessageRepository;
import com.mora.backend.service.ChatService;
import com.mora.backend.service.DocumentService;
import java.util.Base64;
import java.util.ArrayList;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final SpaceRepository spaceRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Interface dùng cho LangChain4j AiServices để tự động hóa Prompt và Structured Outputs
    interface GeminiAssistant {
        @SystemMessage("""
            Bạn là một trợ lý học thuật nghiêm khắc.
            Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây.
            Nếu ngữ cảnh chứa hình ảnh, hãy phân tích kỹ hình ảnh đó để hỗ trợ trả lời.
            Nếu thông tin trong tài liệu không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong tài liệu.").
            """)
        @UserMessage("""
            Ngữ cảnh tài liệu:
            {{context}}
            
            Câu hỏi: {{question}}
            """)
        DocumentChatResponse chat(@V("context") String context, @V("question") String question);

        @SystemMessage("""
            Bạn là một trợ lý học thuật nghiêm khắc.
            Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây.
            Nếu ngữ cảnh chứa hình ảnh, hãy phân tích kỹ hình ảnh đó để hỗ trợ trả lời.
            Nếu thông tin trong tài liệu không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong tài liệu.").
            """)
        @UserMessage("""
            Ngữ cảnh tài liệu:
            {{context}}
            
            Câu hỏi: {{question}}
            """)
        DocumentChatResponse chatWithImage(@V("context") String context, @V("question") String question, @V("image") dev.langchain4j.data.image.Image image);
    }

    interface SpaceAssistant {
        @SystemMessage("""
            Bạn là một trợ lý học thuật nghiêm khắc.
            Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây.
            Ngữ cảnh chứa nhiều tài liệu khác nhau. Mỗi tài liệu được phân tách bằng '--- BẮT ĐẦU FILE: ID [id_cua_file], TÊN [tên file] ---' và '--- KẾT THÚC FILE...'.
            Nếu ngữ cảnh chứa hình ảnh, hãy phân tích kỹ hình ảnh đó để hỗ trợ trả lời.
            Nếu thông tin trong các tài liệu không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong các tài liệu của không gian học tập.").
            Trong mảng trích dẫn (citations), với mỗi trích dẫn bạn phải cung cấp chính xác 'documentId' (lấy từ ID [id_cua_file] trong tiêu đề file tương ứng) và 'pageNumber' của trang chứa câu trích dẫn đó.
            """)
        @UserMessage("""
            Ngữ cảnh tài liệu:
            {{context}}
            
            Câu hỏi: {{question}}
            """)
        SpaceChatResponse chat(@V("context") String context, @V("question") String question);

        @SystemMessage("""
            Bạn là một trợ lý học thuật nghiêm khắc.
            Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây.
            Ngữ cảnh chứa nhiều tài liệu khác nhau. Mỗi tài liệu được phân tách bằng '--- BẮT ĐẦU FILE: ID [id_cua_file], TÊN [tên file] ---' và '--- KẾT THÚC FILE...'.
            Nếu ngữ cảnh chứa hình ảnh, hãy phân tích kỹ hình ảnh đó để hỗ trợ trả lời.
            Nếu thông tin trong các tài liệu không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong các tài liệu của không gian học tập.").
            Trong mảng trích dẫn (citations), với mỗi trích dẫn bạn phải cung cấp chính xác 'documentId' (lấy từ ID [id_cua_file] trong tiêu đề file tương ứng) và 'pageNumber' của trang chứa câu trích dẫn đó.
            """)
        @UserMessage("""
            Ngữ cảnh tài liệu:
            {{context}}
            
            Câu hỏi: {{question}}
            """)
        SpaceChatResponse chatWithImages(@V("context") String context, @V("question") String question, @V("images") List<dev.langchain4j.data.image.Image> images);
    }

    // Interface dùng để rút gọn câu hỏi nối tiếp dựa trên lịch sử
    interface QuestionCondenser {
        @SystemMessage("""
            Bạn là một trợ lý ngôn ngữ AI thông minh.
            Nhiệm vụ của bạn là kết hợp lịch sử cuộc trò chuyện gần nhất và câu hỏi mới của người dùng thành một "Câu hỏi độc lập" (Standalone Question) hoàn chỉnh, rõ nghĩa, và tự chứa đầy đủ ngữ cảnh để có thể dùng truy vấn trực tiếp vào tài liệu.
            - Không được trả lời câu hỏi, CHỈ được viết lại câu hỏi.
            - Giữ nguyên ngôn ngữ của câu hỏi gốc (nếu là Tiếng Việt thì viết lại bằng Tiếng Việt).
            - Nếu câu hỏi mới đã đầy đủ nghĩa và không phụ thuộc vào lịch sử chat, hãy trả về chính xác câu hỏi mới đó.
            """)
        @UserMessage("""
            Lịch sử trò chuyện:
            {{history}}
            
            Câu hỏi mới: {{question}}
            
            Hãy viết lại câu hỏi độc lập:
            """)
        String condense(@V("history") String history, @V("question") String question);
    }

    private String getCondensedQuestion(List<ChatMessageDto> history, String question) {
        if (history == null || history.isEmpty()) {
            return question;
        }

        StringBuilder historyBuilder = new StringBuilder();
        for (ChatMessageDto msg : history) {
            String role = "user".equalsIgnoreCase(msg.getSender()) ? "User" : "Assistant";
            historyBuilder.append(role).append(": ").append(msg.getText()).append("\n");
        }

        try {
            QuestionCondenser condenser = AiServices.builder(QuestionCondenser.class)
                    .chatLanguageModel(chatLanguageModel)
                    .build();
            String rewritten = condenser.condense(historyBuilder.toString(), question);
            if (rewritten != null && !rewritten.trim().isEmpty()) {
                return rewritten.trim();
            }
        } catch (Exception e) {
            log.error("Failed to condense question, falling back to original question", e);
        }
        return question;
    }

    @Override
    @Transactional
    public DocumentChatResponse chatWithDocument(DocumentChatRequest request) {
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

        // Lưu tin nhắn User gửi vào DB
        ChatMessage userMessage = ChatMessage.builder()
                .sender("user")
                .text(request.getQuestion())
                .document(document)
                .space(document.getSpace())
                .build();
        chatMessageRepository.save(userMessage);

        // 3. Định dạng ngữ cảnh đầu vào (Context Formatting) theo Bước 2 trong PHASE_1.md
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("--- BẮT ĐẦU FILE: ").append(document.getFileName()).append(" ---\n");
        for (DocumentPage page : pages) {
            contextBuilder.append("--- TRANG ").append(page.getPageNumber()).append(" ---\n");
            contextBuilder.append(page.getContent()).append("\n\n");
        }
        contextBuilder.append("--- KẾT THÚC FILE: ").append(document.getFileName()).append(" ---");
        String context = contextBuilder.toString();

        // 4. Rút gọn câu hỏi dựa trên lịch sử lưu trong DB
        List<ChatMessage> dbHistory = chatMessageRepository.findByDocumentIdOrderByCreatedAtAsc(request.getDocumentId());
        List<ChatMessageDto> historyDtoList = dbHistory.stream()
                .filter(m -> !m.getId().equals(userMessage.getId())) // loại bỏ tin nhắn vừa lưu
                .map(msg -> ChatMessageDto.builder()
                        .sender(msg.getSender())
                        .text(msg.getText())
                        .build())
                .toList();

        String condensedQuestion = getCondensedQuestion(historyDtoList, request.getQuestion());

        // 5. Kiểm tra và chuẩn bị ảnh để gửi kèm nếu có trang chứa hình ảnh
        dev.langchain4j.data.image.Image imageToSend = null;
        boolean isPdf = "pdf".equalsIgnoreCase(document.getFileType());

        if (!isPdf) {
            try {
                byte[] imageBytes = documentService.renderPageImage(document.getId(), 1);
                if (imageBytes != null && imageBytes.length > 0) {
                    imageToSend = dev.langchain4j.data.image.Image.builder()
                            .base64Data(Base64.getEncoder().encodeToString(imageBytes))
                            .mimeType("image/jpeg")
                            .build();
                }
            } catch (Exception e) {
                log.warn("Lỗi khi kết xuất ảnh cho tài liệu hình ảnh", e);
            }
        } else {
            int targetPage = extractPageNumber(request.getQuestion());
            DocumentPage pageWithImage = null;
            
            if (targetPage > 0) {
                for (DocumentPage page : pages) {
                    if (page.getPageNumber() == targetPage && Boolean.TRUE.equals(page.getHasImage())) {
                        pageWithImage = page;
                        break;
                    }
                }
            }
            
            if (pageWithImage == null) {
                for (DocumentPage page : pages) {
                    if (Boolean.TRUE.equals(page.getHasImage())) {
                        pageWithImage = page;
                        break;
                    }
                }
            }
            
            if (pageWithImage != null) {
                try {
                    byte[] imageBytes = documentService.renderPageImage(document.getId(), pageWithImage.getPageNumber());
                    if (imageBytes != null && imageBytes.length > 0) {
                        imageToSend = dev.langchain4j.data.image.Image.builder()
                                .base64Data(Base64.getEncoder().encodeToString(imageBytes))
                                .mimeType("image/jpeg")
                                .build();
                        log.info("Đã tự động gửi kèm ảnh của trang {} để trợ giúp hỏi đáp", pageWithImage.getPageNumber());
                    }
                } catch (Exception e) {
                    log.warn("Lỗi khi render trang {} làm ảnh", pageWithImage.getPageNumber(), e);
                }
            }
        }

        // 6. Tạo AI Assistant thông qua LangChain4j AiServices
        GeminiAssistant assistant = AiServices.builder(GeminiAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        String fullPrompt = String.format("""
                [SYSTEM PROMPT]
                Bạn là một trợ lý học thuật nghiêm khắc.
                Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây.
                Nếu ngữ cảnh chứa hình ảnh, hãy phân tích kỹ hình ảnh đó để hỗ trợ trả lời.
                Nếu thông tin trong tài liệu không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong tài liệu.").
                
                [USER MESSAGE]
                Ngữ cảnh tài liệu:
                %s
                
                Câu hỏi: %s""", context, condensedQuestion);

        // 7. Gọi Gemini và nhận kết quả cấu trúc
        try {
            DocumentChatResponse response;
            if (imageToSend != null) {
                response = assistant.chatWithImage(context, condensedQuestion, imageToSend);
            } else {
                response = assistant.chat(context, condensedQuestion);
            }
            
            response.setCondensedQuestion(condensedQuestion);
            response.setPromptSent(fullPrompt);
            if (response.getCitations() != null) {
                for (DocumentChatResponse.Citation citation : response.getCitations()) {
                    citation.setDocumentId(document.getId());
                    citation.setDocumentName(document.getFileName());
                }
            }

            // Lưu phản hồi của AI vào DB
            String citationsJson = null;
            if (response.getCitations() != null) {
                try {
                    citationsJson = objectMapper.writeValueAsString(response.getCitations());
                } catch (Exception e) {
                    log.error("Failed to serialize citations to JSON", e);
                }
            }

            ChatMessage assistantMessage = ChatMessage.builder()
                    .sender("assistant")
                    .text(response.isAnswerFound() ? response.getAnswer() : "Tôi không thể tìm thấy câu trả lời cho câu hỏi này trong nội dung tài liệu.")
                    .document(document)
                    .space(document.getSpace())
                    .citations(citationsJson)
                    .condensedQuestion(condensedQuestion)
                    .promptSent(fullPrompt)
                    .build();
            chatMessageRepository.save(assistantMessage);

            return response;
        } catch (Exception e) {
            log.error("Error occurred while calling Gemini API via LangChain4j", e);
            throw new RuntimeException("Lỗi kết nối hoặc xử lý từ AI Engine: " + e.getMessage(), e);
        }
    }

    private int extractPageNumber(String question) {
        if (question == null) return -1;
        String lower = question.toLowerCase();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:trang|page)\\s*(\\d+)").matcher(lower);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return -1;
    }

    @Override
    @Transactional
    public SpaceChatResponse chatWithSpace(SpaceChatRequest request) {
        // 1. Kiểm tra Space tồn tại
        Space space = spaceRepository.findById(request.getSpaceId())
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for chat", request.getSpaceId());
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        // 2. Lấy toàn bộ các trang nội dung của mọi tài liệu trong Space
        List<DocumentPage> pages = documentPageRepository.findBySpaceId(request.getSpaceId());
        if (pages.isEmpty()) {
            log.warn("No pages found for any document in space ID: {}", request.getSpaceId());
            return SpaceChatResponse.builder()
                    .answerFound(false)
                    .answer("Không gian học tập chưa có tài liệu nào hoặc tài liệu không có văn bản.")
                    .citations(List.of())
                    .build();
        }

        // Lưu tin nhắn User gửi vào DB
        ChatMessage userMessage = ChatMessage.builder()
                .sender("user")
                .text(request.getQuestion())
                .document(null)
                .space(space)
                .build();
        chatMessageRepository.save(userMessage);

        // 3. Định dạng siêu ngữ cảnh đầu vào (Multi-Document Context Formatting)
        StringBuilder contextBuilder = new StringBuilder();
        Long currentDocId = null;
        for (DocumentPage page : pages) {
            Document doc = page.getDocument();
            if (currentDocId == null || !currentDocId.equals(doc.getId())) {
                if (currentDocId != null) {
                    contextBuilder.append("--- KẾT THÚC FILE: ID [").append(currentDocId).append("] ---\n\n");
                }
                currentDocId = doc.getId();
                contextBuilder.append("--- BẮT ĐẦU FILE: ID [").append(currentDocId)
                        .append("], TÊN [").append(doc.getFileName()).append("] ---\n");
            }
            contextBuilder.append("--- TRANG ").append(page.getPageNumber()).append(" ---\n");
            contextBuilder.append(page.getContent()).append("\n\n");
        }
        if (currentDocId != null) {
            contextBuilder.append("--- KẾT THÚC FILE: ID [").append(currentDocId).append("] ---");
        }
        String context = contextBuilder.toString();

        // 4. Rút gọn câu hỏi dựa trên lịch sử lưu trong DB
        List<ChatMessage> dbHistory = chatMessageRepository.findBySpaceIdAndDocumentIsNullOrderByCreatedAtAsc(request.getSpaceId());
        List<ChatMessageDto> historyDtoList = dbHistory.stream()
                .filter(m -> !m.getId().equals(userMessage.getId())) // loại bỏ tin nhắn vừa lưu
                .map(msg -> ChatMessageDto.builder()
                        .sender(msg.getSender())
                        .text(msg.getText())
                        .build())
                .toList();

        String condensedQuestion = getCondensedQuestion(historyDtoList, request.getQuestion());

        // 5. Kiểm tra và chuẩn bị ảnh từ các trang chứa hình ảnh (tối đa 3 ảnh)
        List<dev.langchain4j.data.image.Image> imagesToSend = new ArrayList<>();
        int imageCount = 0;
        for (DocumentPage page : pages) {
            if (Boolean.TRUE.equals(page.getHasImage())) {
                try {
                    byte[] imageBytes = documentService.renderPageImage(page.getDocument().getId(), page.getPageNumber());
                    if (imageBytes != null && imageBytes.length > 0) {
                        dev.langchain4j.data.image.Image imgObj = dev.langchain4j.data.image.Image.builder()
                                .base64Data(Base64.getEncoder().encodeToString(imageBytes))
                                .mimeType("image/jpeg")
                                .build();
                        imagesToSend.add(imgObj);
                        imageCount++;
                        if (imageCount >= 3) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Lỗi render ảnh trong Space Chat cho doc {}, page {}", page.getDocument().getId(), page.getPageNumber(), e);
                }
            }
        }

        // 6. Tạo AI Assistant thông qua LangChain4j AiServices
        SpaceAssistant assistant = AiServices.builder(SpaceAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        String fullPrompt = String.format("""
                [SYSTEM PROMPT]
                Bạn là một trợ lý học thuật nghiêm khắc.
                Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây.
                Ngữ cảnh chứa nhiều tài liệu khác nhau. Mỗi tài liệu được phân tách bằng '--- BẮT ĐẦU FILE: ID [id_cua_file], TÊN [tên file] ---' và '--- KẾT THÚC FILE...'.
                Nếu ngữ cảnh chứa hình ảnh, hãy phân tích kỹ hình ảnh đó để hỗ trợ trả lời.
                Nếu thông tin trong các tài liệu không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong các tài liệu của không gian học tập.").
                Trong mảng trích dẫn (citations), với mỗi trích dẫn bạn phải cung cấp chính xác 'documentId' (lấy từ ID [id_cua_file] trong tiêu đề file tương ứng) và 'pageNumber' của trang chứa câu trích dẫn đó.
                
                [USER MESSAGE]
                Ngữ cảnh tài liệu:
                %s
                
                Câu hỏi: %s""", context, condensedQuestion);

        // 7. Gọi Gemini và nhận kết quả cấu trúc
        try {
            SpaceChatResponse response;
            if (!imagesToSend.isEmpty()) {
                response = assistant.chatWithImages(context, condensedQuestion, imagesToSend);
            } else {
                response = assistant.chat(context, condensedQuestion);
            }
            
            response.setCondensedQuestion(condensedQuestion);
            response.setPromptSent(fullPrompt);
            if (response.getCitations() != null) {
                for (SpaceChatResponse.SpaceCitation citation : response.getCitations()) {
                    if (citation.getDocumentId() != null) {
                        documentRepository.findById(citation.getDocumentId())
                            .ifPresent(doc -> citation.setDocumentName(doc.getFileName()));
                    }
                }
            }

            // Lưu phản hồi của AI vào DB
            String citationsJson = null;
            if (response.getCitations() != null) {
                try {
                    citationsJson = objectMapper.writeValueAsString(response.getCitations());
                } catch (Exception e) {
                    log.error("Failed to serialize space citations to JSON", e);
                }
            }

            ChatMessage assistantMessage = ChatMessage.builder()
                    .sender("assistant")
                    .text(response.isAnswerFound() ? response.getAnswer() : "Tôi không tìm thấy câu trả lời phù hợp trong các tài liệu của không gian học tập.")
                    .document(null)
                    .space(space)
                    .citations(citationsJson)
                    .condensedQuestion(condensedQuestion)
                    .promptSent(fullPrompt)
                    .build();
            chatMessageRepository.save(assistantMessage);

            return response;
        } catch (Exception e) {
            log.error("Error occurred while calling Gemini API via LangChain4j for space", e);
            throw new RuntimeException("Lỗi kết nối hoặc xử lý từ AI Engine: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getDocumentChatHistory(Long documentId) {
        List<ChatMessage> messages = chatMessageRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        return messages.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getSpaceChatHistory(Long spaceId) {
        List<ChatMessage> messages = chatMessageRepository.findBySpaceIdAndDocumentIsNullOrderByCreatedAtAsc(spaceId);
        return messages.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public void clearDocumentChatHistory(Long documentId) {
        chatMessageRepository.deleteByDocumentId(documentId);
    }

    @Override
    @Transactional
    public void clearSpaceChatHistory(Long spaceId) {
        chatMessageRepository.deleteBySpaceIdAndDocumentIsNull(spaceId);
    }

    private ChatMessageResponse mapToResponse(ChatMessage msg) {
        List<ChatMessageResponse.Citation> citations = null;
        if (msg.getCitations() != null && !msg.getCitations().isEmpty()) {
            try {
                citations = objectMapper.readValue(msg.getCitations(), new TypeReference<List<ChatMessageResponse.Citation>>() {});
                if (citations != null) {
                    for (ChatMessageResponse.Citation citation : citations) {
                        if (citation.getDocumentId() != null) {
                            documentRepository.findById(citation.getDocumentId())
                                .ifPresent(doc -> citation.setDocumentName(doc.getFileName()));
                        } else if (msg.getDocument() != null) {
                            citation.setDocumentName(msg.getDocument().getFileName());
                            citation.setDocumentId(msg.getDocument().getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to deserialize citations from JSON", e);
            }
        }
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .sender(msg.getSender())
                .text(msg.getText())
                .timestamp(msg.getCreatedAt())
                .citations(citations)
                .condensedQuestion(msg.getCondensedQuestion())
                .promptSent(msg.getPromptSent())
                .build();
    }
}
