package com.mora.backend.controller;

import com.mora.backend.model.dto.request.TypingNotificationRequest;
import com.mora.backend.model.dto.response.TypingNotificationResponse;
import com.mora.backend.model.entity.User;
import com.mora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SpaceRealtimeController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @MessageMapping("/spaces/{spaceId}/typing")
    public void handleTyping(
            Principal principal,
            @DestinationVariable Long spaceId,
            @Payload TypingNotificationRequest request
    ) {
        if (principal == null) return;

        Optional<User> userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        TypingNotificationResponse response = TypingNotificationResponse.builder()
                .spaceId(spaceId)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName() != null ? user.getFullName() : user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .typing(request.isTyping())
                .build();

        messagingTemplate.convertAndSend("/topic/spaces/" + spaceId + "/typing", response);
    }
}
