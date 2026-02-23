package com.next.nexrailai.controller;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class RedirectController {

    @Value("${app.redirect.allowed-hosts:irs.thsrc.com.tw,www.thsrc.com.tw,tdx.transportdata.tw,maas.transportdata.tw}")
    private String allowedHosts;
    private volatile Set<String> allowedHostsCache = Set.of();

    @PostConstruct
    void initAllowedHostsCache() {
        this.allowedHostsCache = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /**
     * 中轉頁面：接收網址並立即重定向，用於繞過 LINE In-App Browser 限制
     */
    @GetMapping("/api/redirect")
    public String redirectToExternal(@RequestParam(value = "url", required = false) String url, Model model) {
        String targetUrl = isAllowedRedirectUrl(url) ? url : "";
        if (!targetUrl.isEmpty()) {
            log.debug(">>>> [Redirect] Allowed redirect URL: {}", targetUrl);
        } else if (url != null && !url.isBlank()) {
            log.warn(">>>> [Redirect] Blocked unsafe redirect URL: {}", url);
        }

        model.addAttribute("targetUrl", targetUrl);
        model.addAttribute("invalidUrl", url != null && !url.isBlank() && targetUrl.isEmpty());
        return "external-redirect";
    }

    private boolean isAllowedRedirectUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                return false;
            }
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                return false;
            }

            return allowedHostsCache.contains(host.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }
}
