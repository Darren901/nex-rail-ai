package com.next.nexrailai.dto;

/**
 * @author darren
 * @date 2026/1/5
 */
public record MaasDeeplinkResponseDTO(
        String result,
        Data data
) {

    public record Data(
            String deeplink,
            String expired
    ) {
    }
}
