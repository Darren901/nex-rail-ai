package com.next.nexrailai.dto;

import java.util.List;

/**
 * @author darren
 * @date 2025/12/31
 */
public record ThsrSummaryDTO(
        String trainNo,           // 車次
        String departureTime,     // 出發時間
        String arrivalTime,       // 抵達時間
        String standardSeatStatus, // 標準車廂座位狀態
        String businessSeatStatus,  // 商務車廂座位狀態
        List<FareResultDTO> fares
) {

    public static ThsrSummaryDTO of(ThsrTimetableDTO timetable, ThsrOdAvailableSeatDTO.OdAvailableSeatDTO seat, List<FareResultDTO> fares) {
        return new ThsrSummaryDTO(
                timetable.trainInfo().trainNo(),
                timetable.originStopTime().departureTime(),
                timetable.destinationStopTime().arrivalTime(),
                seat != null ? seat.getStandardStatusText() : "客滿或已過售票時間",
                seat != null ? translate(seat.businessSeatStatus()) : "客滿或已過售票時間",
                fares != null ? fares : List.of() // ← 加這行,確保永遠不是 null
        );
    }

    private static String translate(String code) {
        return switch (code != null ? code : "X") {
            case "O" -> "還有位子喔！";
            case "L" -> "位子不多了！";
            case "X" -> "客滿";
            default -> "系統忙碌中，暫無資訊";
        };
    }
}
