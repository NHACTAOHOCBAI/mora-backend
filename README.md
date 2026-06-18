# 🌟 Mora Backend - Nền tảng Mạng xã hội Học tập Tích hợp AI

Mora Backend là dịch vụ máy chủ được phát triển trên nền tảng **Java Spring Boot**, đóng vai trò xử lý logic nghiệp vụ, quản lý cơ sở dữ liệu và tích hợp lưu trữ đám mây cho dự án **Mora - AI-Powered Social Learning Network** (Mạng xã hội học tập nhóm tích hợp AI thế hệ mới với cơ chế Source-Grounded AI tương tự NotebookLM).

Dự án hiện tại hỗ trợ các tính năng cốt lõi cho **Giai đoạn 1**: Pipeline xử lý tài liệu, bóc tách nội dung PDF theo trang, tích hợp hệ thống lưu trữ Cloud Storage (Supabase), cơ sở dữ liệu PostgreSQL, và tích hợp mô hình AI Gemini qua LangChain4j hỗ trợ hỏi đáp kèm định vị nguồn trích dẫn và lưu trữ lịch sử cuộc trò chuyện.

---

## 🛠️ Công nghệ Sử dụng (Tech Stack)

*   **Java 26** - Phiên bản Java LTS mới nhất với các tối ưu hóa hiệu năng vượt trội.
*   **Spring Boot 4.1.0** - Framework chính quản lý API, Dependency Injection, Validation.
*   **Spring Data JPA / Hibernate** - Tương tác và ánh xạ cơ sở dữ liệu quan hệ.
*   **PostgreSQL 16** - Hệ quản trị cơ sở dữ liệu quan hệ chính.
*   **Supabase Storage** - Giải pháp lưu trữ Cloud Object Storage quản lý tệp tin tài liệu gốc.
*   **Apache PDFBox 3.0.3** - Thư viện bóc tách và phân tích dữ liệu văn bản từ file PDF theo trang.
*   **LangChain4j 0.31.0** - Thư viện tích hợp LLM chính, kết nối với Google Gemini API, hỗ trợ AI Services, System/User Prompts và Structured Outputs (JSON Schema).
*   **Lombok** - Tự sinh code Boilerplate (Constructor, Getter/Setter, Builder, Logging).
*   **Springdoc OpenAPI v2.8.5** - Tự động sinh tài liệu API (Swagger UI).

---

## 📂 Cấu trúc Thư mục (Package Structure)

Dự án tuân thủ mô hình **Package-by-Layer** kết hợp phân tách logic nghiệp vụ theo quy chuẩn phát triển:

```text
com.mora.backend
├── config/             # Cấu hình Spring Boot (CORS, OpenApi, Jackson, JPA, Gemini...)
├── controller/         # REST API Controllers (Nhận/trả dữ liệu và điều hướng, không xử lý logic)
├── exception/          # Quản lý và xử lý lỗi tập trung toàn cục (Global Exception Handler)
├── model/              # Quản lý cấu trúc dữ liệu
│   ├── entity/         # Đối tượng Entity ánh xạ trực tiếp xuống DB (Document, DocumentPage, ChatMessage...)
│   └── dto/            # Data Transfer Object (DTO)
│       ├── request/    # DTO nhận từ REST Client
│       └── response/   # DTO trả về REST Client
├── repository/         # Tầng kết nối & truy vấn Cơ sở dữ liệu (Spring Data JPA)
├── service/            # Tầng xử lý nghiệp vụ chính (Business Logic)
│   ├── DocumentService.java
│   ├── StorageService.java
│   ├── ChatService.java
│   ├── SpaceService.java
│   └── impl/           # Hiện thực chi tiết (DocumentServiceImpl, ChatServiceImpl, SpaceServiceImpl...)
└── util/               # Các class tiện ích dùng chung
```

---

## 🚀 Tính năng Hiện tại (Current Features)

1.  **Pipeline Xử lý PDF:**
    *   Tải tài liệu gốc định dạng PDF lên Supabase Cloud Storage.
    *   Sử dụng **Apache PDFBox** để bóc tách nội dung văn bản (text extraction) độc lập theo từng trang (Page-by-page mapping).
    *   Lưu thông tin metadata của tài liệu và nội dung chi tiết từng trang vào PostgreSQL.
2.  **Tích hợp AI Engine (Gemini & LangChain4j):**
    *   **Hỏi đáp Source-Grounded (RAG):** Trả lời câu hỏi của người dùng dựa trên ngữ cảnh tài liệu (độc lập hoặc toàn bộ Space).
    *   **Trích dẫn trang (Citations):** Ép đầu ra cấu trúc để Gemini trả về danh sách trích dẫn (quote gốc trong file PDF, số trang, mã tài liệu).
    *   **Rút gọn câu hỏi (Condense Question):** Tự động gom lịch sử trò chuyện trong DB và câu hỏi mới của người dùng thành một câu độc lập trước khi gửi cho LLM.
    *   **Đọc hiểu hình ảnh (Multimodal Vision Engine):** Tự động phát hiện nhạy bén các trang tài liệu PDF chứa hình vẽ, sơ đồ vector hoặc ảnh (`getXObjectNames().iterator().hasNext()`), tự động kết xuất sang ảnh ảo JPEG tối ưu hóa kích thước và gửi kèm dưới dạng dữ liệu Multimodal (Base64) lên Gemini.
    *   **Trình gỡ lỗi Prompt (Prompt Debugger):** Lưu vết toàn bộ nội dung prompt chính xác gửi đi vào cơ sở dữ liệu (`prompt_sent`). Hỗ trợ người dùng nhấp đúp (Double-click) vào bong bóng tin nhắn AI trên giao diện React để hiển thị chi tiết prompt gốc trong hộp thoại Shadcn UI Dialog.
    *   **Công cụ học tập thông minh (Study Helper):** Tự động tạo bản Tóm tắt (Summary) học thuật và bộ câu hỏi ôn tập (Flashcards) dưới dạng JSON từ tài liệu.
    *   **Xử lý lưu trữ bền bỉ (Robust Delete):** Cơ chế xóa file thông minh, tự động bỏ qua lỗi và ghi nhận cảnh báo nếu file không còn trên Supabase Storage nhằm đảm bảo tài liệu và dữ liệu liên quan luôn được dọn dẹp sạch sẽ trong cơ sở dữ liệu PostgreSQL.
