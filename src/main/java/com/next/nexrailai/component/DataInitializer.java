package com.next.nexrailai.component;

import com.next.nexrailai.jpa.entity.SystemAdmin;
import com.next.nexrailai.jpa.repository.StationRepository;
import com.next.nexrailai.jpa.repository.SystemAdminRepository;
import com.next.nexrailai.service.TdxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final StationRepository stationRepository;
    private final SystemAdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String defaultUsername;

    @Value("${app.admin.password:password}")
    private String defaultPassword;

    @Override
    public void run(String... args) throws Exception {
        // initStations();
        initAdmin();
    }

    private void initAdmin() {
        if (adminRepository.count() == 0) {
            log.info(">>>> [Init] Creating default admin user: {}...", defaultUsername);
            SystemAdmin admin = SystemAdmin.builder()
                    .username(defaultUsername)
                    .password(passwordEncoder.encode(defaultPassword))
                    .role(SystemAdmin.Role.SUPER_ADMIN)
                    .build();
            adminRepository.save(admin);
            log.info(">>>> [Init] Default admin created successfully.");
        }
    }
}
