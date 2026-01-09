package com.next.nexrailai.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @Column(nullable = false, unique = true)
    private String lineUserId;

    private String displayName;

    @Builder.Default
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ENABLE;

    private String pictureUrl;

    @CreationTimestamp
    private LocalDateTime joinedAt;

    private LocalDateTime lastActiveAt;

    public enum UserStatus{
        ENABLE,
        DISABLE
    }
}
