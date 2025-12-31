package com.next.nexrailai.service;


import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.ThsrTimetableDTO;
import com.next.nexrailai.jpa.entity.Station;
import com.next.nexrailai.jpa.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ThsrTicketService {

    private final StationRepository stationRepo;
    private final TdxService tdxService;

    /**
     * 給 Gemini 看的參數描述，幫助它精確提取使用者對話中的資訊
     */
    @JsonClassDescription("高鐵車次查詢請求參數")
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
            String time
    ){}

    /**
     * 查詢班次邏輯
     * @param fromName 起點站名 (例: "板橋")
     * @param toName   終點站名 (例: "左營")
     * @param date     日期 (格式: "yyyy-MM-dd")
     * @param afterTime 過濾時間 (格式: "HH:mm"，若不限則傳 null)
     */
    public List<ThsrTimetableDTO> searchTickets(String fromName, String toName, String date, String afterTime) {
        log.info(">>>> [查詢服務] 接收到需求: {} -> {}, 日期: {}, 時間後: {}", fromName, toName, date, afterTime);

        // 1. 從資料庫把中文站名轉成 TDX ID
        String fromId = stationRepo.findByStationNameContaining(fromName)
                .map(Station::getTdxId)
                .orElseThrow(() -> new ApBusinessException(Constant.RCODE.FROM_STATION_NOT_FOUND));

        String toId = stationRepo.findByStationNameContaining(toName)
                .map(Station::getTdxId)
                .orElseThrow(() -> new ApBusinessException(Constant.RCODE.TO_STATION_NOT_FOUND));

        // 2. 呼叫 TDX API 拿原始時刻表
        List<ThsrTimetableDTO> allTrains = tdxService.getThsrTimetable(fromId, toId, date);

        // 3. 如果使用者有指定時間
        if (afterTime != null && !afterTime.isBlank()) {
            return allTrains.stream()
                    .filter(train -> train.originStopTime().departureTime().compareTo(afterTime) >= 0)
                    .collect(Collectors.toList());
        }

        return allTrains;
    }
}
