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
     * Search THSR train schedules along with seat availability and applicable fares for the requested route and date.
     *
     * <p>Results are filtered by the request's time (if provided) and limited to at most 10 train entries.</p>
     *
     * @param request search criteria containing origin, destination, date, time, and optional fare filters
     * @return a list of ThsrSummaryDTO objects each containing a train's timetable, seat availability, and matching fare information
     * @throws ApBusinessException if the origin or destination station cannot be resolved to a TDX station ID
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

    /**
     * Resolve origin and destination names to their normalized forms and corresponding TDX station IDs.
     *
     * @param from the origin station name (may be null; will be normalized)
     * @param to   the destination station name (may be null; will be normalized)
     * @return a RouteInfo containing normalizedFrom, normalizedTo, fromId, and toId
     * @throws ApBusinessException if either station cannot be found (FROM_STATION_NOT_FOUND or TO_STATION_NOT_FOUND)
     */
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

    /**
     * Filters and converts raw THSR fare entries according to the search request's fare-related criteria.
     *
     * @param rawFares the raw fare DTOs containing fare entries to be filtered and converted
     * @param request  the search request whose `fareClass`, `ticketType`, and `cabinClass` fields are used as filters;
     *                 if a field cannot be mapped to an internal code, that filter is ignored
     * @return         a list of FareResultDTO representing fares that match the request filters
     */
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

    /**
     * Map a human-readable enum name to its numeric code.
     *
     * @param enumClass the enum type (must implement {@code BaseEnum}) to search for a matching name
     * @param name      the enum name or display name to look up
     * @return          the enum's code as an {@code Integer} if a match is found, or {@code null} otherwise
     */
    private <E extends Enum<E> & com.next.nexrailai.common.BaseEnum> Integer getCodeFromEnum(Class<E> enumClass, String name) {
        E e = EnumUtil.fromName(enumClass, name);
        return (e != null) ? e.getCode() : null;
    }

    /**
     * Converts a raw THSR fare entry into a FareResultDTO with human-readable names for ticket type, fare class, and cabin class.
     *
     * @param fare raw fare data containing numeric codes for ticket type, fare class, cabin class, and the price
     * @return a FareResultDTO containing the display names for ticket type, fare class, and cabin class, and the original price
     */
    private FareResultDTO convertToFareResult(ThsrFareDTO.Fare fare) {
        String ticketTypeName = getEnumName(Constant.TicketType.class, fare.ticketType());
        String fareClassName = getEnumName(Constant.FareClass.class, fare.fareClass());
        String cabinClassName = getEnumName(Constant.CabinClass.class, fare.cabinClass());

        return new FareResultDTO(ticketTypeName, fareClassName, cabinClassName, fare.price());
    }

    /**
     * Maps an enum code to its human-readable name.
     *
     * @param <E>       enum type that implements BaseEnum
     * @param enumClass the enum class to look up
     * @param code      the numeric code to translate
     * @return          the enum's name for the given code, or "未知" if no matching enum exists
     */
    private <E extends Enum<E> & BaseEnum> String getEnumName(Class<E> enumClass, int code) {
        E e = EnumUtil.fromCode(enumClass, code);
        return (e != null) ? e.getName() : "未知";
    }

    /**
     * Builds a map of available-seat entries keyed by train number.
     *
     * @param seats the list of available-seat DTOs to index
     * @return a map from train number to the corresponding OdAvailableSeatDTO; when multiple entries share the same train number, the first encountered entry is kept
     */
    private Map<String, ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> indexSeats(List<ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> seats) {
        return seats.stream()
                .collect(Collectors.toMap(
                        ThsrOdAvailableSeatDTO.OdAvailableSeatDTO::trainNo,
                        seat -> seat,
                        (seat1, seat2) -> seat1));
    }

    /**
     * Builds a list of THSR summary DTOs from timetables, available seats, and fares, optionally filtering by departure time.
     *
     * @param trains     list of timetable entries to consider
     * @param seatMap    map from train number to available-seat information; may be missing entries for some trains
     * @param fares      list of fare results to include in each summary
     * @param filterTime optional departure time lower bound (compareTo semantics against timetable.originStopTime().departureTime()); when non-blank, only trains with departureTime >= filterTime are included
     * @return           up to 10 ThsrSummaryDTO instances matching the optional time filter, each composed from a timetable entry, its corresponding seat (or null), and the provided fares
     */
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

    /**
     * Generate a MAAS deep link for booking a THSR ticket from the given booking request.
     *
     * Normalizes station names and validates that origin, destination, date, time, and train number are present;
     * if any required information is missing, returns a user-facing prompt requesting the missing fields.
     *
     * @param request the booking request containing origin, destination, train date, train time, and train number
     * @return the MAAS deep link for completing the booking when the request is valid, or a user-facing message asking for missing information otherwise
     */
    public String bookTicket(BookingRequest request) {
        log.info(">>>> [訂票服務] 接收到需求: {}", request);

        String normalizedFrom = normalizeStationName(request.from());
        String normalizedTo = normalizeStationName(request.to());

        if (isBookingRequestInvalid(request, normalizedFrom, normalizedTo)) {
            return "不好意思，我需要更完整的資訊才能幫您產生訂票連結。請告訴我您要搭乘的「起點站」、「終點站」、「日期」以及「出發時間」喔！";
        }

        return tdxService.getMaasDeepLink(normalizedFrom, normalizedTo, request.trainDate(), request.trainTime(), request.trainNumber());
    }
    
    /**
     * Checks whether a booking request is missing any required booking fields.
     *
     * @param req the booking request to validate; expected to provide trainNumber, trainDate, and trainTime
     * @param from the normalized origin station name
     * @param to the normalized destination station name
     * @return `true` if any of trainNumber, from, to, trainDate, or trainTime is null or blank; `false` otherwise
     */
    private boolean isBookingRequestInvalid(BookingRequest req, String from, String to) {
        return req.trainNumber() == null || req.trainNumber().isBlank() ||
               from == null || from.isBlank() ||
               to == null || to.isBlank() ||
               req.trainDate() == null || req.trainDate().isBlank() ||
               req.trainTime() == null || req.trainTime().isBlank();
    }

    /**
     * Builds a Maas deep link for a specific THSR journey.
     *
     * @param from    origin station name (raw input; will be normalized)
     * @param to      destination station name (raw input; will be normalized)
     * @param date    travel date (YYYY-MM-DD or service-expected format)
     * @param time    departure time (HH:mm or service-expected format)
     * @param trainNo train number for the journey
     * @return        the Maas deep link for the specified journey
     */
    public String generateDeepLink(String from, String to, String date, String time, String trainNo) {
        String normalizedFrom = normalizeStationName(from);
        String normalizedTo = normalizeStationName(to);
        return tdxService.getMaasDeepLink(normalizedFrom, normalizedTo, date, time, trainNo);
    }

    /**
     * Normalize a station name by removing THSR-specific words and applying known aliases.
     *
     * @param inputName the original station name (may be null or include "高鐵" / "站")
     * @return the cleaned, normalized station name; returns an empty string if {@code inputName} is null.
     */
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