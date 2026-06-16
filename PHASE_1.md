CHI TIẾT Ý TƯỞNG TRIỂN KHAI GIAI ĐOẠN 1 (CORE AI ENGINE)

Giai đoạn này tập trung hoàn toàn vào việc hiện thực hóa mô hình Source-Grounded AI thông qua 3 bước logic:

### 📌 Bước 1: Ý tưởng Pipeline xử lý tài liệu (Backend - Java Spring Boot)

- **Tư duy thiết kế Cơ sở dữ liệu:** Để hỗ trợ việc trích dẫn chính xác theo trang, dữ liệu văn bản không được lưu trữ thành một khối thô dài (raw text block) mà bắt buộc phải thực thể hóa và gắn chặt với chỉ số trang (`page_number`).
  - _Bảng lưu thông tin tổng quan (Documents):_ Lưu trữ định danh tài liệu, tên file gốc, định dạng (PDF/Docx) và đường dẫn liên kết đến phân vùng lưu trữ file vật lý (Object Storage).
  - _Bảng lưu cấu trúc trang (Document Pages):_ Có mối quan hệ Một-Nhiều với bảng tổng quan. Mỗi dòng trong bảng này chỉ đại diện cho duy nhất nội dung chữ của một trang cụ thể, đi kèm số trang tương ứng.
- **Ý tưởng xử lý trích xuất văn bản trong Spring Boot:** \* Khi người dùng upload file lên hệ thống, Backend tiếp nhận file dưới dạng luồng dữ liệu (Stream).
  - Sử dụng thư viện bóc tách văn bản (như Apache PDFBox) để đọc siêu dữ liệu (metadata) nhằm xác định tổng số trang của file.
  - Chạy một vòng lặp quét qua từng trang độc lập, trích xuất toàn bộ ký tự hiển thị trên trang đó thành một chuỗi văn bản sạch.
  - Lưu từng trang này thành các bản ghi riêng biệt vào Cơ sở dữ liệu. File gốc đồng thời được đẩy lên kho lưu trữ (như Supabase Storage) để chuẩn bị cho việc hiển thị trực quan ở Frontend.

### 📌 Bước 2: Ý tưởng tích hợp AI và Ép đầu ra cấu trúc (AI Engine)

- **Tư duy định dạng Ngữ cảnh đầu vào (Context Formatting):** Khi người dùng đặt câu hỏi, Spring Boot sẽ lấy toàn bộ các trang văn bản đã được bóc tách ở Bước 1 của tài liệu đó ra, nối chúng thành một chuỗi văn bản lớn theo cấu trúc "đánh dấu đường" có quy ước ranh giới rõ ràng:

  ```text
  --- BẮT ĐẦU FILE: ten_tai_lieu.pdf ---
  # TRANG 1
  [Nội dung chữ thuộc trang 1]

  # TRANG 2
  [Nội dung chữ thuộc trang 2]
  --- KẾT THÚC FILE: ten_tai_lieu.pdf ---
  ```

  Cách sắp xếp này giúp mô hình AI nhận diện được không gian và tọa độ vị trí của thông tin cực kỳ tốt khi nó duyệt qua cửa sổ ngữ cảnh khổng lồ của Gemini 1.5 Flash.

- **Ý tưởng thiết kế Kỷ luật Prompt & Ép xuất cấu trúc (JSON Schema):**
  - _Tham số kiểm soát:_ Cài đặt độ sáng tạo (`temperature`) của AI về mức bằng 0 để ép mô hình suy luận logic tối đa và loại bỏ tính sáng tạo tự do gây ra ảo tưởng (hallucination).
  - _System Prompt (Luật thép):_ Định nghĩa vai trò cho AI là một người trợ lý học thuật nghiêm khắc. Ra lệnh cho AI chỉ được phép dùng thông tin có trong ngữ cảnh được cung cấp để trả lời. Nếu câu hỏi nằm ngoài phạm vi tài liệu, AI bắt buộc phải trả về thông báo từ chối thay vì đoán mò.
  - _Cấu trúc đầu ra (JSON Schema):_ Tận dụng tính năng **Structured Outputs** của Gemini để ép mô hình trả về một cấu trúc JSON cố định gồm 3 phần: Trạng thái tìm thấy câu trả lời (`true`/`false`), Nội dung câu trả lời hoàn chỉnh, và Mảng chứa danh sách trích dẫn (mỗi phần tử trích dẫn bao gồm: cụm từ gốc nằm trong tài liệu và số trang chính xác chứa cụm từ đó).

### 📌 Bước 3: Ý tưởng giao diện chia đôi và Trích dẫn tương tác (Frontend - React)

