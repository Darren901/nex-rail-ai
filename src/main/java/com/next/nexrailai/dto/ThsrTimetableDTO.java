package com.next.nexrailai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author darren
 * @date 2025/12/31
 */
public record ThsrTimetableDTO(
        @JsonProperty("TrainDate") String trainDate,
        @JsonProperty("DailyTrainInfo") TrainInfo trainInfo,
        @JsonProperty("OriginStopTime") StopTime originStopTime,
        @JsonProperty("DestinationStopTime") StopTime destinationStopTime
) {
    // 1. 車次基本資訊 (車次號碼)
    public record TrainInfo(
            @JsonProperty("TrainNo") String trainNo,
            @JsonProperty("Direction") Integer direction // 0: 南下, 1: 北上
    ) {}

    // 2. 停站時間資訊
    public record StopTime(
            @JsonProperty("StationID") String stationId,
            @JsonProperty("StationName") StationName stationName,
            @JsonProperty("DepartureTime") String departureTime, // 出發時間 (HH:mm)
            @JsonProperty("ArrivalTime") String arrivalTime      // 抵達時間 (HH:mm)
    ) {}

    public record StationName(
            @JsonProperty("Zh_tw") String zhTw
    ) {}
}
