package p5laris.mission.domain.application;

import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.CompletionInputType;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.GetTodayMissionsResponse;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.StartCompletionSessionResponse;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.MissionRewardOutbox;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionRewardOutboxStatus;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionCompletionAnswerRepository;
import p5laris.mission.domain.domain.repository.MissionRewardOutboxRepository;
import p5laris.mission.domain.domain.repository.MissionTemplateRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextClient;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextResult;
import p5laris.mission.domain.infrastructure.grpc.CharacterProfileClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardResult;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "mission.reward-outbox.enabled=false",
        "mission.reward-outbox.fixed-delay-ms=60000",
        "mission.reward-outbox.initial-delay-ms=60000",
        "mission.reward-outbox.batch-size=10",
        "mission.reward-outbox.max-attempts=5",
        "mission.reward-outbox.processing-timeout-seconds=60",
        "mission.reward-outbox.retry-initial-delay-seconds=60",
        "mission.reward-outbox.retry-max-delay-seconds=3600"
})
class MissionServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long CHARACTER_ID = 2001L;
    private static final Long OTHER_CHARACTER_ID = 2002L;

    @Autowired
    private MissionService missionService;

    @Autowired
    private MissionRewardDispatcher missionRewardDispatcher;

    @Autowired
    private UserMissionRepository userMissionRepository;

    @Autowired
    private MissionCompletionAnswerRepository missionCompletionAnswerRepository;

    @Autowired
    private MissionRewardOutboxRepository missionRewardOutboxRepository;

    @Autowired
    private MissionTemplateRepository missionTemplateRepository;

    @MockitoBean
    private WalletRewardClient walletRewardClient;

    @MockitoBean
    private AiMissionTextClient aiMissionTextClient;

    @MockitoBean
    private CharacterProfileClient characterProfileClient;

    @BeforeEach
    void setUp() {
        missionRewardOutboxRepository.deleteAll();
        missionCompletionAnswerRepository.deleteAll();
        userMissionRepository.deleteAll();
        reset(walletRewardClient, aiMissionTextClient, characterProfileClient);
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new WalletRewardResult(110, 9001L));
        when(walletRewardClient.getWalletStarPiece(anyLong()))
                .thenReturn(110);
        when(characterProfileClient.findActiveCharacterTypeCode(anyLong(), anyLong()))
                .thenReturn(Optional.of("NOVA"));
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenReturn(Optional.of(new AiMissionTextResult(
                        501L,
                        "AI가 바꾼 제안 문구",
                        "AI가 만든 완료 질문",
                        "AI가 만든 완료 반응",
                        false
                )));
    }

    @Test
    void 현재_미션이_없으면_빈_응답을_반환한다() {
        GetCurrentMissionResponse response = missionService.getCurrentMission(USER_ID);

        assertThat(response.hasMission()).isFalse();
    }

    @Test
    void 다음_미션을_생성하면_OFFERED_상태의_현재_미션이_된다() {
        assertThat(missionTemplateRepository.count()).isPositive();

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        GetCurrentMissionResponse current = missionService.getCurrentMission(USER_ID);

        assertThat(created.hasMission()).isTrue();
        assertThat(created.getMission().getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_OFFERED);
        assertThat(created.getMission().getRewardStarPiece()).isEqualTo(10);
        assertThat(created.getMission().getCharacterMessage()).isEqualTo("AI가 바꾼 제안 문구");
        assertThat(current.hasMission()).isTrue();
        assertThat(current.getMission().getId()).isEqualTo(created.getMission().getId());

        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        assertThat(savedMission.getAiGenerationId()).isEqualTo(501L);
        assertThat(savedMission.getCompletionCharacterResponse()).isEqualTo("AI가 만든 완료 반응");
        assertThat(savedAnswer.getQuestionText()).isEqualTo("AI가 만든 완료 질문");
    }

    @Test
    void AI_문구_생성에_실패하면_template_fallback_문구로_미션을_생성한다() {
        when(aiMissionTextClient.generateMissionTexts(any()))
                .thenReturn(Optional.empty());

        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        var template = missionTemplateRepository.findById(savedMission.getMissionTemplateId()).orElseThrow();

        assertThat(savedMission.getAiGenerationId()).isNull();
        assertThat(savedMission.getCharacterMessage()).isEqualTo(template.getFallbackCharacterMessage());
        assertThat(savedMission.getCompletionCharacterResponse()).isEqualTo(template.getFallbackCompletionResponse());
        assertThat(savedAnswer.getQuestionText()).isEqualTo(template.getFallbackQuestion());
    }

    @Test
    void 진행_중인_미션이_있으면_다음_미션을_추가로_생성할_수_없다() {
        missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        assertThatThrownBy(() -> missionService.createNextMission(USER_ID, CHARACTER_ID, 0L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        verify(aiMissionTextClient, times(1)).generateMissionTexts(any());
    }

    @Test
    void 진행_중인_미션은_캐릭터가_달라도_유저_기준으로_하나만_허용한다() {
        missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        assertThatThrownBy(() -> missionService.createNextMission(USER_ID, OTHER_CHARACTER_ID, 0L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
    }

    @Test
    void OFFERED_미션은_거절할_수_있다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        RejectMissionResponse rejected = missionService.rejectMission(
                USER_ID,
                created.getMission().getId()
        );
        GetCurrentMissionResponse current = missionService.getCurrentMission(USER_ID);
        UserMission saved = userMissionRepository.findById(created.getMission().getId()).orElseThrow();

        assertThat(rejected.getMissionId()).isEqualTo(created.getMission().getId());
        assertThat(rejected.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_REJECTED);
        assertThat(rejected.getRejectedAt()).isNotBlank();
        assertThat(rejected.getCharacterMessage()).isNotBlank();
        assertThat(current.hasMission()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(UserMissionStatus.REJECTED);
        assertThat(saved.getRejectedAt()).isNotNull();
    }

    @Test
    void 오늘_미션_stack은_완료_거절_진행중_미션과_집계를_함께_반환한다() {
        CreateNextMissionResponse first = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, first.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, first.getMission().getId(), "첫 번째 미션 완료");

        CreateNextMissionResponse second = missionService.createNextMission(USER_ID, CHARACTER_ID, first.getMission().getId());
        missionService.rejectMission(USER_ID, second.getMission().getId());

        CreateNextMissionResponse third = missionService.createNextMission(USER_ID, CHARACTER_ID, second.getMission().getId());

        GetTodayMissionsResponse response = missionService.getTodayMissions(USER_ID);

        assertThat(response.getMissionDate()).isNotBlank();
        assertThat(response.getMaxDailyOffers()).isEqualTo(15);
        assertThat(response.getOfferedCount()).isEqualTo(3);
        assertThat(response.getCompletedCount()).isEqualTo(1);
        assertThat(response.getRejectedCount()).isEqualTo(1);
        assertThat(response.getRemainingOfferCount()).isEqualTo(12);
        assertThat(response.hasCurrentMissionId()).isTrue();
        assertThat(response.getCurrentMissionId()).isEqualTo(third.getMission().getId());
        assertThat(response.getMissionsList()).hasSize(3);
        assertThat(response.getMissions(0).getId()).isEqualTo(first.getMission().getId());
        assertThat(response.getMissions(0).getStackOrder()).isEqualTo(1);
        assertThat(response.getMissions(0).getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getMissions(0).getCompletedAt()).isNotBlank();
        assertThat(response.getMissions(1).getId()).isEqualTo(second.getMission().getId());
        assertThat(response.getMissions(1).getStackOrder()).isEqualTo(2);
        assertThat(response.getMissions(1).getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_REJECTED);
        assertThat(response.getMissions(1).getRejectedAt()).isNotBlank();
        assertThat(response.getMissions(2).getId()).isEqualTo(third.getMission().getId());
        assertThat(response.getMissions(2).getStackOrder()).isEqualTo(3);
        assertThat(response.getMissions(2).getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_OFFERED);
        assertThat(response.getMissions(2).getCompletedAt()).isBlank();
        assertThat(response.getMissions(2).getRejectedAt()).isBlank();
    }

    @Test
    void 하루_미션_제안은_최대_15개까지만_가능하다() {
        for (int i = 0; i < 15; i++) {
            CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
            missionService.rejectMission(USER_ID, created.getMission().getId());
        }

        assertThatThrownBy(() -> missionService.createNextMission(USER_ID, CHARACTER_ID, 0L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void OFFERED_미션은_완료_질문_세션을_시작할_수_있다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        StartCompletionSessionResponse response = missionService.startCompletionSession(
                USER_ID,
                created.getMission().getId()
        );
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();

        assertThat(response.getMissionId()).isEqualTo(created.getMission().getId());
        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_ANSWERING);
        assertThat(response.getQuestion().getId()).isEqualTo(savedAnswer.getId());
        assertThat(response.getQuestion().getText()).isEqualTo("AI가 만든 완료 질문");
        assertThat(response.getQuestion().getInputType()).isEqualTo(CompletionInputType.COMPLETION_INPUT_TYPE_TEXT);
        assertThat(response.getQuestion().getMinLength()).isEqualTo(1);
        assertThat(response.getQuestion().getMaxLength()).isEqualTo(300);
        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.ANSWERING);
        assertThat(savedMission.getCompletionStartedAt()).isNotNull();
        assertThat(savedAnswer.getAnswerText()).isNull();
        assertThat(savedAnswer.getAnsweredAt()).isNull();
    }

    @Test
    void ANSWERING_미션에_완료_질문_세션을_다시_시작하면_기존_질문을_반환한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        StartCompletionSessionResponse first = missionService.startCompletionSession(
                USER_ID,
                created.getMission().getId()
        );

        StartCompletionSessionResponse second = missionService.startCompletionSession(
                USER_ID,
                created.getMission().getId()
        );

        assertThat(second.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_ANSWERING);
        assertThat(second.getQuestion().getId()).isEqualTo(first.getQuestion().getId());
        assertThat(missionCompletionAnswerRepository.count()).isEqualTo(1);
    }

    @Test
    void REJECTED_미션은_완료_질문_세션을_시작할_수_없다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.rejectMission(USER_ID, created.getMission().getId());

        assertThatThrownBy(() -> missionService.startCompletionSession(USER_ID, created.getMission().getId()))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_INVALID_STATUS);
    }

    @Test
    void ANSWERING_미션은_답변_제출_후_COMPLETED_상태가_된다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "  물 한 컵을 마셨어  "
        );
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        MissionRewardOutbox savedOutbox = missionRewardOutboxRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();

        assertThat(response.getMissionId()).isEqualTo(created.getMission().getId());
        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getAnswer().getText()).isEqualTo("물 한 컵을 마셨어");
        assertThat(response.getAnswer().getAnsweredAt()).isNotBlank();
        assertThat(response.getReward().getStarPiece()).isEqualTo(10);
        assertThat(response.getWallet().getStarPiece()).isEqualTo(110);
        assertThat(response.getCharacterMessage()).isNotBlank();
        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.COMPLETED);
        assertThat(savedMission.getCompletedAt()).isNotNull();
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        assertThat(savedAnswer.getAnswerText()).isEqualTo("물 한 컵을 마셨어");
        assertThat(savedAnswer.getAnsweredAt()).isNotNull();
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionRewardOutboxStatus.SUCCEEDED);
        assertThat(savedOutbox.getAttemptCount()).isZero();
        assertThat(savedOutbox.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        verify(walletRewardClient).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
    }

    @Test
    void 완료_답변은_1자_이상_300자_이하여야_한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "   "
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ANSWER_INVALID);

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "가".repeat(301)
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ANSWER_INVALID);
    }

    @Test
    void COMPLETED_미션에_같은_답변을_다시_제출하면_wallet_적립을_다시_호출하지_않는다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, created.getMission().getId(), "완료했어");

        SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                " 완료했어 "
        );

        assertThat(response.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(response.getAnswer().getText()).isEqualTo("완료했어");
        assertThat(response.getWallet().getStarPiece()).isEqualTo(110);
        assertThat(missionRewardOutboxRepository.findByMissionId(created.getMission().getId()))
                .isPresent()
                .get()
                .extracting(MissionRewardOutbox::getStatus)
                .isEqualTo(MissionRewardOutboxStatus.SUCCEEDED);
        verify(walletRewardClient, times(1)).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
        verify(walletRewardClient, times(1)).getWalletStarPiece(USER_ID);
    }

    @Test
    void COMPLETED_미션에_다른_답변을_다시_제출하면_수정할_수_없다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        missionService.submitCompletionAnswer(USER_ID, created.getMission().getId(), "완료했어");

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "또 완료했어"
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ALREADY_COMPLETED);
    }

    @Test
    void wallet_보상_지급이_실패하면_미션은_COMPLETED지만_보상_marker는_남기지_않는다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED));

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_REWARD_FAILED);

        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionCompletionAnswer savedAnswer = missionCompletionAnswerRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        MissionRewardOutbox savedOutbox = missionRewardOutboxRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();

        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.COMPLETED);
        assertThat(savedMission.getIdempotencyKey()).isNull();
        assertThat(savedAnswer.getAnswerText()).isEqualTo("완료했어");
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionRewardOutboxStatus.PENDING);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(1);
        assertThat(savedOutbox.getLastErrorMessage()).isNotBlank();
    }

    @Test
    void reward_marker가_없는_COMPLETED_미션은_같은_답변_재시도_시_wallet_적립을_다시_시도한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED))
                .thenReturn(new WalletRewardResult(110, 9001L));

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_REWARD_FAILED);

        SubmitCompletionAnswerResponse retried = missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        );
        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionRewardOutbox savedOutbox = missionRewardOutboxRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();

        assertThat(retried.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(retried.getWallet().getStarPiece()).isEqualTo(110);
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionRewardOutboxStatus.SUCCEEDED);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(1);
        verify(walletRewardClient, times(2)).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
    }

    @Test
    void 보상_outbox_스케줄러는_PENDING_보상을_같은_멱등키로_재처리한다() {
        CreateNextMissionResponse created = missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);
        missionService.startCompletionSession(USER_ID, created.getMission().getId());
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new MissionException(MissionErrorCode.MISSION_REWARD_FAILED))
                .thenReturn(new WalletRewardResult(110, 9001L));

        assertThatThrownBy(() -> missionService.submitCompletionAnswer(
                USER_ID,
                created.getMission().getId(),
                "완료했어"
        ))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_REWARD_FAILED);

        MissionRewardOutbox pendingOutbox = missionRewardOutboxRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        ReflectionTestUtils.setField(pendingOutbox, "nextAttemptAt", LocalDateTime.now().minusSeconds(1));
        missionRewardOutboxRepository.saveAndFlush(pendingOutbox);

        int succeededCount = missionRewardDispatcher.dispatchDue(10);

        UserMission savedMission = userMissionRepository.findById(created.getMission().getId()).orElseThrow();
        MissionRewardOutbox savedOutbox = missionRewardOutboxRepository
                .findByMissionId(created.getMission().getId())
                .orElseThrow();
        assertThat(succeededCount).isEqualTo(1);
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        assertThat(savedOutbox.getStatus()).isEqualTo(MissionRewardOutboxStatus.SUCCEEDED);
        assertThat(savedOutbox.getAttemptCount()).isEqualTo(1);
        verify(walletRewardClient, times(2)).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
    }
}
