package com.next.nexrailai.service;

import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.*;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.Station;
import com.next.nexrailai.jpa.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThsrTicketServiceTest {

    @Mock
    private StationRepository stationRepo;
    @Mock
    private TdxService tdxService;

    @InjectMocks
    private ThsrTicketService ticketService;

    @Test
    void searchTickets_ShouldReturnFilteredResults() {
        // Arrange
        SearchRequest request = new SearchRequest("台北", "高雄", "2023-12-01", "10:00", "成人", "單程票", "標準座");

        // Mock Station Repo
        Station st1 = new Station(); st1.setTdxId("1000");
        Station st2 = new Station(); st2.setTdxId("2000");
        when(stationRepo.findByStationNameContaining("台北")).thenReturn(Optional.of(st1));
        // "高雄" is normalized to "左營" in service
        when(stationRepo.findByStationNameContaining("左營")).thenReturn(Optional.of(st2));

        // Mock TDX Timetable
        ThsrTimetableDTO train1 = mock(ThsrTimetableDTO.class);
        ThsrTimetableDTO.StopTime stopTime = new ThsrTimetableDTO.StopTime(
                "1000", 
                new ThsrTimetableDTO.StationName("台北"), 
                "10:30", 
                "10:33"
        );
        ThsrTimetableDTO.TrainInfo trainInfo = new ThsrTimetableDTO.TrainInfo("101", 1);
        
        when(train1.originStopTime()).thenReturn(stopTime);
        when(train1.destinationStopTime()).thenReturn(stopTime); // Add this
        when(train1.trainInfo()).thenReturn(trainInfo);
        
        when(tdxService.getThsrTimetable("1000", "2000", "2023-12-01")).thenReturn(List.of(train1));

        // Mock Seats
        ThsrOdAvailableSeatDTO.OdAvailableSeatDTO seat = new ThsrOdAvailableSeatDTO.OdAvailableSeatDTO(
                "101", "Limited", "Full"
        );
        when(tdxService.getThsrAvailableSeats("1000", "2000", "2023-12-01")).thenReturn(List.of(seat));

        // Mock Fares
        ThsrFareDTO.Fare fare = new ThsrFareDTO.Fare(1, 1, 1, 1490); // TicketType=1(OneWay), FareClass=1(Adult), Cabin=1(Standard)
        ThsrFareDTO fareDTO = new ThsrFareDTO("1000", null, "2000", null, 0, List.of(fare));
        when(tdxService.getFares("1000", "2000")).thenReturn(List.of(fareDTO));

        // Act
        List<ThsrSummaryDTO> result = ticketService.searchTickets(request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("101", result.get(0).trainNo());
        // Verify fare filtering worked (we requested Adult, OneWay, Standard, which matches mock)
        assertEquals(1490, result.get(0).fares().get(0).price());
    }
}
