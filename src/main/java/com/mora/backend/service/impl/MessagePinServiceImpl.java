package com.mora.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.exception.*;
import com.mora.backend.model.dto.response.PinnedMessageResponse;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.*;
import com.mora.backend.util.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessagePinServiceImpl implements MessagePinService {
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final ChatMessageRepository messages;
    private final MessagePinRepository pins;
    private final UserService users;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatStateNotifier notifier;

    private User checkAccess(Space space) {
        User user = users.getCurrentUser();
        if (user.getRole() != Role.ROLE_ADMIN
                && !space.getUser().getId().equals(user.getId())
                && !members.existsBySpaceIdAndUserId(space.getId(), user.getId())) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }
        return user;
    }

    private Space findSpace(Long id, boolean lock) {
        return (lock ? spaces.findForUpdate(id) : spaces.findById(id))
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));
    }

    private ChatMessage findMessage(Long spaceId, Long messageId) {
        ChatMessage message = messages.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        if (message.getSpace() == null || !message.getSpace().getId().equals(spaceId)) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        return message;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PinnedMessageResponse> list(Long spaceId) {
        checkAccess(findSpace(spaceId, false));
        return pins.findAllInSpace(spaceId).stream().map(pin -> new PinnedMessageResponse(
                ChatMessageMapper.toResponse(pin.getMessage(), objectMapper),
                pin.getPinnedByUserId(), pin.getPinnedByName(), pin.getPinnedAt())).toList();
    }

    @Override
    @Transactional
    public void pin(Long spaceId, Long messageId) {
        // Serialize pin/unpin/clear/delete in a workspace, including first-time inserts.
        User user = checkAccess(findSpace(spaceId, true));
        ChatMessage message = findMessage(spaceId, messageId);
        if (pins.existsById(messageId)) return;
        pins.save(MessagePin.builder().message(message)
                .pinnedByUserId(user.getId())
                .pinnedByName(user.displayName())
                .pinnedAt(LocalDateTime.now()).build());
        notifier.changed(spaceId, false);
    }

    @Override
    @Transactional
    public void unpin(Long spaceId, Long messageId) {
        checkAccess(findSpace(spaceId, true));
        findMessage(spaceId, messageId);
        if (!pins.existsById(messageId)) return;
        pins.deleteById(messageId);
        notifier.changed(spaceId, false);
    }
}