- **Bố cục màn hình Split-Screen (TailwindCSS):** Thiết kế giao diện chia làm hai không gian độc lập chiếm trọn chiều cao màn hình trình duyệt nhằm mang lại trải nghiệm tối ưu nhất:
  - _Không gian bên trái (Khung hội thoại):_ Nơi người dùng nhập câu hỏi và hiển thị câu trả lời từ AI. Dưới mỗi câu trả lời, hệ thống sẽ duyệt qua mảng trích dẫn trong chuỗi JSON nhận được từ Backend để kết xuất thành các nút bấm tương tác nhỏ trực quan (Ví dụ: `📍 Trang 5`, `📍 Trang 12`).
  - _Không gian bên phải (Trình xem tài liệu):_ Sử dụng thư viện render file PDF trực tiếp trên trình duyệt (như `react-pdf`). Không gian này sẽ tải file PDF gốc từ kho lưu trữ đám mây về để hiển thị song song cho người dùng.
- **Cơ chế Trích dẫn tương tác (Interactive Citation):**
  - Hệ thống sử dụng một trạng thái chung (State) trong React để quản lý số trang hiện tại đang được hiển thị ở Trình xem PDF (ví dụ: `activePage`).
  - Khi người dùng đọc câu trả lời bên khung chat và click vào một nút trích dẫn (ví dụ: nút `📍 Trang 12`), một sự kiện (Event) sẽ được kích hoạt để cập nhật giá trị của `activePage` thành số 12.
  - Trình xem PDF bên phải khi nhận thấy trạng thái `activePage` thay đổi sẽ lập tức gọi hàm điều hướng nội bộ để cuộn màn hình hoặc lật trang hiển thị đến đúng trang số 12 ngay lập tức, giúp người dùng đối chiếu trực quan một cách nhanh chóng.

### 📌 Bước 4: Mở rộng Lõi AI hướng Đa phương thức và Đa tài liệu (Advanced AI Scope)

Nhằm nâng cao tính học thuật và giá trị thực tiễn của đồ án, hệ thống phát triển thêm 3 trục tính năng nâng cao dựa trên kiến trúc lõi sẵn có:

1. **Ý tưởng Trợ lý Không gian học tập (Space-Wide Multi-Document Chat):**
   - _Mô tả:_ Tự động hóa quá trình khai thác tri thức. Người dùng chỉ cần đặt câu hỏi trong Không gian học tập (Space), AI sẽ tự động tổng hợp, đối chiếu và trả lời dựa trên TOÀN BỘ các tài liệu hiện có trong Space đó mà không cần người dùng chọn thủ công từng file.
   - _Luồng xử lý:_ Frontend chỉ cần truyền lên mã định danh `space_id` và câu hỏi. Ở Backend, Spring Boot thực hiện truy vấn nối bảng (Join Query) để quét sạch toàn bộ dữ liệu các trang (`document_pages`) thuộc tất cả tài liệu nằm trong Space chỉ định. Toàn bộ kho text này được đóng gói tập trung vào một siêu cấu trúc ngữ cảnh có phân ranh giới file/trang rõ ràng trước khi gửi lên Gemini API. AI trả về JSON chứa nội dung kèm cặp định vị nguồn `{document_id, page_number}` để Frontend hiển thị và chuyển đổi file trực quan khi người dùng tương tác trích dẫn.

2. **Ý tưởng Tối ưu hội thoại dài (Condense Question Engine):**
   - _Mô tả:_ Giải quyết triệt để hiện tượng phình to dữ liệu (Context Bloating) và nhiễu thông tin khi phiên hội thoại kéo dài, đảm bảo tốc độ phản hồi của AI luôn ở mức cao nhất.
   - _Luồng xử lý:_ Hệ thống áp dụng quy trình xử lý 2 chặng (Two-step LLM Orchestration). Khi nhận câu hỏi mới mang tính chất nối tiếp từ Client, Spring Boot không nhồi toàn bộ lịch sử chat thô vào tài liệu. Thay vào đó, Backend gọi một lượt API nhanh (Lightweight Call) để ép Gemini kết hợp lịch sử gần nhất và câu hỏi mới thành một "Câu hỏi độc lập" (Standalone Question) đã bọc sẵn ngữ cảnh cũ. Sau đó, hệ thống mới dùng câu hỏi sạch này để truy vấn mỏ neo vào kho tài liệu của Space, giúp lõi AI trả về kết quả chính xác, gọn nhẹ và không bị lạc đề.

3. **Ý tưởng Đọc hiểu hình ảnh (Multimodal Vision Engine):**
   - _Mô tả:_ Hỗ trợ xử lý và phân tích các tài liệu mang tính trực quan như sơ đồ, biểu đồ, ảnh chụp slide bài giảng.
   - _Luồng xử lý:_ Tận dụng năng lực đa phương thức (Multimodal) của Gemini 1.5 Flash. Khi phát hiện tệp tin đầu vào là ảnh (`.png`, `.jpg`) hoặc trang tài liệu có chứa thành phần đồ họa (bóc tách qua Apache PDFBox), Spring Boot sẽ chuyển đổi dữ liệu nhị phân của ảnh sang định dạng Base64 và nhúng trực tiếp vào payload request gửi đi cùng với chuỗi ngữ cảnh văn bản. AI sẽ phân tích trực quan cấu trúc hình ảnh để đưa ra câu trả lời mà không cần qua tầng OCR trung gian.
