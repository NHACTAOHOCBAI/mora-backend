package com.mora.backend.model.dto.response;
import lombok.*;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QuizGradeResponse {
 private int score; private int total; private List<Result> results;
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Result { private Long questionId; private boolean correct; private Integer correctOptionIndex; private String explanation; private StudySetResponse.Citation citation; }
}
