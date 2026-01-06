package com.next.nexrailai.service;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.*;
import com.next.nexrailai.jpa.entity.Station;
import com.next.nexrailai.jpa.repository.StationRepository;
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

    private static final Map<String, Integer> TICKET_TYPE_MAP = Map.of(
            "單程票", 1,
            "來回票", 2,
            "早鳥票", 7,
            "團體票", 8
    );

    private static final Map<String, Integer> FARE_CLASS_MAP = Map.of(
            "成人", 1,
            "學生", 2,
            "孩童", 3,
            "敬老", 4,
            "愛心", 5,
            "軍警", 8,
            "法優", 9
    );

    private static final Map<String, Integer> CABIN_CLASS_MAP = Map.of(
            "標準座", 1,
            "商務座", 2,
            "自由座", 3
    );

    /**
     * 給 Gemini 看的參數描述，幫助它精確提取使用者對話中的資訊
     */
    public record SearchRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("起點車站名稱，例如：'板橋'、'台北'、'左營'。")
            String from,

            @JsonProperty(required = true)
            @JsonPropertyDescription("終點車站名稱，例如：'左營'、'台北'。")
            String to,

            @JsonProperty(required = true)
            @JsonPropertyDescription("查詢日期，格式必須為 YYYY-MM-DD。若使用者說『明天』，請計算出日期。")
            String date,

            @JsonPropertyDescription("出發時間之後，格式為 HH:mm。例如使用者說『下午兩點後』，請轉換為 '14:00'。若無指定則可不傳。")
            String time,

            @JsonPropertyDescription("票種，可選：'單程票', '早鳥票', '團體票'。若無指定，則代表使用者想查詢所有班次資訊，而非特定票價。")
            String ticketType,

            @JsonPropertyDescription("費率等級，可選：'成人', '學生', '孩童', '敬老', '愛心', '軍警', '法優'。若無指定，則代表使用者想查詢所有班次資訊，而非特定票價。")
            String fareClass,

            @JsonPropertyDescription("艙等，可選：'標準座', '商務座', '自由座'。若無指定，則代表使用者想查詢所有班次資訊，而非特定票價。")
            String cabinClass
    ){}

    /**
     * 給 Gemini 看的參數描述，幫助它精確提取使用者對話中的資訊
     */
    public record BookingRequest(
            @JsonPropertyDescription("起點車站名稱。請務必回顧對話歷史，找出使用者最早查詢的出發站 (Origin)。")
            String from,

            @JsonPropertyDescription("終點車站名稱。請務必回顧對話歷史，找出使用者最早查詢的抵達站 (Destination)。")
            String to,

            @JsonPropertyDescription("乘車日期 (YYYY-MM-DD)。請從對話歷史中提取查詢日期。")
            String trainDate,

            @JsonPropertyDescription("該車次的原始出發時間 (HH:mm)。請從對話歷史的班次列表中查找該車次的時間。")
            String trainTime,

            @JsonPropertyDescription("要訂購的車次號碼，例如 '0837' 或 '1321'。")
            String trainNumber
    ) {}

    /**
     * 查詢高鐵班次與座位狀況
     * @param request 包含起訖站、日期、時間、票種等資訊的查詢請求
     * @return 結合了時刻表與座位資訊的旅程列表
     */
    public List<ThsrSummaryDTO> searchTickets(SearchRequest request) {
        log.info(">>>> [查詢服務] 接收到需求: {}", request);

        // 1. 從資料庫把中文站名轉成 TDX ID
        String fromId = stationRepo.findByStationNameContaining(request.from())
                .map(Station::getTdxId)
                .orElseThrow(() -> new ApBusinessException(Constant.RCODE.FROM_STATION_NOT_FOUND));

        String toId = stationRepo.findByStationNameContaining(request.to())
                .map(Station::getTdxId)
                .orElseThrow(() -> new ApBusinessException(Constant.RCODE.TO_STATION_NOT_FOUND));

        // 2. 呼叫 TDX API 拿原始時刻表 & 座位資訊
        List<ThsrTimetableDTO> allTrains = tdxService.getThsrTimetable(fromId, toId, request.date());
        List<ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> allSeats = tdxService.getThsrAvailableSeats(fromId, toId, request.date());

        // 3. 如果使用者有指定票種或費率，就去查票價
        List<FareResultDTO> fares = List.of();

        if (request.ticketType() != null || request.fareClass() != null) {
            List<ThsrFareDTO> tdxFares = tdxService.getFares(fromId, toId);

            Integer targetTicketType = TICKET_TYPE_MAP.getOrDefault(request.ticketType(), 1);
            Integer targetFareClass = FARE_CLASS_MAP.getOrDefault(request.fareClass(), 1);

            fares = tdxFares.stream()
                    .flatMap(fareDTO -> fareDTO.fares().stream())
                    // 更新票價篩選邏輯: 移除對 ticketType 的檢查，只檢查 fareClass
                    .filter(fare -> fare.fareClass().equals(targetFareClass))
                    .filter(fare -> request.cabinClass() == null || CABIN_CLASS_MAP.get(request.cabinClass()).equals(fare.cabinClass()))
                    .map(fare -> {
                        String ticketTypeName = TICKET_TYPE_MAP.entrySet().stream()
                                .filter(e -> e.getValue().equals(fare.ticketType()))
                                .map(Map.Entry::getKey)
                                .findFirst().orElse("未知");
                        String fareClassName = FARE_CLASS_MAP.entrySet().stream()
                                .filter(e -> e.getValue().equals(fare.fareClass()))
                                .map(Map.Entry::getKey)
                                .findFirst().orElse("未知");
                        String cabinClassName = CABIN_CLASS_MAP.entrySet().stream()
                                .filter(e -> e.getValue().equals(fare.cabinClass()))
                                .map(Map.Entry::getKey)
                                .findFirst().orElse("未知");
                        return new FareResultDTO(ticketTypeName, fareClassName, cabinClassName, fare.price());
                    })
                    .collect(Collectors.toList());

            log.info(">>>> [查詢服務] 票價查詢結果數量: {}", fares.size());
        }


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

        // 檢查參數完整性
        if (request.trainNumber() == null || request.trainNumber().isBlank() ||
            request.from() == null || request.from().isBlank() ||
            request.to() == null || request.to().isBlank() ||
            request.trainDate() == null || request.trainDate().isBlank() ||
            request.trainTime() == null || request.trainTime().isBlank()) {

            return "不好意思，我需要更完整的資訊才能幫您產生訂票連結。請告訴我您要搭乘的「起點站」、「終點站」、「日期」以及「出發時間」喔！";
        }

        return tdxService.getMaasDeepLink(request.from(), request.to(), request.trainDate(), request.trainTime(), request.trainNumber());
    }
}
