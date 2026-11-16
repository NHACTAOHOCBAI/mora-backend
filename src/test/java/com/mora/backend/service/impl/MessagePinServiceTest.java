package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.ChatStateNotifier;
import com.mora.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class MessagePinServiceTest {
    @Mock SpaceRepository spaces;
    @Mock SpaceMemberRepository members;
    @Mock ChatMessageRepository messages;
    @Mock MessagePinRepository pins;
    @Mock UserService users;
    @Mock ChatStateNotifier notifier;
    @InjectMocks MessagePinServiceImpl service;

    final User owner = User.builder().id(1L).role(Role.ROLE_USER).build();
    final Space space = Space.builder().id(2L).user(owner).build();
    final ChatMessage message = ChatMessage.builder().id(3L).space(space).sender("assistant")
            .text("Important answer").messageType(MessageType.AI_RESPONSE).build();

    void setupMutation(User user) {
        when(users.getCurrentUser()).thenReturn(user);
        when(spaces.findForUpdate(2L)).thenReturn(Optional.of(space));
    }

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void allMemberRolesCanPinAndUnpin(SpaceRole role) {
        User user = role == SpaceRole.OWNER ? owner :
                User.builder().id(9L).role(Role.ROLE_USER).build();
        setupMutation(user);
        if (role != SpaceRole.OWNER) when(members.existsBySpaceIdAndUserId(2L, 9L)).thenReturn(true);
        when(messages.findById(3L)).thenReturn(Optional.of(message));
        when(pins.existsById(3L)).thenReturn(false, true);
        service.pin(2L, 3L);
        verify(pins).save(argThat(pin -> pin.getMessage() == message
                && pin.getPinnedByUserId().equals(user.getId()) && pin.getPinnedAt() != null));
        service.unpin(2L, 3L);
        verify(pins).deleteById(3L);
        verify(notifier, times(2)).changed(2L, false);
    }

    @Test
    void administratorCanPinWithoutMembership() {
        setupMutation(User.builder().id(99L).role(Role.ROLE_ADMIN).build());
        when(messages.findById(3L)).thenReturn(Optional.of(message));
        service.pin(2L, 3L);
        verify(pins).save(any());
        verifyNoInteractions(members);
    }

    @Test
    void nonmemberCannotReadPinOrUnpin() {
        User outsider = User.builder().id(9L).role(Role.ROLE_USER).build();
        setupMutation(outsider);
        when(spaces.findById(2L)).thenReturn(Optional.of(space));
        assertThrows(AppException.class, () -> service.pin(2L, 3L));
        assertThrows(AppException.class, () -> service.unpin(2L, 3L));
        assertThrows(AppException.class, () -> service.list(2L));
        verifyNoInteractions(messages, pins, notifier);
    }

    @Test
    void refusesCrossSpaceMessage() {
        setupMutation(owner);
        message.setSpace(Space.builder().id(20L).build());
        when(messages.findById(3L)).thenReturn(Optional.of(message));
        assertEquals(ErrorCode.MESSAGE_NOT_FOUND,
                assertThrows(AppException.class, () -> service.pin(2L, 3L)).getErrorCode());
        assertThrows(AppException.class, () -> service.unpin(2L, 3L));
        verifyNoInteractions(pins, notifier);
    }

    @Test
    void repeatedPutAndDeleteDoNotChangeExistingPin() {
        setupMutation(owner);
        when(messages.findById(3L)).thenReturn(Optional.of(message));
        when(pins.existsById(3L)).thenReturn(true, false);
        service.pin(2L, 3L);
        service.unpin(2L, 3L);
        verify(pins, never()).save(any());
        verify(pins, never()).deleteById(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void listIncludesOriginalMessageAndPinAuthor() {
        when(users.getCurrentUser()).thenReturn(owner);
        when(spaces.findById(2L)).thenReturn(Optional.of(space));
        var time = LocalDateTime.now();
        when(pins.findAllInSpace(2L)).thenReturn(List.of(MessagePin.builder().message(message)
                .pinnedByUserId(1L).pinnedByName("Owner").pinnedAt(time).build()));
        var result = service.list(2L).getFirst();
        assertEquals("Important answer", result.message().getText());
        assertEquals(3L, result.message().getId());
        assertEquals("Owner", result.pinnedByName());
        assertEquals(time, result.pinnedAt());
    }

    @Test
    void missingMessageReturnsNotFound() {
        setupMutation(owner);
        assertEquals(ErrorCode.MESSAGE_NOT_FOUND,
                assertThrows(AppException.class, () -> service.pin(2L, 88L)).getErrorCode());
    }
}
