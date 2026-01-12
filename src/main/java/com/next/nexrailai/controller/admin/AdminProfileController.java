package com.next.nexrailai.controller.admin;

import com.next.nexrailai.dto.admin.ChangePasswordRequest;
import com.next.nexrailai.jpa.entity.SystemAdmin;
import com.next.nexrailai.jpa.repository.SystemAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/profile")
@RequiredArgsConstructor
public class AdminProfileController {

    private final SystemAdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName(); // 從 JWT 解析出的 username

        SystemAdmin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("舊密碼錯誤");
        }

        admin.setPassword(passwordEncoder.encode(request.newPassword()));
        adminRepository.save(admin);

        return ResponseEntity.ok("密碼修改成功");
    }
}
