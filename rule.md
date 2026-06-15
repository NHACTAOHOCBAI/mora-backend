# MORA BACKEND - CODING STANDARDS & BEST PRACTICES

Tài liệu này định nghĩa cấu trúc dự án Spring Boot và các quy định viết code (Coding Standards / Best Practices) của dự án **Mora Backend**. Mọi lập trình viên và AI Agent khi tham gia đóng góp mã nguồn đều **bắt buộc** phải đọc và tuân thủ các quy tắc này.

---

## 1. Cấu trúc thư mục (Package Structure)

Dự án tuân thủ mô hình **Package-by-Layer** kết hợp phân tách logic nghiệp vụ rõ ràng:

```text
com.mora.backend
├── config/             # Cấu hình Spring Boot (Security, CORS, Jackson, JPA...)
├── controller/         # REST API Controllers (Chỉ điều hướng và nhận/trả dữ liệu, không xử lý logic)
├── service/            # Tầng xử lý nghiệp vụ (Business Logic)
│   ├── UserService.java    # Interface định nghĩa nghiệp vụ
│   └── impl/
│       └── UserServiceImpl.java # Triển khai chi tiết nghiệp vụ
├── repository/         # Kết nối cơ sở dữ liệu (Spring Data JPA, MyBatis...)
├── model/              # Quản lý cấu trúc dữ liệu
│   ├── entity/         # Đối tượng ánh xạ trực tiếp xuống Database (@Entity)
│   └── dto/            # Data Transfer Object (DTO)
│       ├── request/    # DTO nhận từ REST Client
│       └── response/   # DTO trả về REST Client
├── exception/          # Xử lý lỗi toàn cục (Global Exception Handler)
├── security/           # Cấu hình bảo mật, xử lý JWT Token, phân quyền
└── util/               # Các class tiện ích dùng chung (Date, String, Encryption...)
```

---

## 2. Quy tắc lập trình chi tiết (Coding Rules)

### 2.1. Dependency Injection (DI)
* **Quy tắc:** Tuyệt đối **KHÔNG** sử dụng `@Autowired` trực tiếp lên thuộc tính (Field Injection).
* **Giải pháp:** Sử dụng **Constructor Injection**. Nên dùng kết hợp `@RequiredArgsConstructor` của Lombok để tự sinh Constructor cho các thuộc tính khai báo là `final`.

```java
// ĐÚNG
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService; // Được tự động inject qua constructor của Lombok
}

// SAI
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService; // Field Injection - Khó viết Unit Test độc lập
}
```

### 2.2. Xử lý Dữ liệu đầu vào và đầu ra (Request/Response & DTO)
* **Quy tắc:**
  1. Không bao giờ trả về trực tiếp các class `@Entity` (trong package `model/entity`) cho Client ở tầng Controller.
  2. Phải chuyển đổi Entity sang DTO tương ứng (trong package `model/dto/response`) trước khi trả về.
  3. Mọi Request Body truyền lên đều phải được đóng gói vào các class Request DTO (trong package `model/dto/request`).
* **Validation:** Sử dụng `jakarta.validation` để kiểm tra tính hợp lệ của dữ liệu đầu vào. Đặt `@Valid` trước `@RequestBody`.

```java
// Ví dụ Request DTO hợp lệ
@Data
public class UserRegisterRequest {
    @NotBlank(message = "Username không được để trống")
    @Size(min = 4, max = 20, message = "Username phải từ 4 đến 20 ký tự")
    private String username;

    @Email(message = "Email không đúng định dạng")
    @NotBlank(message = "Email không được để trống")
    private String email;
}
```

### 2.3. Xử lý Lỗi & Exception (Exception Handling)
* **Quy tắc:**
  1. Sử dụng hệ thống Exception tập trung bằng `@RestControllerAdvice` kết hợp với `@ExceptionHandler`.
  2. Không bắt Exception và trả về một Map hoặc String tự do.
  3. Định nghĩa định dạng lỗi đầu ra thống nhất (ví dụ: `ErrorCode`, `Message`, `Timestamp`).
  4. Ném ra các Exception cụ thể (`UserNotFoundException`, `InvalidCredentialException`...) thay vì dùng chung chung `RuntimeException`.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
}
```

### 2.4. Quản lý Giao dịch (Transaction Management)
* **Quy tắc:**
  1. Sử dụng `@Transactional` của Spring Framework (`org.springframework.transaction.annotation.Transactional`) thay vì thư viện ngoài.
  2. Đặt `@Transactional` ở tầng **Service implementation** (ServiceImpl), tuyệt đối không đặt ở Controller.
  3. Đối với các tác vụ chỉ đọc (Read-Only), luôn đánh dấu `@Transactional(readOnly = true)` để tối ưu hóa hiệu năng kết nối DB.

### 2.5. Ghi Log (Logging)
* **Quy tắc:**
  1. Sử dụng `@Slf4j` của Lombok để ghi log. Không sử dụng `System.out.println()` hay `System.err.println()`.
  2. Đặt mức độ log phù hợp:
     * `log.info(...)`: Tiến trình chạy bình thường (e.g. "User registered successfully with ID: {}").
     * `log.warn(...)`: Cảnh báo (e.g. "Login attempt failed for user: {}").
     * `log.error(...)`: Lỗi hệ thống nghiêm trọng (luôn kèm Stacktrace).
     * `log.debug(...)`: Các thông tin chi tiết phục vụ quá trình phát triển.

### 2.6. Thiết kế RESTful API
* Đặt tên endpoint sử dụng danh từ số nhiều (e.g. `/api/users`, `/api/orders`).
* Sử dụng đúng HTTP Method:
  * `GET`: Lấy thông tin.
  * `POST`: Tạo mới tài nguyên.
  * `PUT`: Cập nhật toàn bộ tài nguyên.
  * `PATCH`: Cập nhật một phần tài nguyên.
  * `DELETE`: Xóa tài nguyên.
* Trả về HTTP Status code chính xác (`200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `500 Internal Server Error`).
