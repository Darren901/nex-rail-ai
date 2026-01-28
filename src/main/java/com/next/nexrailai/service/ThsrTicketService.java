package com.next.nexrailai.service;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.*;
import com.next.nexrailai.dto.ai.BookingRequest;
import com.next.nexrailai.dto.ai.SearchRequest;
import com.next.nexrailai.jpa.entity.Station;
import com.next.nexrailai.jpa.repository.StationRepository;
import com.next.nexrailai.utils.EnumUtil;
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
     * 查詢高鐵班次與座位狀況
     * @param request 包含起訖站、日期、時間、票種等資訊的查詢請求
     * @return 結合了時刻表與座位資訊的旅程列表
     */
    public List<ThsrSummaryDTO> searchTickets(SearchRequest request) {
        log.info(">>>> [查詢服務] 接收到需求: {}", request);

        // 1. 站名正規化與 ID 轉換
        String normalizedFrom = normalizeStationName(request.from());
        String normalizedTo = normalizeStationName(request.to());

        String fromId = findStationIdByName(normalizedFrom, Constant.RCODE.FROM_STATION_NOT_FOUND);
        String toId = findStationIdByName(normalizedTo, Constant.RCODE.TO_STATION_NOT_FOUND);

        // 2. 呼叫 TDX API 拿原始時刻表 & 座位資訊
        List<ThsrTimetableDTO> allTrains = tdxService.getThsrTimetable(fromId, toId, request.date());
        List<ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> allSeats = tdxService.getThsrAvailableSeats(fromId, toId, request.date());

        // 3. 總是查詢票價，以便前端/Flex Message 顯示
        List<ThsrFareDTO> tdxFares = tdxService.getFares(fromId, toId);

        // 準備過濾條件參數 (若為 null 則不過濾)
        // 使用 EnumUtil 進行泛型查找
        Constant.FareClass fareClassEnum = EnumUtil.fromName(Constant.FareClass.class, request.fareClass());
        Integer targetFareClass = (fareClassEnum != null) ? fareClassEnum.getCode() : null;
        
        Constant.TicketType ticketTypeEnum = EnumUtil.fromName(Constant.TicketType.class, request.ticketType());
        Integer targetTicketType = (ticketTypeEnum != null) ? ticketTypeEnum.getCode() : null;
        
        Constant.CabinClass cabinClassEnum = EnumUtil.fromName(Constant.CabinClass.class, request.cabinClass());
        Integer targetCabinClass = (cabinClassEnum != null) ? cabinClassEnum.getCode() : null;
        
        log.info(">>>> [DEBUG] Filters - FareClass: {}, TicketType: {}, CabinClass: {}", targetFareClass, targetTicketType, targetCabinClass);

        int rawCount = tdxFares.stream().mapToInt(f -> f.fares().size()).sum();
        log.info(">>>> [DEBUG] Raw fares count from TDX: {}", rawCount);

        List<FareResultDTO> fares = tdxFares.stream()
                .flatMap(fareDTO -> fareDTO.fares().stream())
                // 條件過濾：如果 request 有指定才過濾，否則通過
                .filter(fare -> targetFareClass == null || fare.fareClass().equals(targetFareClass))
                .filter(fare -> targetTicketType == null || fare.ticketType().equals(targetTicketType))
                .filter(fare -> targetCabinClass == null || fare.cabinClass().equals(targetCabinClass))
                .map(fare -> {
                    Constant.TicketType type = EnumUtil.fromCode(Constant.TicketType.class, fare.ticketType());
                    String ticketTypeName = (type != null) ? type.getName() : "未知";
                    
                    Constant.FareClass fc = EnumUtil.fromCode(Constant.FareClass.class, fare.fareClass());
                    String fareClassName = (fc != null) ? fc.getName() : "未知";
                    
                    Constant.CabinClass cc = EnumUtil.fromCode(Constant.CabinClass.class, fare.cabinClass());
                    String cabinClassName = (cc != null) ? cc.getName() : "未知";
                    
                    return new FareResultDTO(ticketTypeName, fareClassName, cabinClassName, fare.price());
                })
                .collect(Collectors.toList());

        log.info(">>>> [查詢服務] 票價查詢結果數量: {}", fares.size());


        // 4. 將座位資訊轉為 Map<車次號碼, 座位物件> 以便快速查找
        Map<String, ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> seatMap = allSeats.stream()
                .collect(Collectors.toMap(
                        ThsrOdAvailableSeatDTO.OdAvailableSeatDTO::trainNo,
                        seat -> seat,
                        (seat1, seat2) -> seat1));

        // 5. 組合時刻表與座位資訊，並根據需求過濾時間
        Stream<ThsrTimetableDTO> trainStream = allTrains.stream();

        if (request.time() != null && !request.time().isBlank()) {
            trainStream = trainStream
                    .filter(train -> train.originStopTime().departureTime().compareTo(request.time()) >= 0);
        }

        List<ThsrTimetableDTO> filteredTrains = trainStream.collect(Collectors.toList());
        log.info(">>>> [查詢服務] 找到 {} 筆班次", filteredTrains.size());

        List<FareResultDTO> finalFares = fares;
        return filteredTrains.stream()
                .limit(10) // 限制回傳的班次數量為 10 筆
                .map(timetable -> {
                    ThsrOdAvailableSeatDTO.OdAvailableSeatDTO seat = seatMap.get(timetable.trainInfo().trainNo());
                    return ThsrSummaryDTO.of(timetable, seat, finalFares);
                })
                .collect(Collectors.toList());
    }

    public String bookTicket(BookingRequest request) {
        log.info(">>>> [訂票服務] 接收到需求: {}", request);

        String normalizedFrom = normalizeStationName(request.from());
        String normalizedTo = normalizeStationName(request.to());

        // 檢查參數完整性
        if (request.trainNumber() == null || request.trainNumber().isBlank() ||
                normalizedFrom == null || normalizedFrom.isBlank() ||
                normalizedTo == null || normalizedTo.isBlank() ||
                request.trainDate() == null || request.trainDate().isBlank() ||
                request.trainTime() == null || request.trainTime().isBlank()) {

            return "不好意思，我需要更完整的資訊才能幫您產生訂票連結。請告訴我您要搭乘的「起點站」、「終點站」、「日期」以及「出發時間」喔！";
        }

        return tdxService.getMaasDeepLink(normalizedFrom, normalizedTo, request.trainDate(), request.trainTime(), request.trainNumber());
    }

    /**
     * 直接產生訂票連結 (供排程服務使用)
     */
    public String generateDeepLink(String from, String to, String date, String time, String trainNo) {
        String normalizedFrom = normalizeStationName(from);
        String normalizedTo = normalizeStationName(to);
        return tdxService.getMaasDeepLink(normalizedFrom, normalizedTo, date, time, trainNo);
    }

    private String normalizeStationName(String inputName) {
        if (inputName == null) return "";
        // 移除常見贅字
        String cleaned = inputName.replace("高鐵", "").replace("站", "").trim();
        // 對應別名 (如: 高雄 -> 左營)
        return STATION_ALIAS_MAP.getOrDefault(cleaned, cleaned);
    }

    private String findStationIdByName(String stationName, Constant.RCODE errorCode) {
        return stationRepo.findByStationNameContaining(stationName)
                .map(Station::getTdxId)
                .orElseThrow(() -> new ApBusinessException(errorCode));
    }
}
