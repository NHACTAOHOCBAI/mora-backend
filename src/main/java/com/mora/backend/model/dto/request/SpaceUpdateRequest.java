package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpaceUpdateRequest {
    @NotBlank(message = "Tên Space không được để trống")
    @Size(max = 100, message = "Tên Space không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 1024, message = "Mô tả không được vượt quá 1024 ký tự")
    private String description;
}
