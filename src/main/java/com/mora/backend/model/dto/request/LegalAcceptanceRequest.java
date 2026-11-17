package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

@Data
public class LegalAcceptanceRequest {
    @AssertTrue(message = "Bạn cần đồng ý Điều khoản sử dụng")
    private boolean acceptsTerms;

    @AssertTrue(message = "Bạn cần đồng ý Chính sách quyền riêng tư")
    private boolean acceptsPrivacy;
}
