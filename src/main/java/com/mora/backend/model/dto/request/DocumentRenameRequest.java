package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRenameRequest {

    @NotBlank(message = "Tên tài liệu không được để trống")
    @Size(max = 255, message = "Tên tài liệu không được vượt quá 255 ký tự")
    private String fileName;
}
