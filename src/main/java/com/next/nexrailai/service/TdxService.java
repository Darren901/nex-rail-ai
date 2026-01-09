package com.next.nexrailai.service;

import com.next.nexrailai.common.ApBusinessException;
import com.next.nexrailai.common.Constant;
import com.next.nexrailai.dto.*;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class TdxService {

    private static final String TDX_TOKEN_REDIS_KEY = "tdx:access_token";

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

            // 1. 呼叫 TDX API
            List<ThsrStationDTO> tdxStations = restClient.get()
                    .uri("/api/basic/v2/Rail/THSR/Station")
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
        String redisKey = "tdx:timetable:" + fromStationId + ":" + toStationId + ":" + date;
        log.info(">>>> [TDX 查詢] 查詢時刻表 (Redis Key: {}): {} -> {} 日期: {}", redisKey, fromStationId, toStationId, date);

        // 1. 嘗試從 Redis 讀取快取
        try {
            String cachedTimetableJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedTimetableJson != null) {
                log.info(">>>> [TDX 查詢] 時刻表快取命中！從 Redis 讀取。");
                return JsonUtil.fromJson(cachedTimetableJson, new ParameterizedTypeReference<List<ThsrTimetableDTO>>() {});
            }
        } catch (Exception e) {
            log.error(">>>> [Redis] 讀取時刻表快取失敗", e);
        }

        // 2. 快取未命中，呼叫 TDX API
        try {
            List<ThsrTimetableDTO> timeTable = restClient.get()
                    .uri(uriBuilder ->
                            uriBuilder
                                .path("/api/basic/v2/Rail/THSR/DailyTimetable/OD/{from}/to/{to}/{date}")
                                .queryParam("$format", "JSON")
                                .build(fromStationId, toStationId, date)
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ThsrTimetableDTO>>() {
                    });

            log.debug(">>>>> [TDX Timetable Result]: {}", JsonUtil.prettyJson(timeTable));

            if (timeTable != null && !timeTable.isEmpty()) {
                // 3. 成功獲取後，寫入 Redis 快取
                try {
                    String jsonToCache = JsonUtil.toJson(timeTable);
                    // 時刻表 cache 6 小時 (因為一天內不太會改變)
                    redisTemplate.opsForValue().set(redisKey, jsonToCache, Duration.ofHours(6));
                    log.info(">>>> [TDX 查詢] 已將時刻表結果寫入 Redis 快取 (6小時)。");
                } catch (Exception e) {
                    log.error(">>>> [Redis] 寫入時刻表快取失敗", e);
                }
                return timeTable;
            }
            return List.of();
        } catch (Exception e) {
            log.error(">>>> [TDX 查詢] 失敗: {}", e.getMessage());
            return List.of();
        }
    }

    public List<ThsrOdAvailableSeatDTO.OdAvailableSeatDTO> getThsrAvailableSeats(String fromStationId, String toStationId, String date) {
        log.info(">>>> [TDX 查詢] 查詢座位: {} -> {} 日期: {}", fromStationId, toStationId, date);
        try {
            ThsrOdAvailableSeatDTO apiResponse = restClient.get()
                    .uri(uriBuilder ->
                            uriBuilder
                                .path("/api/basic/v2/Rail/THSR/AvailableSeatStatus/Train/OD/{from}/to/{to}/TrainDate/{date}")
                                .queryParam("$format", "JSON")
                                .build(fromStationId, toStationId, date)
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                    .retrieve()
                    .body(ThsrOdAvailableSeatDTO.class);

            log.debug(">>>>> [TDX AvailableSeat Result]: {}", JsonUtil.prettyJson(apiResponse));

            if (apiResponse != null && apiResponse.availableSeats() != null) {
                return apiResponse.availableSeats();
            }
            return List.of();

        } catch (Exception e) {
            log.error(">>>> [TDX 查詢] 查詢座位失敗", e);
            return List.of();
        }
    }

    public List<ThsrFareDTO> getFares(String originStationID, String destinationStationID) {
        String redisKey = "tdx:fares:" + originStationID + ":" + destinationStationID;
        log.info(">>>> [TDX 查詢] 查詢票價 (Redis Key: {}): {} -> {}", redisKey, originStationID, destinationStationID);

        // 1. 嘗試從 Redis 讀取快取
        try {
            String cachedFaresJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedFaresJson != null) {
                log.info(">>>> [TDX 查詢] 票價快取命中！從 Redis 讀取。");
                return JsonUtil.fromJson(cachedFaresJson, new ParameterizedTypeReference<List<ThsrFareDTO>>() {});
            }
        } catch (Exception e) {
            log.error(">>>> [Redis] 讀取票價快取失敗", e);
        }

        log.warn(">>>> [TDX 查詢] 票價快取未命中，準備呼叫 TDX API...");

        // 2. 快取未命中，呼叫 TDX API
        try {
            List<ThsrFareDTO> fares = restClient.get()
                    .uri(uriBuilder ->
                            uriBuilder
                                .path("/api/basic/v2/Rail/THSR/ODFare/{from}/to/{to}")
                                .queryParam("$format", "JSON")
                                .build(originStationID, destinationStationID)
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ThsrFareDTO>>() {});

            log.debug(">>>>> [TDX 查詢] Fares Result: {}", JsonUtil.prettyJson(fares));

            if (fares != null && !fares.isEmpty()) {
                // 3. 成功獲取後，寫入 Redis 快取，設定 24 小時過期
                try {
                    String jsonToCache = JsonUtil.toJson(fares);
                    redisTemplate.opsForValue().set(redisKey, jsonToCache, Duration.ofDays(31));
                    log.info(">>>> [TDX 查詢] 已將票價結果寫入 Redis 快取。");
                } catch (Exception e) {
                    log.error(">>>> [Redis] 寫入票價快取失敗", e);
                }
                return fares;
            }
            return List.of();
        } catch (Exception e) {
            log.error(">>>> [TDX 查詢] 查詢票價失敗: {}", e.getMessage());
            return List.of();
        }
    }


    /**
     * 呼叫 TDX MAAS API 取得高鐵訂票 DeepLink
     *
     * @param fromStationName 起點車站名稱
     * @param toStationName   終點車站名稱
     * @param trainDate       乘車日期 (YYYY-MM-DD)
     * @param trainTime       乘車時間 (HH:mm)
     * @param trainNumber     車次號碼
     * @return 訂票 DeepLink URL，若失敗則回傳 null
     */
    public String getMaasDeepLink(String fromStationName, String toStationName, String trainDate, String trainTime, String trainNumber) {
        log.info(">>>> [TDX MAAS] 查詢訂票 DeepLink: {} -> {} 日期: {} 時間: {} 車次: {}", fromStationName, toStationName, trainDate, trainTime, trainNumber);
        try {
            MaasDeeplinkResponseDTO apiResponse = restClient.get()
                    .uri(uriBuilder ->
                            uriBuilder
                                .path("/api/maas-thsr/booking/deeplink/direct/hsr")
                                .queryParam("start_station", "{start}")
                                .queryParam("end_station", "{end}")
                                .queryParam("train_date", "{date}")
                                .queryParam("train_time", "{time}")
                                .queryParam("train_number", "{number}")
                                .build(fromStationName, toStationName, trainDate, trainTime, trainNumber)
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<MaasDeeplinkResponseDTO>() {});

            if (apiResponse != null && "success".equals(apiResponse.result()) && apiResponse.data() != null) {
                String deepLink = apiResponse.data().deeplink();
                log.info(">>>> [TDX MAAS] 成功取得 DeepLink: {}", deepLink);
                return deepLink;
            } else {
                log.warn(">>>> [TDX MAAS] 取得 DeepLink 失敗或回應異常: {}", JsonUtil.prettyJson(apiResponse));
                return null;
            }
        } catch (Exception e) {
            log.error(">>>> [TDX MAAS] 查詢 DeepLink 發生錯誤: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getAccessToken() {
        String accessToken = redisTemplate.opsForValue().get(TDX_TOKEN_REDIS_KEY);
        if (accessToken != null) return accessToken;

        log.info("Token 已過期或不存在，準備向 TDX 申請新 Token...");

        TdxTokenResponse response = restClient.post()
                .uri("/auth/realms/TDXConnect/protocol/openid-connect/token")
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
