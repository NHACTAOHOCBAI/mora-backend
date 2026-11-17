package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "space_member_invitations") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SpaceMemberInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "space_id", nullable = false) private Space space;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inviter_id", nullable = false) private User inviter;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invitee_id", nullable = false) private User invitee;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SpaceRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private InvitationStatus status = InvitationStatus.PENDING;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
