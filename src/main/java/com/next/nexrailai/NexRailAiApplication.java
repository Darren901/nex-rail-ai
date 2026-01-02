package com.next.nexrailai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NexRailAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexRailAiApplication.class, args);
    }

}
