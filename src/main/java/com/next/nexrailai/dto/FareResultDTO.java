package com.next.nexrailai.dto;

public record FareResultDTO(
        String ticketType,
        String fareClass,
        String cabinClass,
        Integer price
) {}
