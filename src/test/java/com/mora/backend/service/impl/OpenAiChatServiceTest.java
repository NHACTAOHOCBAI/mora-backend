package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
import com.mora.backend.exception.AppException;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiChatServiceTest {
    @Mock SpaceRepository spaces;
    @Mock SpaceMemberRepository members;
    @Mock ChatMessageRepository messages;
    @Mock DocumentRepository documents;
    @Mock DocumentPageRepository pages;
    @Mock AiServiceClient ai;
    @Mock ChatSummaryHelper summaries;
    @Mock UserService users;
    @Mock SimpMessagingTemplate socket;
    @InjectMocks ChatServiceImpl chat;

    @Test
    void newUserCanChatWithoutAnyPersonalAiConfiguration() {
        var user = User.builder().id(1L).username("new-user").role(Role.ROLE_USER).build();
        var space = Space.builder().id(2L).user(user).build();
        when(users.getCurrentUser()).thenReturn(user);
        when(spaces.findById(2L)).thenReturn(Optional.of(space));
        when(documents.findBySpaceId(2L)).thenReturn(List.of());
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = new AiServiceClient.PythonChatResponse();
        response.answer = "Xin chào";
        response.citations = List.of();
        when(ai.callChat(any())).thenReturn(response);
        var result = chat.chatWithSpace(SpaceChatRequest.builder().spaceId(2L).question("Chào").build());
        assertEquals("Xin chào", result.getAnswer());
        verify(ai).callChat(argThat(request -> request.spaceId.equals(2L) && request.question.equals("Chào")));
    }

    @Test
    void nonmemberStillCannotCallSharedAi() {
        var user = User.builder().id(1L).role(Role.ROLE_USER).build();
        var owner = User.builder().id(9L).role(Role.ROLE_USER).build();
        when(users.getCurrentUser()).thenReturn(user);
        when(spaces.findById(2L)).thenReturn(Optional.of(Space.builder().id(2L).user(owner).build()));
        when(members.findBySpaceIdAndUserId(2L, 1L)).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> chat.chatWithSpace(
                SpaceChatRequest.builder().spaceId(2L).question("Chào").build()));
        verifyNoInteractions(ai);
    }

    @Test
    void rejectsDocumentFromAnotherSpaceBeforeReadingOrCallingAi() {
        var user = User.builder().id(1L).role(Role.ROLE_USER).build();
        when(users.getCurrentUser()).thenReturn(user);
        when(spaces.findById(2L)).thenReturn(Optional.of(Space.builder().id(2L).user(user).build()));
        when(documents.findAllById(List.of(7L))).thenReturn(List.of(Document.builder().id(7L)
                .space(Space.builder().id(9L).build()).status(DocumentStatus.READY).build()));
        assertThrows(AppException.class, () -> chat.chatWithSpace(SpaceChatRequest.builder()
                .spaceId(2L).question("Hỏi").documentIds(List.of(7L)).build()));
        verifyNoInteractions(ai, pages, messages);
    }

    @Test
    void onlyExistingReadyDocumentsAreAllowedInRetrievalAndRawContext() {
        var user = User.builder().id(1L).role(Role.ROLE_USER).build();
        var space = Space.builder().id(2L).user(user).build();
        when(users.getCurrentUser()).thenReturn(user);
        when(spaces.findById(2L)).thenReturn(Optional.of(space));
        when(documents.findBySpaceId(2L)).thenReturn(List.of(
                Document.builder().id(7L).space(space).status(DocumentStatus.READY).build(),
                Document.builder().id(8L).space(space).status(DocumentStatus.FAILED).build()));
        when(pages.findByDocumentIdOrderByPageNumberAsc(7L)).thenReturn(List.of());
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = new AiServiceClient.PythonChatResponse();
        response.answer = "OK";
        response.citations = List.of();
        when(ai.callChat(any())).thenReturn(response);
        chat.chatWithSpace(SpaceChatRequest.builder().spaceId(2L).question("Hỏi").build());
        verify(ai).callChat(argThat(request -> request.documentIds.equals(List.of(7L))));
        verify(pages, never()).findByDocumentIdOrderByPageNumberAsc(8L);
    }
}
