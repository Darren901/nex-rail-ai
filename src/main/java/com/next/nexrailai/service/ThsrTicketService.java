package com.next.nexrailai.service;

import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.BaseEnum;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.*;
import com.next.nexrailai.dto.ai.BookingRequest;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.Station;
import com.next.nexrailai.jpa.repository.StationRepository;
import com.next.nexrailai.utils.EnumUtil;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ThsrTicketService {

    private final StationRepository stationRepo;
    private final TdxService tdxService;
    
    private static final Map<String, String> STATION_ALIAS_MAP = Map.of(
            "高雄", "左營",
            "臺北", "台北",
            "臺中", "台中",
            "臺南", "台南"
    );

    /**
     * 內部用來傳遞解析後的起訖站資訊
     */
    @Builder
    private record RouteInfo(String fromId, String toId, String normalizedFrom, String normalizedTo) {}

    /**
     * 查詢高鐵班次與座位狀況
     */
    public List<ThsrSummaryDTO> searchTickets(SearchRequest request) {
        log.info(">>>> [查詢服務] 接收到需求: {}", request);

        // 1. 解析起訖站 (正規化 + ID 轉換)
        RouteInfo route = resolveRoute(request.from(), request.to());

        // 2. 呼叫 TDX API 獲取資料
        List<ThsrTimetableDTO> allTrains = tdxService.getThsrTimetable(route.fromId(), route.toId(), request.date());
        List<ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> allSeats = tdxService.getThsrAvailableSeats(route.fromId(), route.toId(), request.date());
        List<ThsrFareDTO> rawFares = tdxService.getFares(route.fromId(), route.toId());

        // 3. 處理票價 (過濾與轉換)
        List<FareResultDTO> processedFares = processFares(rawFares, request);

        // 4. 建立座位索引 (List -> Map)
        Map<String, ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> seatMap = indexSeats(allSeats);

        // 5. 組合結果並過濾
        return assembleResults(allTrains, seatMap, processedFares, request.time());
    }

    private RouteInfo resolveRoute(String from, String to) {
        String normalizedFrom = normalizeStationName(from);
        String normalizedTo = normalizeStationName(to);

        String fromId = findStationIdByName(normalizedFrom, Constant.RCODE.FROM_STATION_NOT_FOUND);
        String toId = findStationIdByName(normalizedTo, Constant.RCODE.TO_STATION_NOT_FOUND);

        return RouteInfo.builder()
                .fromId(fromId)
                .toId(toId)
                .normalizedFrom(normalizedFrom)
                .normalizedTo(normalizedTo)
                .build();
    }

    private List<FareResultDTO> processFares(List<ThsrFareDTO> rawFares, SearchRequest request) {
        // 準備過濾條件
        Integer targetFareClass = getCodeFromEnum(Constant.FareClass.class, request.fareClass());
        Integer targetTicketType = getCodeFromEnum(Constant.TicketType.class, request.ticketType());
        Integer targetCabinClass = getCodeFromEnum(Constant.CabinClass.class, request.cabinClass());

        log.debug(">>>> [DEBUG] Filters - FareClass: {}, TicketType: {}, CabinClass: {}", 
                targetFareClass, targetTicketType, targetCabinClass);

        return rawFares.stream()
                .flatMap(fareDTO -> fareDTO.fares().stream())
                .filter(fare -> targetFareClass == null || fare.fareClass().equals(targetFareClass))
                .filter(fare -> targetTicketType == null || fare.ticketType().equals(targetTicketType))
                .filter(fare -> targetCabinClass == null || fare.cabinClass().equals(targetCabinClass))
                .map(this::convertToFareResult)
                .collect(Collectors.toList());
    }

    private <E extends Enum<E> & com.next.nexrailai.common.BaseEnum> Integer getCodeFromEnum(Class<E> enumClass, String name) {
        E e = EnumUtil.fromName(enumClass, name);
        return (e != null) ? e.getCode() : null;
    }

    private FareResultDTO convertToFareResult(ThsrFareDTO.Fare fare) {
        String ticketTypeName = getEnumName(Constant.TicketType.class, fare.ticketType());
        String fareClassName = getEnumName(Constant.FareClass.class, fare.fareClass());
        String cabinClassName = getEnumName(Constant.CabinClass.class, fare.cabinClass());

        return new FareResultDTO(ticketTypeName, fareClassName, cabinClassName, fare.price());
    }

    private <E extends Enum<E> & BaseEnum> String getEnumName(Class<E> enumClass, int code) {
        E e = EnumUtil.fromCode(enumClass, code);
        return (e != null) ? e.getName() : "未知";
    }

    private Map<String, ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> indexSeats(List<ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> seats) {
        return seats.stream()
                .collect(Collectors.toMap(
                        ThsrOdAvailableSeatDTO.OdAvailableSeatDTO::trainNo,
                        seat -> seat,
                        (seat1, seat2) -> seat1));
    }

    private List<ThsrSummaryDTO> assembleResults(List<ThsrTimetableDTO> trains,
                                                 Map<String, ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> seatMap,
                                                 List<FareResultDTO> fares,
                                                 String filterTime) {
        Stream<ThsrTimetableDTO> stream = trains.stream();

        if (filterTime != null && !filterTime.isBlank()) {
            stream = stream.filter(t -> t.originStopTime().departureTime().compareTo(filterTime) >= 0);
        }

        List<ThsrSummaryDTO> results = stream
                .limit(10)
                .map(timetable -> {
                    ThsrOdAvailableSeatDTO.OdAvailableSeatDTO seat = seatMap.get(timetable.trainInfo().trainNo());
                    return ThsrSummaryDTO.of(timetable, seat, fares);
                })
                .collect(Collectors.toList());

        log.info(">>>> [查詢服務] 找到 {} 筆班次", results.size());
        return results;
    }

    public String bookTicket(BookingRequest request) {
        log.info(">>>> [訂票服務] 接收到需求: {}", request);

        String normalizedFrom = normalizeStationName(request.from());
        String normalizedTo = normalizeStationName(request.to());

        if (isBookingRequestInvalid(request, normalizedFrom, normalizedTo)) {
            return "不好意思，我需要更完整的資訊才能幫您產生訂票連結。請告訴我您要搭乘的「起點站」、「終點站」、「日期」以及「出發時間」喔！";
        }

        return tdxService.getMaasDeepLink(normalizedFrom, normalizedTo, request.trainDate(), request.trainTime(), request.trainNumber());
    }
    
    private boolean isBookingRequestInvalid(BookingRequest req, String from, String to) {
        return req.trainNumber() == null || req.trainNumber().isBlank() ||
               from == null || from.isBlank() ||
               to == null || to.isBlank() ||
               req.trainDate() == null || req.trainDate().isBlank() ||
               req.trainTime() == null || req.trainTime().isBlank();
    }

    public String generateDeepLink(String from, String to, String date, String time, String trainNo) {
        String normalizedFrom = normalizeStationName(from);
        String normalizedTo = normalizeStationName(to);
        return tdxService.getMaasDeepLink(normalizedFrom, normalizedTo, date, time, trainNo);
    }

    private String normalizeStationName(String inputName) {
        if (inputName == null) return "";
        String cleaned = inputName.replace("高鐵", "").replace("站", "").trim();
        return STATION_ALIAS_MAP.getOrDefault(cleaned, cleaned);
    }

    private String findStationIdByName(String stationName, Constant.RCODE errorCode) {
        return stationRepo.findByStationNameContaining(stationName)
                .map(Station::getTdxId)
                .orElseThrow(() -> new ApBusinessException(errorCode));
    }
}
