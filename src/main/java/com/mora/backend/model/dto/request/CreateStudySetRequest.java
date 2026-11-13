package com.mora.backend.model.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data
public class CreateStudySetRequest {
    @NotEmpty private List<Long> documentIds;
    @NotNull @Min(5) @Max(15) private Integer cardCount = 10;
}
