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
public class UserAiSettingRequest {

    @NotBlank(message = "Gemini API Key không được để trống")
    private String geminiApiKey;

    private String chatModel;
    private String routerModel;
    private String evaluatorModel;
    private String parserModel;
    private String summarizerModel;
    private Double temperature;
}
