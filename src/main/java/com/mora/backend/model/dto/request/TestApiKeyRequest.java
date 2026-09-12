package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestApiKeyRequest {

    @NotBlank(message = "API Key không được để trống")
    private String apiKey;

    private String modelName;
}
