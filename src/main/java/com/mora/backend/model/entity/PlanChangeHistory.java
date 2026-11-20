package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "plan_change_history")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PlanChangeHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "from_plan", length = 20) private PlanTier fromPlan;
    @Enumerated(EnumType.STRING) @Column(name = "to_plan", nullable = false, length = 20) private PlanTier toPlan;
    @Column(nullable = false, length = 30) private String source;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
