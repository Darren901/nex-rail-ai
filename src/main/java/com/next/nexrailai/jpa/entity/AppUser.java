package com.next.nexrailai.jpa.entity;

import com.next.nexrailai.common.Constant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    private String status = Constant.USER_STATUS.ENABLE.getCode();

    private String pictureUrl;

    @CreationTimestamp
    private LocalDateTime joinedAt;

    @UpdateTimestamp
    private LocalDateTime lastActiveAt;
}
