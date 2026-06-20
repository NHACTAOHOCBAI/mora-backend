package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentPageResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.model.dto.response.DocumentImageDebugResponse;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.model.entity.Space;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.repository.ChatMessageRepository;
import com.mora.backend.service.DocumentService;
import com.mora.backend.service.StorageService;
import com.mora.backend.client.AiServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import com.mora.backend.util.ImageUtil;
import com.mora.backend.util.VectorGraphicsDetector;
import java.awt.image.BufferedImage;
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
    private final ChatMessageRepository chatMessageRepository;
    private final AiServiceClient aiServiceClient;


    @Override
    @Transactional
    public DocumentResponse uploadAndProcessDocument(MultipartFile file, Long spaceId, Integer vectorPathThreshold) {
        // Validation: Verify Space exists
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for document upload", spaceId);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean isPdf = Objects.equals(contentType, "application/pdf") || 
                        (filename != null && filename.toLowerCase().endsWith(".pdf"));
        boolean isImage = (contentType != null && contentType.startsWith("image/")) || 
                          (filename != null && (filename.toLowerCase().endsWith(".png") || 
                                                filename.toLowerCase().endsWith(".jpg") || 
                                                filename.toLowerCase().endsWith(".jpeg")));

        if (!isPdf && !isImage) {
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

        String fileType = "pdf";
        if (isImage) {
            if (filename != null && filename.contains(".")) {
                fileType = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            } else {
                fileType = "png";
            }
        }

        // 2. Save Document metadata first
        Document document = Document.builder()
                .fileName(filename)
                .fileType(fileType)
                .storageUrl(storageUrl)
                .space(space)
                .vectorPathThreshold(vectorPathThreshold != null ? vectorPathThreshold : 30)
                .build();
        document = documentRepository.save(document);

        List<DocumentPage> pages = new ArrayList<>();
        if (isPdf) {
            // 3. Process PDF pages text extraction and image detection
            try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
                int pageCount = pdfDocument.getNumberOfPages();
                
                // First pass: collect all Tier 1 accepted images
                java.util.List<ImageInfoHolder> allImages = new java.util.ArrayList<>();
                java.util.List<java.util.List<ImageInfoHolder>> pageImages = new java.util.ArrayList<>();
                for (int i = 0; i < pageCount; i++) {
                    pageImages.add(new java.util.ArrayList<>());
                }

                for (int i = 0; i < pageCount; i++) {
                    PDPage pdfPage = pdfDocument.getPage(i);
                    if (pdfPage.getResources() != null) {
                        for (org.apache.pdfbox.cos.COSName name : pdfPage.getResources().getXObjectNames()) {
                            if (pdfPage.getResources().isImageXObject(name)) {
                                org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = pdfPage.getResources().getXObject(name);
                                if (xobj instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                    org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image = 
                                        (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobj;
                                    
                                    ImageFilterResult tier1 = evaluateTier1(image.getWidth(), image.getHeight());
                                    if (tier1.accepted) {
                                        java.awt.image.BufferedImage bufferedImage = null;
                                        try {
                                            bufferedImage = image.getImage();
                                        } catch (Exception e) {
                                            log.warn("Failed to get buffered image: {}", e.getMessage());
                                        }
                                        if (bufferedImage != null) {
                                            long pHash = calculatePerceptualHash(bufferedImage);
                                            ImageInfoHolder holder = new ImageInfoHolder(image.getWidth(), image.getHeight(), pHash);
                                            allImages.add(holder);
                                            pageImages.get(i).add(holder);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Second pass: extract text and determine hasImage status based on uniqueness
                PDFTextStripper textStripper = new PDFTextStripper();
                for (int i = 1; i <= pageCount; i++) {
                    textStripper.setStartPage(i);
                    textStripper.setEndPage(i);
                    String pageContent = textStripper.getText(pdfDocument);
                    
                    boolean hasImg = false;
                    for (ImageInfoHolder holder : pageImages.get(i - 1)) {
                        int occurrences = countSimilarOccurrences(holder, allImages);
                        if (occurrences == 1) {
                            hasImg = true;
                            break;
                        }
                    }
                    PDPage pdfPage = pdfDocument.getPage(i - 1);
                    VectorGraphicsDetector detector = new VectorGraphicsDetector(document.getVectorPathThreshold());
                    if (detector.detect(pdfPage, i)) {
                        hasImg = true;
                    }

                    DocumentPage page = DocumentPage.builder()
                            .document(document)
                            .pageNumber(i)
                            .content(pageContent != null ? pageContent.trim() : "")
                            .hasImage(hasImg)
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
        } else {
            // For images, we create a single page that is flagged as having an image
            DocumentPage page = DocumentPage.builder()
                    .document(document)
                    .pageNumber(1)
                    .content("")
                    .hasImage(true)
                    .build();
            pages.add(page);
        }

        // 4. Save all extracted pages
        documentPageRepository.saveAll(pages);
        document.setPages(pages);

        // 5. Convert and return Response DTO
        return convertToDocumentResponse(document);
    }

    private static class ImageFilterResult {
        final boolean accepted;
        final String reason;

        ImageFilterResult(boolean accepted, String reason) {
            this.accepted = accepted;
            this.reason = reason;
        }
    }

    private ImageFilterResult evaluateTier1(int width, int height) {
        if (width <= 200 || height <= 200) {
            return new ImageFilterResult(false, "Kích thước quá nhỏ (<= 200px)");
        }
        double ratio = (double) width / height;
        if (ratio > 3.0 || ratio < 0.33) {
            return new ImageFilterResult(false, String.format("Tỉ lệ khung hình dị (rộng/cao = %.2f)", ratio));
        }
        return new ImageFilterResult(true, null);
    }

    private static class ImageInfoHolder {
        final int width;
        final int height;
        final long pHash;

        ImageInfoHolder(int width, int height, long pHash) {
            this.width = width;
            this.height = height;
            this.pHash = pHash;
        }
    }

    private long calculatePerceptualHash(java.awt.image.BufferedImage img) {
        try {
            java.awt.Image tmp = img.getScaledInstance(8, 8, java.awt.Image.SCALE_FAST);
            java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
            java.awt.Graphics g = resized.getGraphics();
            g.drawImage(tmp, 0, 0, null);
            g.dispose();
            
            java.awt.image.Raster raster = resized.getRaster();
            byte[] pixels = ((java.awt.image.DataBufferByte) raster.getDataBuffer()).getData();
            
            int sum = 0;
            for (byte b : pixels) {
                sum += (b & 0xFF);
            }
            int avg = sum / 64;
            
            long hash = 0;
            for (int i = 0; i < 64; i++) {
                if ((pixels[i] & 0xFF) >= avg) {
                    hash |= (1L << i);
                }
            }
            return hash;
        } catch (Exception e) {
            log.warn("Failed to calculate perceptual hash: {}", e.getMessage());
            return 0L;
        }
    }

    private boolean isVisuallySimilar(int w1, int h1, long hash1, int w2, int h2, long hash2) {
        if (w1 != w2 || h1 != h2) {
            return false;
        }
        int distance = Long.bitCount(hash1 ^ hash2);
        return distance <= 10;
    }

    private int countSimilarOccurrences(ImageInfoHolder target, java.util.List<ImageInfoHolder> allImages) {
        int count = 0;
        for (ImageInfoHolder other : allImages) {
            if (isVisuallySimilar(target.width, target.height, target.pHash, other.width, other.height, other.pHash)) {
                count++;
            }
        }
        return count;
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
                        .hasImage(page.getHasImage())
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
                .vectorPathThreshold(document.getVectorPathThreshold() != null ? document.getVectorPathThreshold() : 30)
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

        // 2. Delete associated chat messages
        chatMessageRepository.deleteByDocumentId(id);

        // 3. Delete document from database (cascade deletes all related pages)
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

        try {
            AiServiceClient.StudyNotesResponse response = aiServiceClient.generateStudyNotes(context);

            String summary = response.summary != null ? response.summary.trim() : "";
            
            // Serialize list of flashcards to JSON string matching original format
            ObjectMapper objectMapper = new ObjectMapper();
            String flashcards = "[]";
            if (response.flashcards != null) {
                flashcards = objectMapper.writeValueAsString(response.flashcards);
            }

            document.setSummary(summary);
            document.setFlashcards(flashcards);
            documentRepository.save(document);

        } catch (Exception e) {
            log.error("Failed to generate study notes using Python AI service", e);
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
                .pagesWithImages(document.getPages() != null ? document.getPages().stream()
                        .filter(p -> Boolean.TRUE.equals(p.getHasImage()))
                        .map(DocumentPage::getPageNumber)
                        .sorted()
                        .toList() : List.of())
                .vectorPathThreshold(document.getVectorPathThreshold() != null ? document.getVectorPathThreshold() : 30)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderPageImage(Long documentId, int pageNumber) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for rendering", documentId);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        boolean isPdf = "pdf".equalsIgnoreCase(document.getFileType());

        if (!isPdf) {
            // If the document is an image, download and optimize it directly
            try {
                byte[] rawImage = downloadFile(document.getStorageUrl());
                return ImageUtil.resizeAndCompress(rawImage);
            } catch (IOException e) {
                log.error("Failed to download image file from storage: {}", document.getStorageUrl(), e);
                throw new RuntimeException("Không thể tải hình ảnh từ Storage: " + e.getMessage());
            }
        }

        try {
            java.io.File cachedFile = getCachedPdfFile(documentId, document.getStorageUrl());
            try (PDDocument pdfDocument = Loader.loadPDF(cachedFile)) {
                if (pageNumber < 1 || pageNumber > pdfDocument.getNumberOfPages()) {
                    throw new IllegalArgumentException("Số trang không hợp lệ: " + pageNumber);
                }
                PDFRenderer pdfRenderer = new PDFRenderer(pdfDocument);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(pageNumber - 1, 150);
                return ImageUtil.resizeAndCompress(bim);
            }
        } catch (IOException e) {
            log.error("Failed to render PDF page to image for document ID: {}, page: {}", documentId, pageNumber, e);
            throw new RuntimeException("Lỗi kết xuất trang PDF sang hình ảnh: " + e.getMessage(), e);
        }
    }

    private byte[] downloadFile(String urlString) throws IOException {
        try (java.io.InputStream in = java.net.URI.create(urlString).toURL().openStream()) {
            return in.readAllBytes();
        }
    }

    private java.io.File getCachedPdfFile(Long documentId, String storageUrl) throws IOException {
        java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"), "mora-pdf-cache");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        java.io.File cachedFile = new java.io.File(tempDir, documentId + ".pdf");
        if (!cachedFile.exists()) {
            log.info("Downloading PDF for document ID {} to local cache...", documentId);
            byte[] pdfBytes = downloadFile(storageUrl);
            java.nio.file.Files.write(cachedFile.toPath(), pdfBytes);
        }
        return cachedFile;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentImageDebugResponse> debugDocumentImages(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for image debugging", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        List<DocumentImageDebugResponse> debugList = new ArrayList<>();
        if (!"pdf".equalsIgnoreCase(document.getFileType())) {
            // Non-PDF is usually a single image
            DocumentImageDebugResponse.ImageInfo imgInfo = DocumentImageDebugResponse.ImageInfo.builder()
                    .name("Original Image")
                    .type(document.getFileType())
                    .width(0) // Unknown until decoded, but we can set 0
                    .height(0)
                    .accepted(true)
                    .filterReason(null)
                    .build();
            debugList.add(DocumentImageDebugResponse.builder()
                    .pageNumber(1)
                    .images(List.of(imgInfo))
                    .build());
            return debugList;
        }

        try {
            java.io.File cachedFile = getCachedPdfFile(id, document.getStorageUrl());
            try (PDDocument pdfDocument = Loader.loadPDF(cachedFile)) {
                int pageCount = pdfDocument.getNumberOfPages();
                
                // First pass: collect all Tier 1 accepted images with perceptual hashes
                java.util.List<ImageInfoHolder> allImages = new java.util.ArrayList<>();
                for (int i = 0; i < pageCount; i++) {
                    PDPage pdfPage = pdfDocument.getPage(i);
                    if (pdfPage.getResources() != null) {
                        for (org.apache.pdfbox.cos.COSName name : pdfPage.getResources().getXObjectNames()) {
                            if (pdfPage.getResources().isImageXObject(name)) {
                                org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = pdfPage.getResources().getXObject(name);
                                if (xobj instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                    org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image = 
                                        (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobj;
                                    ImageFilterResult tier1 = evaluateTier1(image.getWidth(), image.getHeight());
                                    if (tier1.accepted) {
                                        java.awt.image.BufferedImage bufferedImage = null;
                                        try {
                                            bufferedImage = image.getImage();
                                        } catch (Exception e) {
                                            log.warn("Failed to get buffered image: {}", e.getMessage());
                                        }
                                        if (bufferedImage != null) {
                                            long pHash = calculatePerceptualHash(bufferedImage);
                                            allImages.add(new ImageInfoHolder(image.getWidth(), image.getHeight(), pHash));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Second pass: build the debug response list
                List<DocumentPage> dbPages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(id);
                for (int i = 1; i <= pageCount; i++) {
                    PDPage pdfPage = pdfDocument.getPage(i - 1);
                    List<DocumentImageDebugResponse.ImageInfo> imagesOnPage = new ArrayList<>();
                    if (pdfPage.getResources() != null) {
                        for (org.apache.pdfbox.cos.COSName name : pdfPage.getResources().getXObjectNames()) {
                            org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = pdfPage.getResources().getXObject(name);
                            String xObjectType = xobj != null ? xobj.getClass().getSimpleName() : "Unknown";
                            
                            int width = 0;
                            int height = 0;
                            boolean accepted = false;
                            String filterReason = null;

                            if (xobj instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image = 
                                    (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobj;
                                width = image.getWidth();
                                height = image.getHeight();
                                
                                ImageFilterResult tier1 = evaluateTier1(width, height);
                                if (!tier1.accepted) {
                                    accepted = false;
                                    filterReason = tier1.reason;
                                } else {
                                    java.awt.image.BufferedImage bufferedImage = null;
                                    try {
                                        bufferedImage = image.getImage();
                                    } catch (Exception e) {
                                        log.warn("Failed to get buffered image: {}", e.getMessage());
                                    }
                                    if (bufferedImage != null) {
                                        long pHash = calculatePerceptualHash(bufferedImage);
                                        ImageInfoHolder target = new ImageInfoHolder(width, height, pHash);
                                        int count = countSimilarOccurrences(target, allImages);
                                        log.info("[DEBUG-IMAGE] Page: {}, Name: {}, Dim: {}x{}, Hash: {}, Occurrences: {}", 
                                                 i, name.getName(), width, height, pHash, count);
                                        if (count > 1) {
                                            accepted = false;
                                            filterReason = "Trùng lặp ở Tầng 2 (xuất hiện " + count + " lần)";
                                        } else {
                                            accepted = true;
                                            filterReason = null;
                                        }
                                    } else {
                                        accepted = false;
                                        filterReason = "Không thể đọc dữ liệu hình ảnh (BufferedImage null)";
                                    }
                                }
                            } else {
                                filterReason = "Không phải đối tượng hình ảnh (XObject)";
                            }

                            imagesOnPage.add(DocumentImageDebugResponse.ImageInfo.builder()
                                    .name(name.getName())
                                    .type(xObjectType)
                                    .width(width)
                                    .height(height)
                                    .accepted(accepted)
                                    .filterReason(filterReason)
                                    .build());
                        }
                    }
                    
                    VectorGraphicsDetector detector = new VectorGraphicsDetector(document.getVectorPathThreshold());
                    boolean hasVector = detector.detect(pdfPage, i);
                    int vectorPathCount = detector.getPathCount();
                    if (hasVector) {
                        imagesOnPage.add(DocumentImageDebugResponse.ImageInfo.builder()
                                .name("Sơ đồ Vector")
                                .type("VectorGraphics")
                                .width(0)
                                .height(0)
                                .accepted(true)
                                .filterReason(null)
                                .build());
                    }

                    String pageContent = "";
                    if (i - 1 < dbPages.size()) {
                        pageContent = dbPages.get(i - 1).getContent();
                    }
                    debugList.add(DocumentImageDebugResponse.builder()
                            .pageNumber(i)
                            .pageContent(pageContent)
                            .images(imagesOnPage)
                            .vectorPathCount(vectorPathCount)
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Failed to extract image debug details for document ID: {}", id, e);
            throw new RuntimeException("Lỗi bóc tách ảnh để debug: " + e.getMessage(), e);
        }

        return debugList;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] extractImageResource(Long documentId, int pageNumber, String imageName) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for image extraction", documentId);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        try {
            java.io.File cachedFile = getCachedPdfFile(documentId, document.getStorageUrl());
            try (PDDocument pdfDocument = Loader.loadPDF(cachedFile)) {
                if (pageNumber < 1 || pageNumber > pdfDocument.getNumberOfPages()) {
                    throw new IllegalArgumentException("Số trang không hợp lệ: " + pageNumber);
                }
                PDPage pdfPage = pdfDocument.getPage(pageNumber - 1);
                if (pdfPage.getResources() != null) {
                    org.apache.pdfbox.cos.COSName cosName = org.apache.pdfbox.cos.COSName.getPDFName(imageName);
                    org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = pdfPage.getResources().getXObject(cosName);
                    if (xobj instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image = 
                            (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobj;
                        BufferedImage bufferedImage = image.getImage();
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(bufferedImage, "png", baos);
                        return baos.toByteArray();
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to extract image resource '{}' from document ID: {}, page: {}", imageName, documentId, pageNumber, e);
            throw new RuntimeException("Lỗi trích xuất tài nguyên hình ảnh: " + e.getMessage(), e);
        }
        throw new RuntimeException("Không tìm thấy hình ảnh này trong tài liệu.");
    }

    @Override
    @Transactional
    public DocumentResponse updateVectorPathThreshold(Long id, Integer threshold) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for updating threshold", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        document.setVectorPathThreshold(threshold != null ? threshold : 30);
        
        // Re-process the document pages to re-detect vector graphics
        if ("pdf".equalsIgnoreCase(document.getFileType())) {
            try {
                java.io.File cachedFile = getCachedPdfFile(id, document.getStorageUrl());
                try (PDDocument pdfDocument = Loader.loadPDF(cachedFile)) {
                    int pageCount = pdfDocument.getNumberOfPages();
                    List<DocumentPage> pages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(id);
                    
                    // Re-calculate image status matching the evaluated images (Tier 1 uniqueness + vector graphics threshold)
                    java.util.List<ImageInfoHolder> allImages = new java.util.ArrayList<>();
                    java.util.List<java.util.List<ImageInfoHolder>> pageImages = new java.util.ArrayList<>();
                    for (int i = 0; i < pageCount; i++) {
                        pageImages.add(new java.util.ArrayList<>());
                    }

                    for (int i = 0; i < pageCount; i++) {
                        PDPage pdfPage = pdfDocument.getPage(i);
                        if (pdfPage.getResources() != null) {
                            for (org.apache.pdfbox.cos.COSName name : pdfPage.getResources().getXObjectNames()) {
                                if (pdfPage.getResources().isImageXObject(name)) {
                                    org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = pdfPage.getResources().getXObject(name);
                                    if (xobj instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image = 
                                            (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobj;
                                        ImageFilterResult tier1 = evaluateTier1(image.getWidth(), image.getHeight());
                                        if (tier1.accepted) {
                                            java.awt.image.BufferedImage bufferedImage = null;
                                            try {
                                                bufferedImage = image.getImage();
                                            } catch (Exception e) {
                                                log.warn("Failed to get buffered image: {}", e.getMessage());
                                            }
                                            if (bufferedImage != null) {
                                                long pHash = calculatePerceptualHash(bufferedImage);
                                                ImageInfoHolder holder = new ImageInfoHolder(image.getWidth(), image.getHeight(), pHash);
                                                allImages.add(holder);
                                                pageImages.get(i).add(holder);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    for (int i = 1; i <= pageCount; i++) {
                        boolean hasImg = false;
                        if (i - 1 < pageImages.size()) {
                            for (ImageInfoHolder holder : pageImages.get(i - 1)) {
                                int occurrences = countSimilarOccurrences(holder, allImages);
                                if (occurrences == 1) {
                                    hasImg = true;
                                    break;
                                }
                            }
                        }
                        PDPage pdfPage = pdfDocument.getPage(i - 1);
                        VectorGraphicsDetector detector = new VectorGraphicsDetector(document.getVectorPathThreshold());
                        if (detector.detect(pdfPage, i)) {
                            hasImg = true;
                        }

                        if (i - 1 < pages.size()) {
                            DocumentPage page = pages.get(i - 1);
                            page.setHasImage(hasImg);
                        }
                    }
                    documentPageRepository.saveAll(pages);
                }
            } catch (IOException e) {
                log.error("Failed to re-process document pages for threshold update", e);
                throw new RuntimeException("Lỗi xử lý tài liệu khi cập nhật ngưỡng: " + e.getMessage(), e);
            }
        }
        
        document = documentRepository.save(document);
        return convertToDocumentResponse(document);
    }
}
