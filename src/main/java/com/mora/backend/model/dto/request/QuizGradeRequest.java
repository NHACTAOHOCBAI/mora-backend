package com.mora.backend.model.dto.request;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;
@Data
public class QuizGradeRequest {
    @NotEmpty private List<Answer> answers;
    @Data public static class Answer { private Long questionId; private Integer selectedOptionIndex; }
}
