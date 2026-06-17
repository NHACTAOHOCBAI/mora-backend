package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentPageResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.model.entity.Space;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.service.DocumentService;
import com.mora.backend.service.StorageService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final SpaceRepository spaceRepository;
    private final ChatLanguageModel chatLanguageModel;

    // Interface dùng cho LangChain4j AiServices để tự động hóa Prompt sinh Note & Flashcards
    interface GeminiStudyHelper {
        @SystemMessage("""
            Bạn là một chuyên gia tóm tắt tài liệu và giảng dạy học thuật.
            Nhiệm vụ của bạn là đọc nội dung tài liệu được cung cấp và sinh ra 2 phần:
            1. Tóm tắt nội dung tài liệu (dưới dạng Markdown chi tiết, cấu trúc rõ ràng, sinh động, dễ học).
            2. Một danh sách gồm khoảng 5-10 câu hỏi ôn tập (Flashcards) dưới dạng định dạng JSON chuẩn. Mỗi flashcard có cấu trúc: {"question": "câu hỏi...", "answer": "câu trả lời..."}
            
            Vì kết quả trả về cần được tách biệt rõ ràng để xử lý lập trình, bạn BẮT BUỘC phải trả về kết quả chính xác theo định dạng phân tách sau đây:
            === BẮT ĐẦU TÓM TẮT ===
            [Nội dung tóm tắt ở định dạng Markdown]
            === KẾT THÚC TÓM TẮT ===
            === BẮT ĐẦU FLASHCARDS ===
            [Mảng JSON chứa các flashcards, ví dụ: [{"question": "Câu hỏi 1?", "answer": "Đáp án 1"}, {"question": "Câu hỏi 2?", "answer": "Đáp án 2"}]]
            === KẾT THÚC FLASHCARDS ===
            
            Hãy đảm bảo bạn chỉ sử dụng thông tin trong tài liệu đã cung cấp.
            """)
        @UserMessage("""
            Tài liệu:
            {{context}}
            """)
        String generateStudyNotes(@V("context") String context);
    }


    @Override
    @Transactional
    public DocumentResponse uploadAndProcessDocument(MultipartFile file, Long spaceId) {
        // Validation: Verify Space exists
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for document upload", spaceId);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        // Validation: Verify if file is PDF
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (!Objects.equals(contentType, "application/pdf") && 
            (filename == null || !filename.toLowerCase().endsWith(".pdf"))) {
            log.warn("Invalid file format uploaded: {}", filename);
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        // 1. Upload to Supabase Storage
        String storageUrl;
        try {
            storageUrl = storageService.upload(file);
        } catch (Exception e) {
            log.error("Failed to upload file to storage service: {}", filename, e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // 2. Save Document metadata first
        Document document = Document.builder()
                .fileName(filename)
                .fileType("pdf")
                .storageUrl(storageUrl)
                .space(space)
                .build();
        document = documentRepository.save(document);

        // 3. Process PDF pages text extraction
        List<DocumentPage> pages = new ArrayList<>();
        try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
            int pageCount = pdfDocument.getNumberOfPages();
            
            PDFTextStripper textStripper = new PDFTextStripper();
            for (int i = 1; i <= pageCount; i++) {
                textStripper.setStartPage(i);
                textStripper.setEndPage(i);
                String pageContent = textStripper.getText(pdfDocument);
                
                DocumentPage page = DocumentPage.builder()
                        .document(document)
                        .pageNumber(i)
                        .content(pageContent != null ? pageContent.trim() : "")
                        .build();
                pages.add(page);
            }
        } catch (IOException e) {
            log.error("Error occurred while reading and parsing PDF: {}", filename, e);
            // Clean up uploaded storage file on error
            try {
                String key = storageUrl.substring(storageUrl.lastIndexOf("/") + 1);
                storageService.delete(key);
            } catch (Exception ex) {
                log.error("Failed to clean up uploaded file from storage after parser failure", ex);
            }
            throw new RuntimeException("Lỗi bóc tách nội dung PDF: " + e.getMessage(), e);
        }

        // 4. Save all extracted pages
        documentPageRepository.saveAll(pages);
        document.setPages(pages);

        // 5. Convert and return Response DTO
        return convertToDocumentResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocumentById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        List<DocumentPage> pages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(id);
        
        List<DocumentPageResponse> pageResponses = pages.stream()
                .map(page -> DocumentPageResponse.builder()
                        .id(page.getId())
                        .pageNumber(page.getPageNumber())
                        .content(page.getContent())
                        .build())
                .toList();

        return DocumentDetailResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .storageUrl(document.getStorageUrl())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .pages(pageResponses)
                .summary(document.getSummary())
                .flashcards(document.getFlashcards())
                .build();
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for deletion", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        // Extract file name from Supabase storage URL (the text after the last '/')
        String storageUrl = document.getStorageUrl();
        String fileName = storageUrl.substring(storageUrl.lastIndexOf("/") + 1);

        // 1. Delete file on Supabase Storage
        try {
            storageService.delete(fileName);
        } catch (Exception e) {
            log.error("Failed to delete file '{}' from Supabase storage during document deletion", fileName, e);
        }

        // 2. Delete document from database (cascade deletes all related pages)
        documentRepository.delete(document);
    }

    @Override
    @Transactional
    public DocumentDetailResponse generateStudyNotes(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for study notes generation", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        // Nếu đã có sẵn thì không cần sinh lại
        if (document.getSummary() != null && document.getFlashcards() != null) {
            return getDocumentById(id);
        }

        List<DocumentPage> pages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(id);
        if (pages.isEmpty()) {
            throw new RuntimeException("Tài liệu không có nội dung văn bản để phân tích.");
        }

        // Tạo context từ toàn bộ các trang tài liệu
        StringBuilder contextBuilder = new StringBuilder();
        for (DocumentPage page : pages) {
            contextBuilder.append("Trang ").append(page.getPageNumber()).append(":\n")
                    .append(page.getContent()).append("\n\n");
        }
        String context = contextBuilder.toString();

        GeminiStudyHelper helper = AiServices.builder(GeminiStudyHelper.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        try {
            String rawOutput = helper.generateStudyNotes(context);

            // Phân tách tóm tắt và flashcard
            String summary = "";
            String flashcards = "[]";

            int startSummaryIdx = rawOutput.indexOf("=== BẮT ĐẦU TÓM TẮT ===");
            int endSummaryIdx = rawOutput.indexOf("=== KẾT THÚC TÓM TẮT ===");
            int startFlashcardsIdx = rawOutput.indexOf("=== BẮT ĐẦU FLASHCARDS ===");
            int endFlashcardsIdx = rawOutput.indexOf("=== KẾT THÚC FLASHCARDS ===");

            if (startSummaryIdx != -1 && endSummaryIdx != -1) {
                summary = rawOutput.substring(startSummaryIdx + "=== BẮT ĐẦU TÓM TẮT ===".length(), endSummaryIdx).trim();
            } else {
                // Fallback nếu model không tuân thủ hoàn toàn định dạng phân tách
                summary = rawOutput;
            }

            if (startFlashcardsIdx != -1 && endFlashcardsIdx != -1) {
                flashcards = rawOutput.substring(startFlashcardsIdx + "=== BẮT ĐẦU FLASHCARDS ===".length(), endFlashcardsIdx).trim();
            }

            document.setSummary(summary);
            document.setFlashcards(flashcards);
            documentRepository.save(document);

        } catch (Exception e) {
            log.error("Failed to generate study notes using Gemini API", e);
            throw new RuntimeException("Lỗi sinh tóm tắt hoặc flashcard bằng AI: " + e.getMessage(), e);
        }

        return getDocumentById(id);
    }

    @Override
    @Transactional
    public DocumentResponse renameDocument(Long id, String newName) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for renaming", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        if (newName != null && !newName.toLowerCase().endsWith(".pdf")) {
            newName = newName + ".pdf";
        }

        document.setFileName(newName);
        document = documentRepository.save(document);
        return convertToDocumentResponse(document);
    }

    private DocumentResponse convertToDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .storageUrl(document.getStorageUrl())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
