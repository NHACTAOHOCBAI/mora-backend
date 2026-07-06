# 🌟 Mora Backend - Nền tảng Mạng xã hội Học tập Tích hợp AI

Mora Backend là dịch vụ máy chủ được phát triển trên nền tảng **Java Spring Boot**, đóng vai trò xử lý logic nghiệp vụ, quản lý cơ sở dữ liệu và tích hợp lưu trữ đám mây cho dự án **Mora - AI-Powered Social Learning Network** (Mạng xã hội học tập nhóm tích hợp AI thế hệ mới với cơ chế Source-Grounded AI tương tự NotebookLM).

Dự án hiện tại hỗ trợ các tính năng cốt lõi cho **Giai đoạn 1**: Pipeline xử lý tài liệu, bóc tách nội dung PDF theo trang, tích hợp hệ thống lưu trữ Cloud Storage (Supabase), cơ sở dữ liệu PostgreSQL, và kết nối tích hợp mô hình AI Gemini thông qua Python microservice `mora-ai` hỗ trợ hỏi đáp kèm định vị nguồn trích dẫn và lưu trữ lịch sử cuộc trò chuyện.

---

## 🛠️ Công nghệ Sử dụng (Tech Stack)

*   **Java 26** - Phiên bản Java LTS mới nhất với các tối ưu hóa hiệu năng vượt trội.
*   **Spring Boot 4.1.0** - Framework chính quản lý API, Dependency Injection, Validation.
*   **Spring Data JPA / Hibernate** - Tương tác và ánh xạ cơ sở dữ liệu quan hệ.
*   **PostgreSQL 16** - Hệ quản trị cơ sở dữ liệu quan hệ chính.
*   **Supabase Storage** - Giải pháp lưu trữ Cloud Object Storage quản lý tệp tin tài liệu gốc.
*   **Apache PDFBox 3.0.3** - Thư viện bóc tách và phân tích dữ liệu văn bản từ file PDF theo trang.
*   **RestTemplate** - HTTP Client trong Spring Boot dùng để kết nối với Python AI Service (`mora-ai`).
*   **Lombok** - Tự sinh code Boilerplate (Constructor, Getter/Setter, Builder, Logging).
*   **Springdoc OpenAPI v2.8.5** - Tự động sinh tài liệu API (Swagger UI).

---

## 📂 Cấu trúc Thư mục (Package Structure)

Dự án tuân thủ mô hình **Package-by-Layer** kết hợp phân tách logic nghiệp vụ theo quy chuẩn phát triển:

```text
com.mora.backend
├── client/             # Các Client gọi dịch vụ ngoài (AiServiceClient kết nối tới mora-ai)
├── config/             # Cấu hình Spring Boot (CORS, OpenApi, Jackson, JPA...)
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
    *   **Phát hiện Vector Graphics và Image:** Nhận diện các trang chứa sơ đồ vector thông qua bộ đếm nét vẽ và so sánh với ngưỡng `vectorPathThreshold` cấu hình trên từng tài liệu (mặc định là 30, tùy chỉnh linh hoạt từ 5 đến 200).
2.  **Tích hợp AI Engine (Python microservice `mora-ai` & Gemini SDK):**
    *   **Hỏi đáp Source-Grounded (RAG):** Gửi yêu cầu qua `AiServiceClient` sang Python Server để hỏi đáp dựa trên ngữ cảnh tài liệu (độc lập hoặc toàn bộ Space).
    *   **Trình bày So sánh Dạng Bảng (Markdown Table Comparisons):** Tự động sinh nội dung so sánh, phân biệt và đối chiếu dưới định dạng bảng Markdown có chèn ký tự xuống dòng thực tế ở cuối mỗi hàng khi người dùng hỏi dạng so sánh.
    *   **Trích dẫn trang (Citations):** Gemini trả về danh sách trích dẫn (quote gốc trong file PDF, số trang, mã tài liệu) qua cấu trúc JSON chuẩn.
    *   **Rút gọn câu hỏi (Condense Question):** Tự động gom lịch sử trò chuyện và câu hỏi mới thành một câu độc lập trên Python Server trước khi gửi cho LLM.
    *   **Đọc hiểu hình ảnh (Multimodal Vision Engine):** Tự động phát hiện các trang PDF chứa ảnh/sơ đồ vector, kết xuất thành ảnh ảo JPEG rồi gửi dữ liệu Base64 qua Python Server để Gemini xử lý dạng binary/inlineData.
    *   **Trình gỡ lỗi Prompt (Prompt Debugger):** Lưu vết toàn bộ nội dung prompt chính xác gửi đi vào cơ sở dữ liệu (`prompt_sent`). Hỗ trợ hiển thị trực quan prompt gốc kèm hình ảnh trong hộp thoại trên Frontend.
    *   **Công cụ học tập thông minh (Study Helper):** Tạo bản Tóm tắt (Summary) học thuật và bộ flashcards thông qua Python AI Service.
    *   **Xử lý lưu trữ bền bỉ (Robust Delete):** Cơ chế xóa file thông minh, tự động dọn dẹp sạch sẽ tài liệu và các trang liên quan trong cơ sở dữ liệu.
3.  **Bảo mật & Phân quyền (Security & JWT):**
    *   Tích hợp **Spring Security** với cơ chế xác thực không lưu trạng thái (Stateless) sử dụng **JWT Token**.
    *   Phân chia vai trò rõ ràng: Người dùng thường (`ROLE_USER`) và Quản trị viên (`ROLE_ADMIN`).
    *   Khởi tạo tự động tài khoản Admin mặc định thông qua database seeder (`DatabaseSeeder.java`).
4.  **Đánh Giá Chất Lượng RAG (Ragas Benchmark):**
    *   **Golden Dataset:** Quản lý tập câu hỏi chuẩn gồm các câu hỏi và đáp án mẫu (Ground Truth).
    *   **Đánh giá tự động bằng Ragas:** Chạy đánh giá RAG dựa trên các chỉ số nâng cao (Faithfulness, Answer Relevance, Context Precision, Context Recall) bằng cách phối hợp với AI Service.
    *   **So sánh hiệu năng:** Giao diện so sánh trực quan hiệu năng giữa các lần chạy thử nghiệm khác nhau dưới dạng biểu đồ cột và bảng so sánh ngữ cảnh/câu trả lời chi tiết cho từng câu hỏi.

---

## 📋 Tài liệu API (API Documents)

Sau khi khởi chạy ứng dụng thành công, tài liệu Swagger UI sẽ khả dụng tại:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

### Các Endpoint chính:
*   `POST /api/auth/register` - Đăng ký tài khoản mới.
*   `POST /api/auth/login` - Đăng nhập hệ thống nhận JWT Token.
*   `GET /api/users/me` - Lấy thông tin tài khoản hiện tại.
*   `PUT /api/users/profile` - Cập nhật thông tin cá nhân.
*   `PUT /api/users/profile/password` - Thay đổi mật khẩu.
*   `POST /api/documents/upload` - Tải lên file PDF và bóc tách nội dung từng trang.
*   `GET /api/documents/{id}` - Lấy thông tin chi tiết tài liệu kèm các trang.
*   `DELETE /api/documents/{id}` - Xóa tài liệu khỏi hệ thống.
*   `POST /api/documents/{id}/generate-study-notes` - Sinh tóm tắt & Flashcards cho tài liệu bằng AI.
*   `PATCH /api/documents/{id}/rename` - Đổi tên tài liệu.
*   `GET /api/documents/{id}/debug-images` - Debug xem danh sách hình ảnh trích xuất của từng trang.
*   `GET /api/documents/{id}/pages/{pageNumber}/images/{imageName}` - Tải/trích xuất ảnh gốc từ trang PDF dưới dạng PNG.
*   `PATCH /api/documents/{id}/threshold` - Cập nhật cấu hình ngưỡng Vector Path và tự động quét lại toàn bộ hình ảnh trong tài liệu.
*   `POST /api/chat` - Hỏi đáp với tài liệu cụ thể.
*   `POST /api/chat/space` - Hỏi đáp trên toàn bộ Không gian học tập (nhiều tài liệu).
*   `GET /api/chat/document/{documentId}` - Lấy lịch sử chat của tài liệu.
*   `GET /api/chat/space/{spaceId}` - Lấy lịch sử chat của Space.
*   `DELETE /api/chat/document/{documentId}` - Xóa lịch sử chat của tài liệu.
*   `DELETE /api/chat/space/{spaceId}` - Xóa lịch sử chat của Space.
*   `POST /api/admin/benchmarks/questions` - Tạo câu hỏi kiểm thử mới.
*   `GET /api/admin/benchmarks/questions` - Danh sách câu hỏi kiểm thử.
*   `PUT /api/admin/benchmarks/questions/{id}` - Sửa câu hỏi kiểm thử.
*   `DELETE /api/admin/benchmarks/questions/{id}` - Xóa câu hỏi kiểm thử.
*   `POST /api/admin/benchmarks/run` - Thực thi lượt chạy đánh giá Ragas Benchmark.
*   `GET /api/admin/benchmarks/runs` - Lấy danh sách lịch sử các lượt chạy.
*   `GET /api/admin/benchmarks/runs/{id}` - Chi tiết lượt chạy kèm điểm số của từng câu hỏi.
*   `DELETE /api/admin/benchmarks/runs/{id}` - Xóa lượt chạy đánh giá.

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
