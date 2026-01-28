package com.next.nexrailai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.dto.admin.BroadcastRequest;
import com.next.nexrailai.dto.admin.LoginRequest;
import com.next.nexrailai.jpa.entity.AppUser;
import com.next.nexrailai.jpa.repository.AppUserRepository;
import com.next.nexrailai.jpa.repository.SystemConfigRepository;
import com.next.nexrailai.security.JwtService;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private LineMessageService lineMessageService;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockitoBean
    private SystemConfigService systemConfigService;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        appUserRepository.deleteAll();
    }

    @Test
    void testLogin_Success() throws Exception {
        // Arrange
        LoginRequest loginRequest = new LoginRequest("admin", "password");
        
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateToken(any())).thenReturn("mock-jwt-token");

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"));
    }

    @Test
    void testBroadcast_Unauthorized() throws Exception {
        BroadcastRequest request = new BroadcastRequest("Test Message");

        mockMvc.perform(post("/api/admin/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testBroadcast_Success() throws Exception {
        // Arrange
        String token = "valid-admin-token";
        BroadcastRequest request = new BroadcastRequest("Hello World");
        
        // Mock JWT Validation for Filter
        when(jwtService.getUsername(token)).thenReturn("admin");
        when(jwtService.validateToken(eq(token), any(UserDetails.class))).thenReturn(true);
        
        UserDetails adminUser = User.builder()
                .username("admin")
                .password("password")
                .roles("ADMIN")
                .build();
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(adminUser);

        // Prepare Target User
        AppUser user = AppUser.builder()
                .lineUserId("U123456")
                .status(AppUser.UserStatus.ENABLE)
                .build();
        appUserRepository.save(user);

        // Act
        mockMvc.perform(post("/api/admin/broadcast")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Assert
        verify(lineMessageService).multicast(eq(List.of("U123456")), any(TextMessage.class));
    }

    @Test
    void testUpdateSystemConfig_Success() throws Exception {
        // Arrange
        String token = "valid-admin-token";
        String configKey = "test_key";
        
        // Mock JWT
        when(jwtService.getUsername(token)).thenReturn("admin");
        when(jwtService.validateToken(eq(token), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(User.withUsername("admin").password("pw").roles("ADMIN").build());

        // Mock config service behavior since it's mocked
        when(systemConfigService.get(configKey)).thenReturn("new_value");

        // Act
        Map<String, String> payload = Map.of("value", "new_value", "description", "new_desc");
        
        mockMvc.perform(post("/api/admin/configs/" + configKey)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Assert
        verify(systemConfigService).updateConfig(eq(configKey), eq("new_value"), eq("new_desc"));
        String updatedValue = systemConfigService.get(configKey);
        assert(updatedValue.equals("new_value"));
    }
}
