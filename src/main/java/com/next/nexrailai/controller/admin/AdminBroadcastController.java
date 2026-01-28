package com.next.nexrailai.controller.admin;

import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.dto.admin.BroadcastRequest;
import com.next.nexrailai.jpa.entity.AppUser;
import com.next.nexrailai.jpa.repository.AppUserRepository;
import com.next.nexrailai.service.LineMessageService;
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
    private final LineMessageService lineMessageService; /**
     * Broadcasts the provided message to all enabled users.
     *
     * @param request the broadcast request containing the message to send
     * @return `200 OK` if the message was sent to at least one enabled user; `400 Bad Request` if there are no enabled recipients
     */

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

        lineMessageService.multicast(targetUserIds, new TextMessage(request.message()));
        
        return ResponseEntity.ok().build();
    }
}