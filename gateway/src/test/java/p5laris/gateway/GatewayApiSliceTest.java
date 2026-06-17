package p5laris.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import p5laris.common.tracing.TraceContext;
import p5laris.gateway.domain.mission.api.MissionController;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.infrastructure.grpc.MissionGatewayService;
import p5laris.gateway.domain.notification.api.NotificationController;
import p5laris.gateway.domain.notification.api.dto.NotificationDto;
import p5laris.gateway.domain.notification.infrastructure.grpc.NotificationGatewayService;
import p5laris.gateway.domain.user.api.AuthController;
import p5laris.gateway.domain.user.infrastructure.grpc.AuthGatewayService;
import p5laris.gateway.global.auth.JwtValidator;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MissionController.class,
        NotificationController.class,
        AuthController.class
})
class GatewayApiSliceTest {

    private static final long LOGIN_USER_ID = 42L;
    private static final String TOKEN = "slice-test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MissionGatewayService missionGatewayService;

    @MockitoBean
    private NotificationGatewayService notificationGatewayService;

    @MockitoBean
    private AuthGatewayService authGatewayService;

    @MockitoBean
    private JwtValidator jwtValidator;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUpAuthentication() {
        when(redisTemplate.hasKey("blacklist:" + TOKEN)).thenReturn(false);
        when(jwtValidator.validateAndGetUserId(TOKEN)).thenReturn(LOGIN_USER_ID);
    }

    @Test
    void protectedApiWithoutBearerTokenReturnsStandardUnauthorizedBody() throws Exception {
        mockMvc.perform(get("/api/mission/v1/missions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("C004"))
                .andExpect(jsonPath("$.error.path").value("/api/mission/v1/missions/current"));

        verifyNoInteractions(missionGatewayService);
    }

    @Test
    void validMissionRequestUsesAuthenticatedUserAndSerializesResponseContract() throws Exception {
        MissionDto.MissionResponse response = new MissionDto.MissionResponse(
                100L,
                "2026-06-11",
                1,
                "Drink water",
                "Drink one glass of water.",
                "A small start counts.",
                "HEALTH",
                "EASY",
                10,
                "OFFERED"
        );
        when(missionGatewayService.createNextMission(
                eq(LOGIN_USER_ID),
                eq(new MissionDto.CreateNextMissionRequest(7L, 0L))
        )).thenReturn(response);

        mockMvc.perform(post("/api/mission/v1/missions/today-focus/next")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterId": 7,
                                  "lastMissionId": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.missionDate").value("2026-06-11"))
                .andExpect(jsonPath("$.data.rewardStarPiece").value(10))
                .andExpect(jsonPath("$.data.status").value("OFFERED"));

        verify(missionGatewayService).createNextMission(
                LOGIN_USER_ID,
                new MissionDto.CreateNextMissionRequest(7L, 0L)
        );
    }

    @Test
    void invalidMissionBodyReturnsValidationErrorBeforeGrpcCall() throws Exception {
        mockMvc.perform(post("/api/mission/v1/missions/today-focus/next")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterId": 0,
                                  "lastMissionId": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(missionGatewayService);
    }

    @Test
    void invalidQuietHoursFormatReturnsValidationErrorBeforeGrpcCall() throws Exception {
        mockMvc.perform(patch("/api/notification/v1/settings")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pushEnabled": true,
                                  "missionOfferEnabled": true,
                                  "characterStateEnabled": true,
                                  "dailyReminderEnabled": true,
                                  "quietHoursEnabled": true,
                                  "quietHoursStart": "25:00",
                                  "quietHoursEnd": "07:30"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(notificationGatewayService);
    }

    @Test
    void notificationListPreservesCursorJsonShapeAndQueryParameters() throws Exception {
        NotificationDto.NotificationsResponse response = new NotificationDto.NotificationsResponse(
                List.of(new NotificationDto.NotificationItem(
                        9L,
                        "MISSION_REWARD_RECOVERED",
                        "Reward complete",
                        "Your reward has arrived.",
                        "MISSION",
                        100L,
                        false,
                        "2026-06-11T12:30:00"
                )),
                new NotificationDto.PageInfo(8L, true, 20)
        );
        when(notificationGatewayService.getNotifications(LOGIN_USER_ID, false, 10L, 20))
                .thenReturn(response);

        mockMvc.perform(get("/api/notification/v1/notifications")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("read", "false")
                        .param("cursor", "10")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(9))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.pageInfo.nextCursor").value(8))
                .andExpect(jsonPath("$.data.pageInfo.hasNext").value(true))
                .andExpect(jsonPath("$.data.pageInfo.size").value(20));

        verify(notificationGatewayService).getNotifications(LOGIN_USER_ID, false, 10L, 20);
    }

    @Test
    void publicRefreshEndpointValidatesJsonWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/v1/token-refreshes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verify(authGatewayService, never()).refreshToken(any());
        verify(redisTemplate, never()).hasKey(any());
        verify(jwtValidator, never()).validateAndGetUserId(any());
    }

    @Test
    void traceIdFilterKeepsSafeIncomingTraceIdOnMvcResponse() throws Exception {
        when(missionGatewayService.getCurrentMission(LOGIN_USER_ID))
                .thenReturn(new MissionDto.MissionResponse(
                        100L, "2026-06-11", 1, "Title", "Description",
                        "Message", "HEALTH", "EASY", 10, "OFFERED"
                ));

        mockMvc.perform(get("/api/mission/v1/missions/current")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header(TraceContext.TRACE_ID_HEADER, "trace-slice-20260611"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, "trace-slice-20260611"));
    }
}
