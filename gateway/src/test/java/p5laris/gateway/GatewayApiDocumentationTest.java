package p5laris.gateway;

import com.p5laris.proto.item.v1.PurchaseItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import p5laris.gateway.domain.character.api.CharacterController;
import p5laris.gateway.domain.character.api.dto.CharacterTalkMessagesResponse;
import p5laris.gateway.domain.character.infrastructure.grpc.CharacterGatewayService;
import p5laris.gateway.domain.item.api.ItemController;
import p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService;
import p5laris.gateway.domain.mission.api.MissionController;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.infrastructure.grpc.MissionGatewayService;
import p5laris.gateway.domain.share.api.ShareController;
import p5laris.gateway.domain.share.api.dto.ShareDto;
import p5laris.gateway.domain.share.infrastructure.grpc.ShareGatewayService;
import p5laris.gateway.domain.user.api.OnboardingController;
import p5laris.gateway.domain.user.api.dto.OnboardingDto;
import p5laris.gateway.domain.user.infrastructure.grpc.OnboardingGatewayService;
import p5laris.gateway.global.auth.JwtValidator;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MissionController.class,
        CharacterController.class,
        ShareController.class,
        OnboardingController.class,
        ItemController.class
})
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class GatewayApiDocumentationTest {

    private static final long USER_ID = 42L;
    private static final String TOKEN = "rest-docs-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MissionGatewayService missionGatewayService;

    @MockitoBean
    private CharacterGatewayService characterGatewayService;

    @MockitoBean
    private ShareGatewayService shareGatewayService;

    @MockitoBean
    private OnboardingGatewayService onboardingGatewayService;

    @MockitoBean
    private ItemGatewayService itemGatewayService;

    @MockitoBean
    private JwtValidator jwtValidator;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUpAuthentication() {
        when(redisTemplate.hasKey("blacklist:" + TOKEN)).thenReturn(false);
        when(jwtValidator.validateAndGetUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void documentMissionCompletion() throws Exception {
        MissionDto.CompletionAnswerResponse response = new MissionDto.CompletionAnswerResponse(
                100L,
                "COMPLETED",
                new MissionDto.CompletionAnswer("물 한 컵을 마셨어요.", "2026-06-11T12:00:00"),
                new MissionDto.MissionReward(10, 2),
                new MissionDto.WalletSnapshot(130),
                "PAID",
                null,
                "좋아, 오늘도 한 걸음이야."
        );
        when(missionGatewayService.submitCompletionAnswer(
                USER_ID,
                100L,
                new MissionDto.SubmitCompletionAnswerRequest("물 한 컵을 마셨어요.")
        )).thenReturn(response);

        mockMvc.perform(post("/api/mission/v1/missions/{missionId}/completion-answers", 100L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": "물 한 컵을 마셨어요."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andDo(document(
                        "mission-complete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(authHeader()),
                        pathParameters(parameterWithName("missionId").description("완료할 미션 ID")),
                        requestFields(
                                fieldWithPath("answer").type(JsonFieldType.STRING).description("완료 질문에 대한 답변")
                        ),
                        relaxedResponseFields(responseFields(
                                fieldWithPath("data.missionId").type(JsonFieldType.NUMBER).description("완료된 미션 ID"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("미션 상태"),
                                fieldWithPath("data.answer.text").type(JsonFieldType.STRING).description("저장된 답변"),
                                fieldWithPath("data.answer.answeredAt").type(JsonFieldType.STRING).description("답변 시각"),
                                fieldWithPath("data.reward.starPiece").type(JsonFieldType.NUMBER).description("별조각 보상"),
                                fieldWithPath("data.reward.affection").type(JsonFieldType.NUMBER).description("호감도 보상"),
                                fieldWithPath("data.wallet.starPiece").type(JsonFieldType.NUMBER).description("보상 반영 후 별조각"),
                                fieldWithPath("data.rewardStatus").type(JsonFieldType.STRING).description("보상 지급 상태"),
                                fieldWithPath("data.characterMessage").type(JsonFieldType.STRING).description("완료 캐릭터 메시지")
                        ))
                ));
    }

    @Test
    void documentCharacterTalkMessages() throws Exception {
        CharacterTalkMessagesResponse response = new CharacterTalkMessagesResponse(
                7L,
                "2026-06-11",
                "session-10",
                List.of(
                        new CharacterTalkMessagesResponse.MessageItem(
                                "USER", "오늘 조금 지쳤어.", 1, "request-1",
                                false, "2026-06-11T12:00:00", "session-10"
                        ),
                        new CharacterTalkMessagesResponse.MessageItem(
                                "ASSISTANT", "잠깐 숨을 고르는 것도 좋아.", 2, "request-1",
                                false, "2026-06-11T12:00:01", "session-10"
                        )
                )
        );
        when(characterGatewayService.getCharacterTalkMessages(7L, USER_ID, LocalDate.of(2026, 6, 11)))
                .thenReturn(response);

        mockMvc.perform(get("/api/character/v1/characters/{characterId}/talk/messages", 7L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("date", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
                .andDo(document(
                        "character-talk-messages",
                        preprocessResponse(prettyPrint()),
                        requestHeaders(authHeader()),
                        pathParameters(parameterWithName("characterId").description("캐릭터 ID")),
                        queryParameters(parameterWithName("date").description("조회 날짜, ISO-8601 형식").optional()),
                        relaxedResponseFields(responseFields(
                                fieldWithPath("data.characterId").type(JsonFieldType.NUMBER).description("캐릭터 ID"),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("조회 날짜"),
                                fieldWithPath("data.latestSessionId").type(JsonFieldType.STRING).description("최신 대화 세션 ID"),
                                fieldWithPath("data.messages").type(JsonFieldType.ARRAY).description("대화 메시지 목록"),
                                fieldWithPath("data.messages[].role").type(JsonFieldType.STRING).description("발화 역할"),
                                fieldWithPath("data.messages[].content").type(JsonFieldType.STRING).description("대화 내용"),
                                fieldWithPath("data.messages[].sequence").type(JsonFieldType.NUMBER).description("세션 내 순서"),
                                fieldWithPath("data.messages[].requestId").type(JsonFieldType.STRING).description("요청 추적 ID"),
                                fieldWithPath("data.messages[].fallbackUsed").type(JsonFieldType.BOOLEAN).description("fallback 사용 여부"),
                                fieldWithPath("data.messages[].createdAt").type(JsonFieldType.STRING).description("생성 시각"),
                                fieldWithPath("data.messages[].sessionId").type(JsonFieldType.STRING).description("세션 ID")
                        ))
                ));
    }

    @Test
    void documentShareEventCreation() throws Exception {
        ShareDto.CreateShareEventRequest request =
                new ShareDto.CreateShareEventRequest(30L, "KAKAO", "LINK", "share-42-20260611");
        when(shareGatewayService.createShareEvent(USER_ID, request))
                .thenReturn(new ShareDto.ShareEventResponse(
                        500L,
                        true,
                        10,
                        "PAID",
                        new ShareDto.ShareEventResponse.WalletInfo(140)
                ));

        mockMvc.perform(post("/api/share/v1/share-events")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shareCardId": 30,
                                  "platform": "KAKAO",
                                  "shareType": "LINK",
                                  "idempotencyKey": "share-42-20260611"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardPaid").value(true))
                .andDo(document(
                        "share-event-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(authHeader()),
                        requestFields(
                                fieldWithPath("shareCardId").type(JsonFieldType.NUMBER).description("공유 카드 ID"),
                                fieldWithPath("platform").type(JsonFieldType.STRING).description("공유 플랫폼"),
                                fieldWithPath("shareType").type(JsonFieldType.STRING).description("공유 방식"),
                                fieldWithPath("idempotencyKey").type(JsonFieldType.STRING).description("중복 보상 방지 멱등키")
                        ),
                        relaxedResponseFields(responseFields(
                                fieldWithPath("data.shareEventId").type(JsonFieldType.NUMBER).description("공유 이벤트 ID"),
                                fieldWithPath("data.rewardPaid").type(JsonFieldType.BOOLEAN).description("보상 지급 여부"),
                                fieldWithPath("data.rewardStarPiece").type(JsonFieldType.NUMBER).description("별조각 보상량"),
                                fieldWithPath("data.rewardStatus").type(JsonFieldType.STRING).description("보상 처리 상태"),
                                fieldWithPath("data.wallet.starPiece").type(JsonFieldType.NUMBER).description("보상 반영 후 별조각")
                        ))
                ));
    }

    @Test
    void documentOnboardingProfileSave() throws Exception {
        OnboardingDto.ProfileResponse response = OnboardingDto.ProfileResponse.builder()
                .livingType("ALONE")
                .wakeUpTime("07:30")
                .sleepTime("23:30")
                .preferredMissionTime("MORNING")
                .routineGoal("WAKE_UP")
                .activityPreference("INDOOR")
                .missionIntensity("LIGHT")
                .answersJson("{}")
                .completed(true)
                .onboardingVersion(2)
                .routineGoals(List.of("WAKE_UP"))
                .preferredTimeSlots(List.of("MORNING"))
                .missionPlaceContexts(List.of("HOME"))
                .avoidedMissionTags(List.of("OUTDOOR"))
                .build();
        when(onboardingGatewayService.saveProfile(eq(USER_ID), any(OnboardingDto.SaveProfileRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/onboarding/v1/profiles/me")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "livingType": "ALONE",
                                  "wakeUpTime": "07:30",
                                  "sleepTime": "23:30",
                                  "preferredMissionTime": "MORNING",
                                  "routineGoal": "WAKE_UP",
                                  "activityPreference": "INDOOR",
                                  "missionIntensity": "LIGHT",
                                  "answersJson": "{}",
                                  "completed": true,
                                  "onboardingVersion": 2,
                                  "routineGoals": ["WAKE_UP"],
                                  "preferredTimeSlots": ["MORNING"],
                                  "missionPlaceContexts": ["HOME"],
                                  "avoidedMissionTags": ["OUTDOOR"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andDo(document(
                        "onboarding-profile-save",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(authHeader()),
                        requestFields(
                                fieldWithPath("livingType").type(JsonFieldType.STRING).description("주거 형태"),
                                fieldWithPath("wakeUpTime").type(JsonFieldType.STRING).description("기상 시간"),
                                fieldWithPath("sleepTime").type(JsonFieldType.STRING).description("취침 시간"),
                                fieldWithPath("preferredMissionTime").type(JsonFieldType.STRING).description("선호 미션 시간"),
                                fieldWithPath("routineGoal").type(JsonFieldType.STRING).description("대표 루틴 목표"),
                                fieldWithPath("activityPreference").type(JsonFieldType.STRING).description("활동 선호"),
                                fieldWithPath("missionIntensity").type(JsonFieldType.STRING).description("미션 강도"),
                                fieldWithPath("answersJson").type(JsonFieldType.STRING).description("레거시 답변 JSON"),
                                fieldWithPath("completed").type(JsonFieldType.BOOLEAN).description("온보딩 완료 여부"),
                                fieldWithPath("onboardingVersion").type(JsonFieldType.NUMBER).description("온보딩 버전"),
                                fieldWithPath("routineGoals").type(JsonFieldType.ARRAY).description("루틴 목표 목록"),
                                fieldWithPath("preferredTimeSlots").type(JsonFieldType.ARRAY).description("선호 시간대 목록"),
                                fieldWithPath("missionPlaceContexts").type(JsonFieldType.ARRAY).description("미션 장소 목록"),
                                fieldWithPath("avoidedMissionTags").type(JsonFieldType.ARRAY).description("회피 미션 태그")
                        ),
                        relaxedResponseFields(responseFields(
                                fieldWithPath("data.completed").type(JsonFieldType.BOOLEAN).description("저장된 완료 여부"),
                                fieldWithPath("data.onboardingVersion").type(JsonFieldType.NUMBER).description("저장된 버전"),
                                fieldWithPath("data.routineGoals").type(JsonFieldType.ARRAY).description("저장된 루틴 목표"),
                                fieldWithPath("data.preferredTimeSlots").type(JsonFieldType.ARRAY).description("저장된 선호 시간대"),
                                fieldWithPath("data.missionPlaceContexts").type(JsonFieldType.ARRAY).description("저장된 장소"),
                                fieldWithPath("data.avoidedMissionTags").type(JsonFieldType.ARRAY).description("저장된 회피 태그")
                        ))
                ));
    }

    @Test
    void documentItemPurchase() throws Exception {
        when(itemGatewayService.purchaseItem(USER_ID, 70L, 1, "purchase-42-70"))
                .thenReturn(PurchaseItemResponse.newBuilder()
                        .setPurchaseId(800L)
                        .setItemId(70L)
                        .setName("Nova Skin")
                        .setQuantity(1)
                        .setPrice(50)
                        .setStarPiece(90)
                        .setTransactionId(900L)
                        .build());

        mockMvc.perform(post("/api/item/v1/item-purchases")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "purchase-42-70")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "itemId": 70,
                                  "quantity": 1,
                                  "idempotencyKey": "body-key-is-fallback"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wallet.starPiece").value(90))
                .andDo(document(
                        "item-purchase",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                                authHeader(),
                                headerWithName("Idempotency-Key").description("아이템 구매 멱등키")
                        ),
                        requestFields(
                                fieldWithPath("itemId").type(JsonFieldType.NUMBER).description("구매할 아이템 ID"),
                                fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("구매 수량"),
                                fieldWithPath("idempotencyKey").type(JsonFieldType.STRING)
                                        .description("헤더가 없을 때 사용하는 멱등키").optional()
                        ),
                        relaxedResponseFields(responseFields(
                                fieldWithPath("data.purchaseId").type(JsonFieldType.NUMBER).description("구매 이력 ID"),
                                fieldWithPath("data.itemId").type(JsonFieldType.NUMBER).description("구매한 아이템 ID"),
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("아이템 이름"),
                                fieldWithPath("data.quantity").type(JsonFieldType.NUMBER).description("구매 수량"),
                                fieldWithPath("data.price").type(JsonFieldType.NUMBER).description("차감된 별조각"),
                                fieldWithPath("data.wallet.starPiece").type(JsonFieldType.NUMBER).description("구매 후 별조각"),
                                fieldWithPath("data.transactionId").type(JsonFieldType.NUMBER).description("지갑 거래 ID")
                        ))
                ));
    }

    private static org.springframework.restdocs.headers.HeaderDescriptor authHeader() {
        return headerWithName("Authorization").description("Bearer 액세스 토큰");
    }

    private static org.springframework.restdocs.payload.FieldDescriptor[] responseFields(
            org.springframework.restdocs.payload.FieldDescriptor... dataFields
    ) {
        org.springframework.restdocs.payload.FieldDescriptor[] fields =
                new org.springframework.restdocs.payload.FieldDescriptor[dataFields.length + 3];
        fields[0] = fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("요청 성공 여부");
        fields[1] = fieldWithPath("data").type(JsonFieldType.OBJECT).description("응답 데이터");
        fields[2] = fieldWithPath("error").type(JsonFieldType.NULL).description("성공 시 null");
        System.arraycopy(dataFields, 0, fields, 3, dataFields.length);
        return fields;
    }
}
