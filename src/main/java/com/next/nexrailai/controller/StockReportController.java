package com.next.nexrailai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/stock/report")
@RequiredArgsConstructor
public class StockReportController {

    private final StringRedisTemplate redisTemplate;

    private static final String REPORT_KEY_PREFIX = "stock:daily-report:";

    @GetMapping("/{reportId}")
    public String viewReport(@PathVariable String reportId, Model model) {
        String markdown = redisTemplate.opsForValue().get(REPORT_KEY_PREFIX + reportId);
        if (markdown == null) {
            model.addAttribute("expired", true);
            return "stock-report";
        }
        model.addAttribute("content", markdown);
        return "stock-report";
    }
}
