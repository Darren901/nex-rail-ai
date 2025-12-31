package com.next.nexrailai.component;

import com.next.nexrailai.service.TdxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TdxService tdxService;

    @Override
    public void run(String... args) {
        // tdxService.syncThsrStations();
    }
}
