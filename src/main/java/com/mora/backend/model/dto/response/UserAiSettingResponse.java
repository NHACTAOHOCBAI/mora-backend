package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAiSettingResponse {

    private boolean hasApiKey;
    private String maskedApiKey;
    private String chatModel;
    private String routerModel;
    private String evaluatorModel;
    private String parserModel;
    private String summarizerModel;
    private Double temperature;
}
