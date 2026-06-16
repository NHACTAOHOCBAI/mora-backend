# 📘 TÀI LIỆU TỔNG QUAN VÀ Ý TƯỞNG TRIỂN KHAI DỰ ÁN

## 📝 1. TỔNG QUAN DỰ ÁN

**AI-Powered Social Learning Network** là một nền tảng mạng xã hội học tập nhóm tích hợp AI thế hệ mới. Dự án giải quyết bài toán phân tán tài liệu và hiện tượng "ảo tưởng" (hallucination) của AI thông qua mô hình **Source-Grounded AI** (AI mỏ neo kiến thức) tương tự như NotebookLM, kết hợp với hạ tầng tương tác cộng đồng dành riêng cho học sinh, sinh viên.

---

## 🎯 2. MỤC TIÊU CỐT LÕI

1. **Độ chính xác tuyệt đối (Strict Grounding):** AI bị giới hạn phạm vi kiến thức, chỉ trả lời dựa trên kho tài liệu do người dùng/nhóm cung cấp.
2. **Minh bạch nguồn gốc (Explainable AI):** Cung cấp khả năng kiểm chứng thông tin trực quan thông qua cơ chế ánh xạ trích dẫn (Citation Mapping).
3. **Cộng tác tri thức (Social Learning):** Biến việc tương tác với AI từ trải nghiệm cá nhân thành không gian thảo luận nhóm và đồng sáng tạo đề cương.

---

## 🗺️ 3. PHẠM VI TRIỂN KHAI (PROJECT SCOPE)

Dự án được thiết kế theo mô hình **MVP (Minimum Viable Product)** và triển khai cuốn chiếu qua 2 giai đoạn chính:

### 🚀 Giai đoạn 1: Lõi AI Vững Chắc (Core AI Engine)

- **Pipeline xử lý tài liệu:** Trích xuất văn bản sạch từ các định dạng `.pdf`, `.docx`, `.txt` theo cấu trúc từng trang và lưu trữ Metadata.
- **Long-Context Orchestration:** Tận dụng Context Window lớn của mô hình (Gemini API) để nạp toàn bộ tài liệu của nhóm vào cửa sổ ngữ cảnh, giữ toàn vẹn ngữ cảnh toàn cục.
- **Giao diện Split-screen View:** Giao diện chia đôi màn hình độc lập:
  - _Bên trái:_ Khung hội thoại chat với AI.
  - _Bên phải:_ Trình xem tài liệu PDF trực quan (PDF Viewer).
- **Trích dẫn tương tác (Interactive Citations):** Ép AI trả về cấu trúc JSON nghiêm ngặt chứa nguồn chính xác đến từng trang. Khi click vào thẻ nguồn (Ví dụ: `[Trang 12]`), PDF Viewer tự động cuộn đến trang đối chiếu.

### 🌐 Giai đoạn 2: Tầng Cộng Đồng (Social Layer)

- **Không gian học tập (Shared Spaces):** Quản lý nhóm học tập theo môn học, các thành viên cùng đóng góp tài liệu vào một kho chung để AI học tập.
- **Bảng tin nhóm (Social Feed & Pin):** Cho phép "Ghim" các câu trả lời chất lượng của AI lên bảng tin chung để các thành viên khác vào Thích (Like), Bình luận (Comment) và tranh biện.
- **Ghi chú cộng tác (Shared Notes):** Trình soạn thảo ghi chú chung (Rich Text đơn giản) để nhóm cùng đúc kết kiến thức từ AI thành đề cương ôn thi hệ thống.

---

## 💻 4. CÔNG NGHỆ LỰA CHỌN (TECH STACK)

| Thành phần     | Công nghệ lựa chọn                             | Lý do lựa chọn                                                                                                                                  |
| :------------- | :--------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------- |
| **Frontend**   | React (Vite), TailwindCSS                      | Tối ưu hiệu năng Single Page App (SPA), phản hồi mượt mà, hỗ trợ tốt thư viện render PDF (`react-pdf`).                                         |
| **Backend**    | Java Spring Boot (Spring Web, Spring Data JPA) | Hệ sinh thái mạnh mẽ, cấu trúc hướng đối tượng chặt chẽ, xử lý đa luồng tốt và quản lý logic doanh nghiệp ổn định.                              |
| **Database**   | PostgreSQL                                     | Lưu trữ dữ liệu quan hệ (User, Space, Document Metadata) bền vững, toàn vẹn dữ liệu cao.                                                        |
| **AI LLM API** | Gemini 1.5 Flash / Pro                         | Context Window khổng lồ (1M - 2M tokens), tốc độ phản hồi nhanh, chi phí tối ưu, kết nối qua REST API hoặc Google AI Client Libraries cho Java. |

---
