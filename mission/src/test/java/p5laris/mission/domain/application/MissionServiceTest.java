package p5laris.mission.domain.application;

import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionTemplateRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private MissionTemplateRepository missionTemplateRepository;

    @BeforeEach
    void setUp() {
        userMissionRepository.deleteAll();
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
}
