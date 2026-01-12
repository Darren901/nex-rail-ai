package com.next.nexrailai.controller.admin;

import com.next.nexrailai.jpa.entity.SystemConfig;
import com.next.nexrailai.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
@Slf4j
public class AdminSystemConfigController {

    private final SystemConfigService configService;

    @GetMapping
    public ResponseEntity<List<SystemConfig>> getAllConfigs() {
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    @PostMapping("/{key}")
    public ResponseEntity<Void> updateConfig(
            @PathVariable String key,
            @RequestBody Map<String, String> payload
    ) {
        String value = payload.get("value");
        String description = payload.get("description");
        
        configService.updateConfig(key, value, description);
        return ResponseEntity.ok().build();
    }
}
