package com.next.nexrailai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RedirectController {

    /**
     * 中轉頁面：接收網址並立即重定向，用於繞過 LINE In-App Browser 限制
     */
    @GetMapping("/api/redirect")
    public String redirectToExternal(@RequestParam(value = "url", required = false) String url, Model model) {
        model.addAttribute("targetUrl", url != null ? url : "");
        return "external-redirect";
    }
}
