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
import com.mora.backend.client.AiServiceClient;
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
    private final AiServiceClient aiServiceClient;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper = new ObjectMapper();



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

        // 3. Kiểm tra và chuẩn bị ảnh để gửi kèm nếu có trang chứa hình ảnh (Đưa lên trước để đưa vào debugContext)
        List<String> base64Images = new ArrayList<>();
        java.util.Map<Integer, String> pageImageMap = new java.util.HashMap<>();
        boolean isPdf = "pdf".equalsIgnoreCase(document.getFileType());

        if (!isPdf) {
            try {
                byte[] imageBytes = documentService.renderPageImage(document.getId(), 1);
                if (imageBytes != null && imageBytes.length > 0) {
                    String base64Str = Base64.getEncoder().encodeToString(imageBytes);
                    base64Images.add(base64Str);
                    pageImageMap.put(1, base64Str);
                }
            } catch (Exception e) {
                log.warn("Lỗi khi kết xuất ảnh cho tài liệu hình ảnh", e);
            }
        } else {
            int targetPage = extractPageNumber(request.getQuestion());
            
            // Prioritize the page matching targetPage if it has an image
            if (targetPage > 0) {
                for (DocumentPage page : pages) {
                    if (page.getPageNumber() == targetPage && Boolean.TRUE.equals(page.getHasImage())) {
                        try {
                            byte[] imageBytes = documentService.renderPageImage(document.getId(), page.getPageNumber());
                            if (imageBytes != null && imageBytes.length > 0) {
                                String base64Str = Base64.getEncoder().encodeToString(imageBytes);
                                base64Images.add(base64Str);
                                pageImageMap.put(page.getPageNumber(), base64Str);
                                log.info("Đã tự động gửi kèm ảnh của trang {} (được yêu cầu) để trợ giúp hỏi đáp", page.getPageNumber());
                            }
                        } catch (Exception e) {
                            log.warn("Lỗi khi render trang {} làm ảnh", page.getPageNumber(), e);
                        }
                        break;
                    }
                }
            }
            
            // Fill other pages that have images
            for (DocumentPage page : pages) {
                if (Boolean.TRUE.equals(page.getHasImage()) && !pageImageMap.containsKey(page.getPageNumber())) {
                    try {
                        byte[] imageBytes = documentService.renderPageImage(document.getId(), page.getPageNumber());
                        if (imageBytes != null && imageBytes.length > 0) {
                            String base64Str = Base64.getEncoder().encodeToString(imageBytes);
                            base64Images.add(base64Str);
                            pageImageMap.put(page.getPageNumber(), base64Str);
                            log.info("Đã tự động gửi kèm ảnh của trang {} để trợ giúp hỏi đáp", page.getPageNumber());
                        }
                    } catch (Exception e) {
                        log.warn("Lỗi khi render trang {} làm ảnh", page.getPageNumber(), e);
                    }
                }
            }
        }

        // 4. Định dạng ngữ cảnh đầu vào (Context Formatting) theo Bước 2 trong PHASE_1.md
        StringBuilder contextBuilder = new StringBuilder();
        StringBuilder debugContextBuilder = new StringBuilder();
        
        contextBuilder.append("--- BẮT ĐẦU FILE: ").append(document.getFileName()).append(" ---\n");
        debugContextBuilder.append("--- BẮT ĐẦU FILE: ").append(document.getFileName()).append(" ---\n");
        
        for (DocumentPage page : pages) {
            contextBuilder.append("--- TRANG ").append(page.getPageNumber()).append(" ---\n");
            contextBuilder.append(page.getContent()).append("\n\n");
            
            debugContextBuilder.append("--- TRANG ").append(page.getPageNumber()).append(" ---\n");
            // Nếu trang này chứa ảnh được chọn gửi, chèn thẻ img ngay sau tiêu đề trang trong debugContext
            if (pageImageMap.containsKey(page.getPageNumber())) {
                debugContextBuilder.append(String.format("<img src=\"data:image/jpeg;base64,%s\" />\n\n", pageImageMap.get(page.getPageNumber())));
            }
            debugContextBuilder.append(page.getContent()).append("\n\n");
        }
        
        contextBuilder.append("--- KẾT THÚC FILE: ").append(document.getFileName()).append(" ---");
        debugContextBuilder.append("--- KẾT THÚC FILE: ").append(document.getFileName()).append(" ---");
        
        String context = contextBuilder.toString();
        String debugContext = debugContextBuilder.toString();

        // 5. Rút gọn câu hỏi dựa trên lịch sử lưu trong DB
        List<ChatMessage> dbHistory = chatMessageRepository.findByDocumentIdOrderByCreatedAtAsc(request.getDocumentId());
        List<ChatMessageDto> historyDtoList = dbHistory.stream()
                .filter(m -> !m.getId().equals(userMessage.getId())) // loại bỏ tin nhắn vừa lưu
                .map(msg -> ChatMessageDto.builder()
                        .sender(msg.getSender())
                        .text(msg.getText())
                        .build())
                .toList();

        // 6. Gọi Python AI service qua Client
        try {
            logGeminiRequest(
                    "Bạn là một trợ lý học thuật nghiêm khắc.\nHãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây (bao gồm cả nội dung văn bản và hình ảnh của tài liệu đó).\nNếu tài liệu có hình ảnh đính kèm (hoặc bản thân tài liệu là hình ảnh), hãy phân tích kỹ hình ảnh và bạn ĐƯỢC PHÉP suy luận logic dựa trên hình ảnh để trả lời câu hỏi của người dùng.\nNếu thông tin trong tài liệu (cả phần chữ và phần hình ảnh) không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: \"Tôi không tìm thấy thông tin này trong tài liệu.\").",
                    context,
                    request.getQuestion(),
                    base64Images
            );
            
            DocumentChatResponse response = aiServiceClient.chatWithDocument(context, request.getQuestion(), base64Images, historyDtoList);
            
            String actualCondensedQuestion = response.getCondensedQuestion() != null ? response.getCondensedQuestion() : request.getQuestion();
            String fullPrompt = String.format("""
                    [SYSTEM PROMPT]
                    Bạn là một trợ lý học thuật nghiêm khắc.
                    Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây (bao gồm cả nội dung văn bản và hình ảnh của tài liệu đó).
                    Nếu tài liệu có hình ảnh đính kèm (hoặc bản thân tài liệu là hình ảnh), hãy phân tích kỹ hình ảnh và bạn ĐƯỢC PHÉP suy luận logic dựa trên hình ảnh để trả lời câu hỏi của người dùng.
                    Nếu thông tin trong tài liệu (cả phần chữ và phần hình ảnh) không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong tài liệu.").
                    
                    [USER MESSAGE]
                    Ngữ cảnh tài liệu:
                    %s
                    
                    Câu hỏi: %s""", debugContext, actualCondensedQuestion);

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
                    .text(response.getAnswer())
                    .document(document)
                    .space(document.getSpace())
                    .citations(citationsJson)
                    .condensedQuestion(actualCondensedQuestion)
                    .promptSent(fullPrompt)
                    .build();
            chatMessageRepository.save(assistantMessage);

            return response;
        } catch (Exception e) {
            log.error("Error occurred while calling Gemini API via Python service", e);
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

        // 3. Kiểm tra và chuẩn bị ảnh từ các trang chứa hình ảnh (không giới hạn ảnh) (Đưa lên trước để đưa vào debugContext)
        List<String> base64Images = new ArrayList<>();
        java.util.Map<String, String> pageImageMap = new java.util.HashMap<>();
        for (DocumentPage page : pages) {
            if (Boolean.TRUE.equals(page.getHasImage())) {
                try {
                    byte[] imageBytes = documentService.renderPageImage(page.getDocument().getId(), page.getPageNumber());
                    if (imageBytes != null && imageBytes.length > 0) {
                        String base64Str = Base64.getEncoder().encodeToString(imageBytes);
                        base64Images.add(base64Str);
                        
                        String key = page.getDocument().getId() + "_" + page.getPageNumber();
                        pageImageMap.put(key, base64Str);
                    }
                } catch (Exception e) {
                    log.warn("Lỗi render ảnh trong Space Chat cho doc {}, page {}", page.getDocument().getId(), page.getPageNumber(), e);
                }
            }
        }

        // 4. Định dạng siêu ngữ cảnh đầu vào (Multi-Document Context Formatting)
        StringBuilder contextBuilder = new StringBuilder();
        StringBuilder debugContextBuilder = new StringBuilder();
        Long currentDocId = null;
        for (DocumentPage page : pages) {
            Document doc = page.getDocument();
            if (currentDocId == null || !currentDocId.equals(doc.getId())) {
                if (currentDocId != null) {
                    contextBuilder.append("--- KẾT THÚC FILE: ID [").append(currentDocId).append("] ---\n\n");
                    debugContextBuilder.append("--- KẾT THÚC FILE: ID [").append(currentDocId).append("] ---\n\n");
                }
                currentDocId = doc.getId();
                contextBuilder.append("--- BẮT ĐẦU FILE: ID [").append(currentDocId)
                        .append("], TÊN [").append(doc.getFileName()).append("] ---\n");
                debugContextBuilder.append("--- BẮT ĐẦU FILE: ID [").append(currentDocId)
                        .append("], TÊN [").append(doc.getFileName()).append("] ---\n");
            }
            contextBuilder.append("--- TRANG ").append(page.getPageNumber()).append(" ---\n");
            contextBuilder.append(page.getContent()).append("\n\n");
            
            debugContextBuilder.append("--- TRANG ").append(page.getPageNumber()).append(" ---\n");
            // Nếu trang này chứa ảnh nằm trong danh sách được chọn gửi, chèn thẻ img ngay dưới tiêu đề trang
            String key = doc.getId() + "_" + page.getPageNumber();
            if (pageImageMap.containsKey(key)) {
                debugContextBuilder.append(String.format("<img src=\"data:image/jpeg;base64,%s\" />\n\n", pageImageMap.get(key)));
            }
            debugContextBuilder.append(page.getContent()).append("\n\n");
        }
        if (currentDocId != null) {
            contextBuilder.append("--- KẾT THÚC FILE: ID [").append(currentDocId).append("] ---");
            debugContextBuilder.append("--- KẾT THÚC FILE: ID [").append(currentDocId).append("] ---");
        }
        String context = contextBuilder.toString();
        String debugContext = debugContextBuilder.toString();

        // 5. Rút gọn câu hỏi dựa trên lịch sử lưu trong DB
        List<ChatMessage> dbHistory = chatMessageRepository.findBySpaceIdAndDocumentIsNullOrderByCreatedAtAsc(request.getSpaceId());
        List<ChatMessageDto> historyDtoList = dbHistory.stream()
                .filter(m -> !m.getId().equals(userMessage.getId())) // loại bỏ tin nhắn vừa lưu
                .map(msg -> ChatMessageDto.builder()
                        .sender(msg.getSender())
                        .text(msg.getText())
                        .build())
                .toList();

        // 6. Gọi Python AI service qua Client
        try {
            logGeminiRequest(
                    "Bạn là một trợ lý học thuật nghiêm khắc.\nHãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây (bao gồm cả nội dung văn bản và hình ảnh của tài liệu đó).\nNgữ cảnh chứa nhiều tài liệu khác nhau. Mỗi tài liệu được phân tách bằng '--- BẮT ĐẦU FILE: ID [id_cua_file], TÊN [tên file] ---' và '--- KẾT THÚC FILE...'.\nNếu tài liệu có hình ảnh đính kèm (hoặc bản thân tài liệu là hình ảnh), hãy phân tích kỹ hình ảnh và bạn ĐƯỢC PHÉP suy luận logic dựa trên hình ảnh để trả lời câu hỏi của người dùng.\nNếu thông tin trong các tài liệu (cả phần chữ và phần hình ảnh) không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: \"Tôi không tìm thấy thông tin này trong các tài liệu của không gian học tập.\").\nTrong mảng trích dẫn (citations), với mỗi trích dẫn bạn phải cung cấp chính xác 'documentId' (lấy từ ID [id_cua_file] trong tiêu đề file tương ứng) và 'pageNumber' của trang chứa câu trích dẫn đó.",
                    context,
                    request.getQuestion(),
                    base64Images
            );
            
            SpaceChatResponse response = aiServiceClient.chatWithSpace(context, request.getQuestion(), base64Images, historyDtoList);
            
            String actualCondensedQuestion = response.getCondensedQuestion() != null ? response.getCondensedQuestion() : request.getQuestion();
            String fullPrompt = String.format("""
                    [SYSTEM PROMPT]
                    Bạn là một trợ lý học thuật nghiêm khắc.
                    Hãy trả lời câu hỏi của người dùng CHỈ sử dụng thông tin từ ngữ cảnh tài liệu được cung cấp dưới đây (bao gồm cả nội dung văn bản và hình ảnh của tài liệu đó).
                    Ngữ cảnh chứa nhiều tài liệu khác nhau. Mỗi tài liệu được phân tách bằng '--- BẮT ĐẦU FILE: ID [id_cua_file], TÊN [tên file] ---' và '--- KẾT THÚC FILE...'.
                    Nếu tài liệu có hình ảnh đính kèm (hoặc bản thân tài liệu là hình ảnh), hãy phân tích kỹ hình ảnh và bạn ĐƯỢC PHÉP suy luận logic dựa trên hình ảnh để trả lời câu hỏi của người dùng.
                    Nếu thông tin trong các tài liệu (cả phần chữ và phần hình ảnh) không đủ hoặc câu hỏi nằm ngoài phạm vi tài liệu, bạn bắt buộc phải trả lời 'false' cho trường 'answerFound', không được tự ý đoán mò, và đặt 'answer' thành câu từ chối trả lời phù hợp (Ví dụ: "Tôi không tìm thấy thông tin này trong các tài liệu của không gian học tập.").
                    Trong mảng trích dẫn (citations), với mỗi trích dẫn bạn phải cung cấp chính xác 'documentId' (lấy từ ID [id_cua_file] trong tiêu đề file tương ứng) và 'pageNumber' của trang chứa câu trích dẫn đó.
                    
                    [USER MESSAGE]
                    Ngữ cảnh tài liệu:
                    %s
                    
                    Câu hỏi: %s""", debugContext, actualCondensedQuestion);

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
                    .text(response.getAnswer())
                    .document(null)
                    .space(space)
                    .citations(citationsJson)
                    .condensedQuestion(actualCondensedQuestion)
                    .promptSent(fullPrompt)
                    .build();
            chatMessageRepository.save(assistantMessage);

            return response;
        } catch (Exception e) {
            log.error("Error occurred while calling Gemini API via Python service for space", e);
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

    private void logGeminiRequest(String systemPrompt, String context, String question, List<String> base64Images) {
        StringBuilder partsBuilder = new StringBuilder();
        
        // System Prompt + User Message Text
        String text = String.format("[SYSTEM PROMPT]\n%s\n\n[USER MESSAGE]\nNgữ cảnh tài liệu:\n%s\n\nCâu hỏi: %s", systemPrompt, context, question);
        
        // Escape JSON characters in text
        String escapedText = text.replace("\\", "\\\\")
                                 .replace("\"", "\\\"")
                                 .replace("\n", "\\n")
                                 .replace("\r", "");
        
        partsBuilder.append("        {\n")
                    .append("          \"text\": \"").append(escapedText).append("\"\n")
                    .append("        }");
        
        if (base64Images != null) {
            for (String base64 : base64Images) {
                partsBuilder.append(",\n        {\n")
                            .append("          \"inlineData\": {\n")
                            .append("            \"mimeType\": \"image/jpeg\",\n")
                            .append("            \"data\": \"").append(base64).append("\"\n")
                            .append("          }\n")
                            .append("        }");
            }
        }
        
        String json = "{\n" +
                "  \"contents\": [\n" +
                "    {\n" +
                "      \"parts\": [\n" +
                partsBuilder.toString() + "\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        
        log.info("[GEMINI REQUEST JSON]:\n{}", json);
    }
}
