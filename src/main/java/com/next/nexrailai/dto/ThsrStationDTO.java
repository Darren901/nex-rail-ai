package com.next.nexrailai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author darren
 * @date 2025/12/31
 */
public record ThsrStationDTO(
        @JsonProperty("StationID") String stationId,
        @JsonProperty("StationName") Name stationName,
        @JsonProperty("StationPosition") Position position,
        @JsonProperty("StationAddress") String address) {

    public record Name(
            @JsonProperty("Zh_tw") String zhTw,
            @JsonProperty("En") String en
    ) {}

    public record Position(
            @JsonProperty("PositionLon") Double lon,
            @JsonProperty("PositionLat") Double lat
    ) {}
}
