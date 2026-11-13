package com.mora.backend.service;
import com.mora.backend.model.dto.request.*;
import com.mora.backend.model.dto.response.*;
import java.util.List;
public interface StudySetService {
 StudySetResponse create(Long spaceId, CreateStudySetRequest request);
 List<StudySetResponse> list(Long spaceId);
 StudySetResponse get(Long id);
 QuizGradeResponse grade(Long id, QuizGradeRequest request);
 void delete(Long id);
}
