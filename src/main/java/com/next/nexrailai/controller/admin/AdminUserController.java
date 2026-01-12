package com.next.nexrailai.controller.admin;

import com.next.nexrailai.dto.admin.UpdateQuotaRequest;
import com.next.nexrailai.dto.admin.UpdateUserStatusRequest;
import com.next.nexrailai.dto.admin.UserDetailResponse;
import com.next.nexrailai.jpa.entity.AppUser;
import com.next.nexrailai.jpa.repository.AppUserRepository;
import com.next.nexrailai.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.next.nexrailai.service.AiService;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserRepository userRepository;
    private final RateLimitService rateLimitService;
    private final AiService aiService;

    @GetMapping
    public ResponseEntity<Page<UserDetailResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastActiveAt"));

        Page<AppUser> userPage = userRepository.findAll(pageRequest);

        Page<UserDetailResponse> dtoPage = userPage.map(user -> UserDetailResponse.builder()
                .lineUserId(user.getLineUserId())
                .displayName(user.getDisplayName())
                .pictureUrl(user.getPictureUrl())
                .status(user.getStatus())
                .joinedAt(user.getJoinedAt())
                .lastActiveAt(user.getLastActiveAt())
                .dailyQuota(rateLimitService.getRemainingQuota(user.getLineUserId()))
                .monthlyQuota(rateLimitService.getRemainingMonthlyQuota(user.getLineUserId()))
                .build());

        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailResponse> getUser(@PathVariable String userId) {
        return userRepository.findById(userId)
                .map(user -> UserDetailResponse.builder()
                        .lineUserId(user.getLineUserId())
                        .displayName(user.getDisplayName())
                        .pictureUrl(user.getPictureUrl())
                        .status(user.getStatus())
                        .joinedAt(user.getJoinedAt())
                        .lastActiveAt(user.getLastActiveAt())
                        .dailyQuota(rateLimitService.getRemainingQuota(user.getLineUserId()))
                        .monthlyQuota(rateLimitService.getRemainingMonthlyQuota(user.getLineUserId()))
                        .build())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable String userId,
            @RequestBody UpdateUserStatusRequest request
    ) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(request.status());
            userRepository.save(user);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/quota")
    public ResponseEntity<Void> updateUserQuota(
            @PathVariable String userId,
            @RequestBody UpdateQuotaRequest request
    ) {
        if (request.type() == UpdateQuotaRequest.QuotaType.DAILY) {
            if (request.action() == UpdateQuotaRequest.QuotaAction.SET) {
                rateLimitService.setQuota(userId, request.amount());
            } else {
                rateLimitService.addQuota(userId, request.amount());
            }
        } else {
            if (request.action() == UpdateQuotaRequest.QuotaAction.SET) {
                rateLimitService.setMonthlyQuota(userId, request.amount());
            } else {
                rateLimitService.addMonthlyQuota(userId, request.amount());
            }
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/memory")
    public ResponseEntity<Void> deleteUserMemory(@PathVariable String userId) {
        aiService.clearMemory(userId);
        return ResponseEntity.ok().build();
    }
}
