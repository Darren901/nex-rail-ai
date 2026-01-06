package com.next.nexrailai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ThsrFareDTO(
        @JsonProperty("OriginStationID") String originStationID,
        @JsonProperty("OriginStationName") ThsrStationDTO.Name originStationName,
        @JsonProperty("DestinationStationID") String destinationStationID,
        @JsonProperty("DestinationStationName") ThsrStationDTO.Name destinationStationName,
        @JsonProperty("Direction") Integer direction,
        @JsonProperty("Fares") List<Fare> fares
) {
    public record Fare(
            @JsonProperty("TicketType") Integer ticketType,
            @JsonProperty("FareClass") Integer fareClass,
            @JsonProperty("CabinClass") Integer cabinClass,
            @JsonProperty("Price") Integer price
    ) {}
}