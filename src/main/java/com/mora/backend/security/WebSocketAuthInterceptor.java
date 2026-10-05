package com.mora.backend.security;

import com.mora.backend.model.entity.Role;
import com.mora.backend.model.entity.User;
import com.mora.backend.repository.SpaceMemberRepository;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;

    private static final Pattern SPACE_TOPIC_PATTERN = Pattern.compile("^/topic/spaces/(\\d+)(/.*)?$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // 1. Xác thực Token khi CONNECT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || authHeader.isBlank()) {
                authHeader = accessor.getFirstNativeHeader("token");
            }

            if (authHeader != null && !authHeader.isBlank()) {
                if (authHeader.startsWith("Bearer ")) {
                    authHeader = authHeader.substring(7);
                }

                try {
                    String username = jwtTokenProvider.extractUsername(authHeader);
                    if (username != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        if (jwtTokenProvider.isTokenValid(authHeader, userDetails)) {
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                            accessor.setUser(auth);
                            log.info("[WebSocket] User '{}' đã kết nối và xác thực thành công", username);
                        }
                    }
                } catch (Exception e) {
                    log.error("[WebSocket] Xác thực JWT thất bại khi CONNECT: {}", e.getMessage());
                    throw new SecurityException("Token không hợp lệ hoặc đã hết hạn: " + e.getMessage());
                }
            } else {
                log.warn("[WebSocket] Nhận kết nối CONNECT không có Header Authorization");
            }
        }

        // 2. Kiểm tra quyền thành viên khi SUBSCRIBE vào Topic của Space
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null) {
                Matcher matcher = SPACE_TOPIC_PATTERN.matcher(destination);
                if (matcher.matches()) {
                    Long spaceId = Long.parseLong(matcher.group(1));
                    Principal principal = accessor.getUser();

                    if (principal == null) {
                        log.warn("[WebSocket] Từ chối đăng ký {}: Chưa xác thực người dùng", destination);
                        throw new SecurityException("Yêu cầu xác thực để nhận tin từ Không gian học tập này.");
                    }

                    String username = principal.getName();
                    Optional<User> userOpt = userRepository.findByUsername(username);

                    if (userOpt.isEmpty()) {
                        throw new SecurityException("Không tìm thấy thông tin người dùng: " + username);
                    }

                    User user = userOpt.get();
                    if (user.getRole() != Role.ROLE_ADMIN) {
                        boolean isOwner = spaceRepository.existsByIdAndUserId(spaceId, user.getId());
                        boolean isMember = spaceMemberRepository.existsBySpaceIdAndUserId(spaceId, user.getId());

                        if (!isOwner && !isMember) {
                            log.warn("[WebSocket] Từ chối SUBSCRIBE: User '{}' không phải thành viên Space #{}", username, spaceId);
                            throw new SecurityException("Bạn không phải thành viên của Không gian học tập này.");
                        }
                    }

                    log.info("[WebSocket] User '{}' đăng ký lắng nghe thành công kênh: {}", username, destination);
                }
            }
        }

        return message;
    }
}
