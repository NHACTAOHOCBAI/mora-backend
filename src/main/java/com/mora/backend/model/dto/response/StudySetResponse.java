package com.mora.backend.model.dto.response;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StudySetResponse {
  private Long id; private Long spaceId; private String title; private String createdByName; private LocalDateTime createdAt;
  private List<SourceDocument> sourceDocuments; private List<FlashcardItem> flashcards; private List<QuizQuestionItem> quizQuestions;
  @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class SourceDocument { private Long id; private String name; }
  @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class FlashcardItem { private Long id; private String front; private String back; private Citation citation; }
  @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class QuizQuestionItem { private Long id; private String question; private List<String> options; private Citation citation; }
  @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Citation { private Long documentId; private String documentName; private Integer pageNumber; private String quote; }
}
