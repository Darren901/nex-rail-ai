package com.next.nexrailai.service;

import com.next.nexrailai.dto.MaasDeeplinkResponseDTO;
import com.next.nexrailai.dto.TdxTokenResponse;
import com.next.nexrailai.jpa.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TdxServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private StationRepository stationRepo;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TdxService tdxService;

    @SuppressWarnings("unchecked")
    @Test
    void getMaasDeepLink_ShouldReturnLink_WhenApiSuccess() {
        // Arrange
        // Mock Redis for Token (Cache Hit)
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("tdx:access_token")).thenReturn("valid_token");

        // Mock RestClient for DeepLink
        RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        MaasDeeplinkResponseDTO.Data data = new MaasDeeplinkResponseDTO.Data("http://deeplink", null);
        MaasDeeplinkResponseDTO response = new MaasDeeplinkResponseDTO("success", data);
        
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        // Act
        String result = tdxService.getMaasDeepLink("台北", "高雄", "2023-12-01", "10:00", "101");

        // Assert
        assertEquals("http://deeplink", result);
    }
}
