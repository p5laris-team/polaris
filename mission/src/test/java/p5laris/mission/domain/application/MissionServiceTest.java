package p5laris.mission.domain.application;

import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.CompletionInputType;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.StartCompletionSessionResponse;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionCompletionAnswerRepository;
import p5laris.mission.domain.domain.repository.MissionTemplateRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class MissionServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long CHARACTER_ID = 2001L;
    private static final Long OTHER_CHARACTER_ID = 2002L;

    @Autowired
    private MissionService missionService;

    @Autowired
    private UserMissionRepository userMissionRepository;

    @Autowired
    private MissionCompletionAnswerRepository missionCompletionAnswerRepository;

    @Autowired
    private MissionTemplateRepository missionTemplateRepository;

    @MockitoBean
    private WalletRewardClient walletRewardClient;

    @BeforeEach
    void setUp() {
        missionCompletionAnswerRepository.deleteAll();
        userMissionRepository.deleteAll();
        reset(walletRewardClient);
        when(walletRewardClient.earnMissionReward(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new WalletRewardResult(110, 9001L));
        when(walletRewardClient.getWalletStarPiece(anyLong()))
                .thenReturn(110);
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
        assertThat(current.hasMission()).isTrue();
        assertThat(current.getMission().getId()).isEqualTo(created.getMission().getId());
    }

    @Test
    void 진행_중인_미션이_있으면_다음_미션을_추가로_생성할_수_없다() {
        missionService.createNextMission(USER_ID, CHARACTER_ID, 0L);

        assertThatThrownBy(() -> missionService.createNextMission(USER_ID, CHARACTER_ID, 0L))
                .isInstanceOf(MissionException.class)
                .extracting("errorCode")
                .isEqualTo(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
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
        assertThat(response.getQuestion().getText()).isNotBlank();
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

        assertThat(savedMission.getStatus()).isEqualTo(UserMissionStatus.COMPLETED);
        assertThat(savedMission.getIdempotencyKey()).isNull();
        assertThat(savedAnswer.getAnswerText()).isEqualTo("완료했어");
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

        assertThat(retried.getStatus()).isEqualTo(MissionStatus.MISSION_STATUS_COMPLETED);
        assertThat(retried.getWallet().getStarPiece()).isEqualTo(110);
        assertThat(savedMission.getIdempotencyKey()).isEqualTo("MISSION_REWARD:" + created.getMission().getId());
        verify(walletRewardClient, times(2)).earnMissionReward(
                USER_ID,
                created.getMission().getId(),
                10,
                "MISSION_REWARD:" + created.getMission().getId()
        );
    }
}