3.  **Lưu trữ Lịch sử Trò chuyện:**
    *   Tự động lưu lại các tin nhắn trao đổi (User & Assistant) vào DB PostgreSQL.
    *   Cung cấp các API REST để lấy lịch sử cuộc trò chuyện và dọn dẹp (xóa) lịch sử trò chuyện của từng tài liệu/Space.

---

## 📋 Tài liệu API (API Documents)

Sau khi khởi chạy ứng dụng thành công, tài liệu Swagger UI sẽ khả dụng tại:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

### Các Endpoint chính:
*   `POST /api/documents/upload` - Tải lên file PDF và bóc tách nội dung từng trang.
*   `GET /api/documents/{id}` - Lấy thông tin chi tiết tài liệu kèm các trang.
*   `DELETE /api/documents/{id}` - Xóa tài liệu khỏi hệ thống.
*   `POST /api/documents/{id}/generate-study-notes` - Sinh tóm tắt & Flashcards cho tài liệu bằng AI.
*   `POST /api/chat` - Hỏi đáp với tài liệu cụ thể.
*   `POST /api/chat/space` - Hỏi đáp trên toàn bộ Không gian học tập (nhiều tài liệu).
*   `GET /api/chat/document/{documentId}` - Lấy lịch sử chat của tài liệu.
*   `GET /api/chat/space/{spaceId}` - Lấy lịch sử chat của Space.
*   `DELETE /api/chat/document/{documentId}` - Xóa lịch sử chat của tài liệu.
*   `DELETE /api/chat/space/{spaceId}` - Xóa lịch sử chat của Space.

---

## 🛠️ Hướng dẫn Cài đặt & Chạy ứng dụng

### 1. Yêu cầu Hệ thống
*   Đã cài đặt **Java JDK 26**.
*   Đã cài đặt **Maven 3.9+**.
*   Đã cài đặt **Docker** & **Docker Compose**.

### 2. Khởi chạy Database & Công cụ quản trị (pgAdmin)
Dự án sử dụng PostgreSQL chạy trong Docker container thông qua file `docker-compose.yml` ở cổng `5433`, đi kèm công cụ quản trị **pgAdmin 4** ở cổng `5050`:

```bash
docker compose up -d
```

*   **PostgreSQL:** Hoạt động trên cổng `5433` (`localhost:5433`).
*   **pgAdmin 4:** Truy cập tại **[http://localhost:5050](http://localhost:5050)**.
    *   **Tài khoản đăng nhập:** Email `admin@mora.com` / Mật khẩu `admin`
    *   **Kết nối Database từ pgAdmin:** Sử dụng Host name `postgres`, Port `5432`, Maintenance database `mora_db`, Username `postgres`, Password `postgres`.

### 3. Cấu hình Ứng dụng
Tạo file `.env` ở thư mục gốc của backend (hoặc cấu hình các biến môi trường trực tiếp) dựa trên file `.env.example`:

```properties
DB_URL=jdbc:postgresql://localhost:5433/mora_db
DB_USERNAME=postgres
DB_PASSWORD=postgres
SUPABASE_URL=https://your-supabase-project.supabase.co
SUPABASE_KEY=your-supabase-service-role-key
SUPABASE_BUCKET=mora-documents
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL_NAME=gemini-1.5-flash
GEMINI_TEMPERATURE=0.0
```

### 4. Khởi chạy Ứng dụng
Sử dụng Maven Wrapper có sẵn để chạy ứng dụng:

*   **Windows (Command Prompt/PowerShell):**
    ```bash
    .\mvnw.cmd spring-boot:run
    ```
*   **Linux/macOS:**
    ```bash
    chmod +x mvnw
    ./mvnw spring-boot:run
    ```

---

## ✍️ Quy tắc Code (Coding Rules)

Khi tham gia đóng góp mã nguồn cho dự án, vui lòng tuân thủ các nguyên tắc được định nghĩa chi tiết tại [rule.md](file:///c:/Users/phucnd/Desktop/Mora/mora-backend/rule.md):
*   **Dependency Injection:** Tuyệt đối **KHÔNG** dùng `@Autowired` trực tiếp trên Field. Hãy dùng **Constructor Injection** (Khuyên dùng `@RequiredArgsConstructor` kết hợp thuộc tính `final`).
*   **Request/Response & DTO:** Controller **KHÔNG** nhận hoặc trả về trực tiếp các Entity. Phải thông qua các đối tượng DTO chuyên biệt.
*   **Transaction:** Đặt `@Transactional` ở tầng Service Implementation. Các phương thức đọc dữ liệu nên dùng `@Transactional(readOnly = true)`.
*   **Logging:** Dùng `@Slf4j` từ Lombok để ghi nhận nhật ký hệ thống.
