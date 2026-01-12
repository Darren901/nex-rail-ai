package com.next.nexrailai.controller.admin;

import com.next.nexrailai.dto.admin.BroadcastRequest;
import com.next.nexrailai.jpa.entity.AppUser;
import com.next.nexrailai.jpa.repository.AppUserRepository;
import com.next.nexrailai.service.LineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/broadcast")
@RequiredArgsConstructor
@Slf4j
public class AdminBroadcastController {

    private final AppUserRepository userRepository;
    private final LineService lineService;

    @PostMapping
    public ResponseEntity<Void> sendBroadcast(@RequestBody BroadcastRequest request) {
        log.info(">>>> [Admin] 收到廣播請求: {}", request.message());
        
        List<String> targetUserIds = userRepository.findAllByStatus(AppUser.UserStatus.ENABLE)
                .stream()
                .map(AppUser::getLineUserId)
                .toList();
        
        if (targetUserIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        lineService.sendMulticast(targetUserIds, request.message());
        
        return ResponseEntity.ok().build();
    }
}
