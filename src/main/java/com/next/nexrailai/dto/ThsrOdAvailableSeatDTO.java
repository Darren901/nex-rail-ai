package com.next.nexrailai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author darren
 * @date 2025/12/31
 */
public record ThsrOdAvailableSeatDTO(
        @JsonProperty("TrainDate") String trainDate,
        @JsonProperty("AvailableSeats") List<OdAvailableSeatDTO> availableSeats
) {
    public record OdAvailableSeatDTO(
            @JsonProperty("TrainNo") String trainNo,
            @JsonProperty("StandardSeatStatus") String standardSeatStatus,
            @JsonProperty("BusinessSeatStatus") String businessSeatStatus
    ) {
        public String getStandardStatusText() {
            return translate(standardSeatStatus);
        }

        private String translate(String code) {
            return switch (code) {
                case "O" -> "還有位子喔！";
                case "L" -> "位子不多了！";
                case "X" -> "客滿";
                default -> "系統忙碌中，暫無資訊";
            };
        }
    }
}
