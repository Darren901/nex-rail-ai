package com.next.nexrailai.service;

import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.TdxTokenResponse;
import com.next.nexrailai.dto.ThsrStationDTO;
import com.next.nexrailai.dto.ThsrTimetableDTO;
import com.next.nexrailai.jpa.entity.Station;
import com.next.nexrailai.jpa.repository.StationRepository;
import com.next.nexrailai.utils.JsonUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TdxService {

    private static final String TDX_TOKEN_REDIS_KEY = "tdx:access_token";
    private static final Map<String, Integer> DEEPLINK_MAP = Map.ofEntries(
            Map.entry("0990", 1), Map.entry("1000", 2), Map.entry("1020", 3),
            Map.entry("1030", 4), Map.entry("1035", 5), Map.entry("1040", 6),
            Map.entry("1043", 7), Map.entry("1047", 8), Map.entry("1050", 9),
            Map.entry("1053", 10), Map.entry("1057", 11), Map.entry("1060", 12)
    );

    private final RestClient restClient;
    private final StationRepository stationRepo;
    private final StringRedisTemplate redisTemplate;

    @Value("${tdx.client-id}")
    private String clientId;

    @Value("${tdx.client-secret}")
    private String clientSecret;

    /**
     * 從 TDX 同步高鐵車站資料到資料庫
     */
    @Transactional
    public void syncThsrStations() {
        log.info(">>>> [TDX 同步] 開始抓取高鐵車站基本資料...");

        try {
            String token = getAccessToken();

            if (token == null || token.isBlank()) {
                log.error(">>>> [TDX 同步] 失敗：無法取得有效 Token");
                return;
            }

            // 1. 呼叫 TDX API (使用剛才定義的 HsrStationDTO)
            List<ThsrStationDTO> tdxStations = restClient.get()
                    .uri("https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/Station")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ThsrStationDTO>>() {});

            if (tdxStations == null || tdxStations.isEmpty()) {
                log.warn(">>>> [TDX 同步] 警告：抓不到任何車站資料");
                return;
            }

            log.debug(">>>>> [TDX Stations Result]: {}", JsonUtil.prettyJson(tdxStations));

            // 2. 先清空並立刻同步資料庫
            stationRepo.deleteAllInBatch();
            stationRepo.flush();

            // 3. 轉換為 Entity 並儲存
            List<Station> entities = tdxStations.stream()
                            .map(dto -> Station.builder()
                            .stationName(dto.stationName().zhTw())
                            .tdxId(dto.stationId())
                            .deeplinkId(DEEPLINK_MAP.getOrDefault(dto.stationId(), 0))
                            .longitude(dto.position().lon())
                            .latitude(dto.position().lat())
                            .build())
                            .toList();

            // 4. 儲存到 PostgreSQL
            stationRepo.saveAll(entities);

            log.info(">>>> [TDX 同步] 成功！共同步 {} 個車站", entities.size());

        } catch (Exception e) {
            log.error(">>>> [TDX 同步] 失敗：{}", e.getMessage(), e);
        }
    }

    public List<ThsrTimetableDTO> getThsrTimetable(String fromStationId, String toStationId, String date) {
        log.info(">>>> [TDX 查詢] 查票中: {} -> {} 日期: {}", fromStationId, toStationId, date);

        String url = String.format(
                "https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/DailyTimetable/OD/%s/to/%s/%s?$format=JSON",
                fromStationId, toStationId, date
        );

        try {
            List<ThsrTimetableDTO> timeTable = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ThsrTimetableDTO>>() {
                    });

            log.debug(">>>>> [TDX Timetable Result]: {}", JsonUtil.prettyJson(timeTable));

            return timeTable;
        } catch (Exception e) {
            log.error(">>>> [TDX 查詢] 失敗: {}", e.getMessage());
            return List.of();
        }
    }

    private String getAccessToken() {
        String accessToken = redisTemplate.opsForValue().get(TDX_TOKEN_REDIS_KEY);
        if (accessToken != null) return accessToken;

        log.info("Token 已過期或不存在，準備向 TDX 申請新 Token...");

        TdxTokenResponse response = restClient.post()
                .uri("https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret)
                .retrieve()
                .body(TdxTokenResponse.class);

        if(response != null){
            redisTemplate.opsForValue().set(TDX_TOKEN_REDIS_KEY, response.accessToken(), Duration.ofSeconds(response.expiresIn() - 60));
            return response.accessToken();
        }
        throw new ApBusinessException(Constant.RCODE.TDX_TOKEN_ERROR);
    }
}
