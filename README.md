# 🌟 Mora Backend - Nền tảng Mạng xã hội Học tập Tích hợp AI

Mora Backend là dịch vụ máy chủ được phát triển trên nền tảng **Java Spring Boot**, đóng vai trò xử lý logic nghiệp vụ, quản lý cơ sở dữ liệu và tích hợp lưu trữ đám mây cho dự án **Mora - AI-Powered Social Learning Network** (Mạng xã hội học tập nhóm tích hợp AI thế hệ mới với cơ chế Source-Grounded AI tương tự NotebookLM).

Dự án hiện tại hỗ trợ các tính năng cốt lõi cho **Giai đoạn 1**: Pipeline xử lý tài liệu, bóc tách nội dung PDF theo trang hỗ trợ định vị nguồn trích dẫn trực quan, tích hợp hệ thống lưu trữ Cloud Storage (Supabase) và cơ sở dữ liệu PostgreSQL.

---

## 🛠️ Công nghệ Sử dụng (Tech Stack)

*   **Java 26** - Phiên bản Java LTS mới nhất với các tối ưu hóa hiệu năng vượt trội.
*   **Spring Boot 4.1.0** - Framework chính quản lý API, Dependency Injection, Validation.
*   **Spring Data JPA / Hibernate** - Tương tác và ánh xạ cơ sở dữ liệu quan hệ.
*   **PostgreSQL 16** - Hệ quản trị cơ sở dữ liệu quan hệ chính.
*   **Supabase Storage** - Giải pháp lưu trữ Cloud Object Storage quản lý tệp tin tài liệu gốc.
*   **Apache PDFBox 3.0.3** - Thư viện bóc tách và phân tích dữ liệu văn bản từ file PDF theo trang.
*   **Lombok** - Tự sinh code Boilerplate (Constructor, Getter/Setter, Builder, Logging).
*   **Springdoc OpenAPI v2.8.5** - Tự động sinh tài liệu API (Swagger UI).

---

## 📂 Cấu trúc Thư mục (Package Structure)

Dự án tuân thủ mô hình **Package-by-Layer** kết hợp phân tách logic nghiệp vụ theo quy chuẩn phát triển:

```text
com.mora.backend
├── config/             # Cấu hình Spring Boot (CORS, OpenApi, Jackson, JPA...)
├── controller/         # REST API Controllers (Nhận/trả dữ liệu và điều hướng, không xử lý logic)
├── exception/          # Quản lý và xử lý lỗi tập trung toàn cục (Global Exception Handler)
├── model/              # Quản lý cấu trúc dữ liệu
│   ├── entity/         # Đối tượng Entity ánh xạ trực tiếp xuống DB (Document, DocumentPage...)
│   └── dto/            # Data Transfer Object (DTO)
│       ├── request/    # DTO nhận từ REST Client
│       └── response/   # DTO trả về REST Client
├── repository/         # Tầng kết nối & truy vấn Cơ sở dữ liệu (Spring Data JPA)
├── service/            # Tầng xử lý nghiệp vụ chính (Business Logic)
│   ├── DocumentService.java
│   ├── StorageService.java
│   └── impl/           # Hiện thực chi tiết (DocumentServiceImpl, SupabaseStorageServiceImpl)
└── util/               # Các class tiện ích dùng chung (Date, String...)
```

---

## 🚀 Tính năng Hiện tại (Current Features)

1.  **Pipeline Xử lý PDF:**
    *   Tự động tải tài liệu gốc định dạng PDF lên Supabase Cloud Storage.
    *   Sử dụng **Apache PDFBox** để bóc tách nội dung văn bản (text extraction) độc lập theo từng trang (Page-by-page mapping).
    *   Lưu thông tin metadata của tài liệu và nội dung chi tiết từng trang vào PostgreSQL phục vụ cho các câu hỏi ngữ cảnh (Source-Grounded AI).
2.  **API Quản lý Tài liệu:**
    *   Tải lên tài liệu mới.
    *   Xem chi tiết nội dung tài liệu theo từng trang.
    *   Xóa tài liệu (Đồng thời xóa trên DB và Cloud Storage).

---

## 📋 Tài liệu API (API Documents)

Sau khi khởi chạy ứng dụng thành công, tài liệu Swagger UI sẽ khả dụng tại:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

### Các Endpoint chính:
*   `POST /api/documents/upload` - Tải lên file PDF và tự động bóc tách nội dung từng trang (`multipart/form-data`).
*   `GET /api/documents/{id}` - Lấy thông tin chi tiết tài liệu kèm nội dung văn bản các trang.
*   `DELETE /api/documents/{id}` - Xóa tài liệu khỏi hệ thống lưu trữ Cloud và Cơ sở dữ liệu.

---

## 🛠️ Hướng dẫn Cài đặt & Chạy ứng dụng

### 1. Yêu cầu Hệ thống
*   Đã cài đặt **Java JDK 26**.
*   Đã cài đặt **Maven 3.9+**.
*   Đã cài đặt **Docker** & **Docker Compose**.

### 2. Khởi chạy Database
Dự án sử dụng PostgreSQL chạy trong Docker container thông qua file `docker-compose.yml` ở cổng `5433`:

```bash
docker compose up -d
```

### 3. Cấu hình Ứng dụng
Xem hoặc chỉnh sửa các thông số kết nối Database và Cloud Storage tại [application.properties](file:///c:/Users/phucnd/Desktop/Mora/mora-backend/src/main/resources/application.properties):

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5433/mora_db
spring.datasource.username=postgres
spring.datasource.password=postgres

# Supabase Storage Configuration
supabase.url=https://<your-supabase-project>.supabase.co
supabase.key=<your-supabase-service-role-key>
supabase.bucket=mora-documents
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

Khi tham gia đóng góp mã nguồn cho dự án, vui lòng tuân thủ các nguyên tắc được định nghĩa chi tiết tại [rule.md](file:///c:/Users/phucnd/Desktop/Mora/mora-backend/rule.md) (hoặc xem tóm tắt bên dưới):
*   **Dependency Injection:** Tuyệt đối **KHÔNG** dùng `@Autowired` trực tiếp trên Field. Hãy dùng **Constructor Injection** (Khuyên dùng `@RequiredArgsConstructor` kết hợp thuộc tính `final`).
*   **Request/Response & DTO:** Controller **KHÔNG** nhận hoặc trả về trực tiếp các Entity. Phải thông qua các đối tượng DTO chuyên biệt.
*   **Transaction:** Đặt `@Transactional` ở tầng Service Implementation. Các phương thức đọc dữ liệu nên dùng `@Transactional(readOnly = true)`.
*   **Logging:** Dùng `@Slf4j` từ Lombok để ghi nhận nhật ký hệ thống.
