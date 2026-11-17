package com.mora.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.client.AiServiceClient;
import com.mora.backend.exception.*;
import com.mora.backend.model.dto.request.*;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class StudySetServiceImpl implements StudySetService {
    private final StudySetRepository studySets;
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final DocumentRepository documents;
    private final DocumentPageRepository documentPages;
    private final AiServiceClient ai;
    private final UserService users;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override @Transactional
    public StudySetResponse create(Long spaceId, CreateStudySetRequest request) {
        Space space = requireMember(spaceId);
        if (!Set.of(5, 10, 15).contains(request.getCardCount())) throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        List<Long> ids = request.getDocumentIds().stream().distinct().toList();
        List<Document> selected = documents.findAllById(ids);
        if (selected.size() != ids.size() || selected.stream().anyMatch(d -> !d.getSpace().getId().equals(spaceId))) throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        if (selected.stream().anyMatch(d -> d.getStatus() != DocumentStatus.READY)) throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);

        AiServiceClient.PythonStudyGenerateRequest aiRequest = new AiServiceClient.PythonStudyGenerateRequest();
        aiRequest.spaceId = spaceId; aiRequest.documentIds = ids; aiRequest.cardCount = request.getCardCount();
        aiRequest.context = new ArrayList<>();
        for (Document document : selected) {
            for (DocumentPage page : documentPages.findByDocumentIdOrderByPageNumberAsc(document.getId())) {
                AiServiceClient.PythonStudyGenerateRequest.StudyContextItem item = new AiServiceClient.PythonStudyGenerateRequest.StudyContextItem();
                item.documentId = document.getId(); item.documentName = document.getName(); item.pageNumber = page.getPageNumber(); item.text = page.getText();
                aiRequest.context.add(item);
            }
        }
        AiServiceClient.PythonStudyGenerateResponse generated = ai.generateStudySet(aiRequest);
        if (generated == null || generated.flashcards == null || generated.flashcards.size() != request.getCardCount() || generated.quizQuestions == null || generated.quizQuestions.size() != 5) throw new AppException(ErrorCode.AI_ENGINE_ERROR);

        StudySet set = StudySet.builder().title(clean(generated.title, "Bộ ôn tập tài liệu")).space(space).createdBy(users.getCurrentUser()).build();
        for (Document document : selected) set.getSourceDocuments().add(StudySetDocument.builder().studySet(set).document(document).build());
        for (AiServiceClient.PythonStudyGenerateResponse.StudyFlashcard card : generated.flashcards) {
            validateCitation(card.citation, ids);
            set.getFlashcards().add(Flashcard.builder().studySet(set).front(clean(card.front, "Câu hỏi")).back(clean(card.back, "")).documentId(card.citation.documentId).documentName(card.citation.documentName).pageNumber(card.citation.pageNumber).sourceQuote(card.citation.quote).build());
        }
        for (AiServiceClient.PythonStudyGenerateResponse.StudyQuizQuestion question : generated.quizQuestions) {
            validateCitation(question.citation, ids);
            if (question.options == null || question.options.size() != 4 || question.correctOptionIndex == null || question.correctOptionIndex < 0 || question.correctOptionIndex > 3) throw new AppException(ErrorCode.AI_ENGINE_ERROR);
            try { set.getQuizQuestions().add(QuizQuestion.builder().studySet(set).question(clean(question.question, "Câu hỏi")).optionsJson(mapper.writeValueAsString(question.options)).correctOptionIndex(question.correctOptionIndex).explanation(clean(question.explanation, "")).documentId(question.citation.documentId).documentName(question.citation.documentName).pageNumber(question.citation.pageNumber).sourceQuote(question.citation.quote).build()); }
            catch (Exception e) { throw new AppException(ErrorCode.AI_ENGINE_ERROR); }
        }
        StudySetResponse response = toResponse(studySets.save(set));
        space.setLastActivityAt(LocalDateTime.now());
        spaces.save(space);
        return response;
    }

    @Override @Transactional(readOnly = true) public List<StudySetResponse> list(Long spaceId) { requireMember(spaceId); return studySets.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream().map(this::toResponse).toList(); }
    @Override @Transactional(readOnly = true) public StudySetResponse get(Long id) { StudySet set = requireSet(id); requireMember(set.getSpace().getId()); return toResponse(set); }

    @Override @Transactional(readOnly = true)
    public QuizGradeResponse grade(Long id, QuizGradeRequest request) {
        StudySet set = requireSet(id); requireMember(set.getSpace().getId());
        Map<Long, Integer> submitted = new HashMap<>(); for (QuizGradeRequest.Answer answer : request.getAnswers()) if (answer.getQuestionId() != null) submitted.put(answer.getQuestionId(), answer.getSelectedOptionIndex());
        int score = 0; List<QuizGradeResponse.Result> results = new ArrayList<>();
        for (QuizQuestion q : set.getQuizQuestions()) { boolean correct = Objects.equals(submitted.get(q.getId()), q.getCorrectOptionIndex()); if (correct) score++; results.add(QuizGradeResponse.Result.builder().questionId(q.getId()).correct(correct).correctOptionIndex(q.getCorrectOptionIndex()).explanation(q.getExplanation()).citation(citation(q.getDocumentId(), q.getDocumentName(), q.getPageNumber(), q.getSourceQuote())).build()); }
        return QuizGradeResponse.builder().score(score).total(set.getQuizQuestions().size()).results(results).build();
    }
    @Override @Transactional public void delete(Long id) { StudySet set = requireSet(id); requireMember(set.getSpace().getId()); studySets.delete(set); }

    private Space requireMember(Long id) { Space space = spaces.findById(id).orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND)); User user = users.getCurrentUser(); if (user.getRole() != Role.ROLE_ADMIN && !members.existsBySpaceIdAndUserId(id, user.getId())) throw new AppException(ErrorCode.UNAUTHORIZED); return space; }
    private StudySet requireSet(Long id) { return studySets.findById(id).orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND)); }
    private void validateCitation(AiServiceClient.PythonStudyGenerateResponse.StudyCitation c, List<Long> validIds) { if (c == null || c.documentId == null || !validIds.contains(c.documentId) || c.pageNumber == null || c.pageNumber < 1) throw new AppException(ErrorCode.AI_ENGINE_ERROR); }
    private String clean(String s, String fallback) { return s == null || s.isBlank() ? fallback : s.trim(); }
    private StudySetResponse.Citation citation(Long docId, String name, Integer page, String quote) { return StudySetResponse.Citation.builder().documentId(docId).documentName(name).pageNumber(page).quote(quote).build(); }
    private List<String> options(String json) { try { return mapper.readValue(json, new TypeReference<List<String>>(){}); } catch (Exception e) { return List.of(); } }
    private StudySetResponse toResponse(StudySet set) { return StudySetResponse.builder().id(set.getId()).spaceId(set.getSpace().getId()).title(set.getTitle()).createdByName(set.getCreatedBy() == null ? null : (set.getCreatedBy().getFullName() == null ? set.getCreatedBy().getEmail() : set.getCreatedBy().getFullName())).createdAt(set.getCreatedAt()).sourceDocuments(set.getSourceDocuments().stream().map(d -> StudySetResponse.SourceDocument.builder().id(d.getDocument().getId()).name(d.getDocument().getName()).build()).toList()).flashcards(set.getFlashcards().stream().map(c -> StudySetResponse.FlashcardItem.builder().id(c.getId()).front(c.getFront()).back(c.getBack()).citation(citation(c.getDocumentId(),c.getDocumentName(),c.getPageNumber(),c.getSourceQuote())).build()).toList()).quizQuestions(set.getQuizQuestions().stream().map(q -> StudySetResponse.QuizQuestionItem.builder().id(q.getId()).question(q.getQuestion()).options(options(q.getOptionsJson())).citation(citation(q.getDocumentId(),q.getDocumentName(),q.getPageNumber(),q.getSourceQuote())).build()).toList()).build(); }
}
