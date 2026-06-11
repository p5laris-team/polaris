package p5laris.gateway.domain.mission.infrastructure.grpc;

import com.p5laris.proto.mission.v1.CreateNextMissionRequest;
import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.GetCurrentMissionRequest;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.GetTodayMissionsRequest;
import com.p5laris.proto.mission.v1.GetTodayMissionsResponse;
import com.p5laris.proto.mission.v1.Mission;
import com.p5laris.proto.mission.v1.MissionCategory;
import com.p5laris.proto.mission.v1.MissionDifficulty;
import com.p5laris.proto.mission.v1.MissionServiceGrpc;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionRequest;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.TodayMission;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.exception.MissionGatewayErrorCode;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionGatewayServiceTest {

    private MissionServiceGrpc.MissionServiceBlockingStub stub;
    private MissionGatewayService service;

    @BeforeEach
    void setUp() {
        stub = mock(MissionServiceGrpc.MissionServiceBlockingStub.class);
        service = new MissionGatewayService();
        ReflectionTestUtils.setField(service, "missionStub", stub);
    }

    @Test
    void mapsCurrentAndCreatedMissionResponses() {
        Mission mission = mission(11L, MissionStatus.MISSION_STATUS_OFFERED);
        when(stub.getCurrentMission(any())).thenReturn(
                GetCurrentMissionResponse.newBuilder().setMission(mission).build()
        );
        when(stub.createNextMission(any())).thenReturn(
                CreateNextMissionResponse.newBuilder().setMission(mission).build()
        );

        var current = service.getCurrentMission(7L);
        var created = service.createNextMission(
                7L, new MissionDto.CreateNextMissionRequest(3L, null)
        );

        assertThat(current.id()).isEqualTo(11L);
        assertThat(current.category()).isEqualTo("BASIC_ROUTINE");
        assertThat(current.difficulty()).isEqualTo("EASY");
        assertThat(current.status()).isEqualTo("OFFERED");
        assertThat(created.title()).isEqualTo("drink water");

        verify(stub).getCurrentMission(
                GetCurrentMissionRequest.newBuilder().setUserId(7L).build()
        );
        verify(stub).createNextMission(
                CreateNextMissionRequest.newBuilder()
                        .setUserId(7L)
                        .setCharacterId(3L)
                        .setLastMissionId(0L)
                        .build()
        );
    }

    @Test
    void mapsMissionHistoryAndOptionalFields() {
        TodayMission todayMission = TodayMission.newBuilder()
                .setId(11L)
                .setStackOrder(1)
                .setTitle("drink water")
                .setCategory(MissionCategory.MISSION_CATEGORY_BASIC_ROUTINE)
                .setDifficulty(MissionDifficulty.MISSION_DIFFICULTY_EASY)
                .setRewardStarPiece(10)
                .setStatus(MissionStatus.MISSION_STATUS_COMPLETED)
                .setCharacterMessage("good")
                .setCreatedAt("created")
                .setHasAnswer(true)
                .setAnswerPreview("done")
                .build();
        when(stub.getTodayMissions(any())).thenReturn(GetTodayMissionsResponse.newBuilder()
                .setMissionDate("2026-06-11")
                .setMaxDailyOffers(5)
                .setOfferedCount(2)
                .setCompletedCount(1)
                .setRemainingOfferCount(3)
                .setMaxDailyRewardCount(20)
                .setCompletedRewardCount(1)
                .setRemainingRewardCount(19)
                .setMaxDailyRejectCount(10)
                .setRemainingRejectCount(9)
                .setCurrentMissionId(11L)
                .addMissions(todayMission)
                .build());

        var response = service.getMissionHistory(7L, LocalDate.of(2026, 6, 11));

        assertThat(response.currentMissionId()).isEqualTo(11L);
        assertThat(response.missions()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("COMPLETED");
            assertThat(item.completedAt()).isNull();
            assertThat(item.answerPreview()).isEqualTo("done");
        });
        ArgumentCaptor<GetTodayMissionsRequest> captor =
                ArgumentCaptor.forClass(GetTodayMissionsRequest.class);
        verify(stub).getTodayMissions(captor.capture());
        assertThat(captor.getValue().getMissionDate()).isEqualTo("2026-06-11");
    }

    @Test
    void mapsRejectMissionRequestAndResponse() {
        when(stub.rejectMission(any())).thenReturn(RejectMissionResponse.newBuilder()
                .setMissionId(11L)
                .setStatus(MissionStatus.MISSION_STATUS_REJECTED)
                .setRejectedAt("rejected")
                .setCharacterMessage("next time")
                .build());

        var response = service.rejectMission(
                7L, 11L, new MissionDto.RejectMissionRequest("TOO_HARD", "later")
        );

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(stub).rejectMission(
                RejectMissionRequest.newBuilder()
                        .setUserId(7L)
                        .setMissionId(11L)
                        .setReasonCode("TOO_HARD")
                        .setReasonText("later")
                        .build()
        );
    }

    @Test
    void validatesRequestsAndMapsGrpcStatuses() {
        assertThatThrownBy(() -> service.createNextMission(7L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.createNextMission(
                7L, new MissionDto.CreateNextMissionRequest(1L, -1L)
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getMissionDetail(7L, 0L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.submitCompletionAnswer(
                7L, 1L, new MissionDto.SubmitCompletionAnswerRequest(" ")
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MissionGatewayErrorCode.MISSION_ANSWER_INVALID);
        assertThatThrownBy(() -> service.upsertMissionFeedback(
                7L, 1L, new MissionDto.UpsertMissionFeedbackRequest("UNKNOWN", null, null, null)
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MissionGatewayErrorCode.MISSION_FEEDBACK_INVALID);

        when(stub.getCurrentMission(any()))
                .thenThrow(Status.NOT_FOUND.withDescription("MISSION_TEMPLATE_NOT_FOUND").asRuntimeException());
        assertThatThrownBy(() -> service.getCurrentMission(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MissionGatewayErrorCode.MISSION_TEMPLATE_NOT_FOUND);

        reset(stub);
        when(stub.getCurrentMission(any()))
                .thenThrow(Status.RESOURCE_EXHAUSTED
                        .withDescription("MISSION_REJECT_LIMIT_EXCEEDED").asRuntimeException());
        assertThatThrownBy(() -> service.getCurrentMission(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MissionGatewayErrorCode.MISSION_REJECT_LIMIT_EXCEEDED);

        reset(stub);
        when(stub.getCurrentMission(any()))
                .thenThrow(Status.UNAVAILABLE.withDescription("MISSION_REWARD_FAILED").asRuntimeException());
        assertThatThrownBy(() -> service.getCurrentMission(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MissionGatewayErrorCode.MISSION_REWARD_FAILED);
    }

    private Mission mission(long id, MissionStatus status) {
        return Mission.newBuilder()
                .setId(id)
                .setMissionDate("2026-06-11")
                .setStackOrder(1)
                .setTitle("drink water")
                .setDescription("one glass")
                .setCharacterMessage("you can do it")
                .setCategory(MissionCategory.MISSION_CATEGORY_BASIC_ROUTINE)
                .setDifficulty(MissionDifficulty.MISSION_DIFFICULTY_EASY)
                .setRewardStarPiece(10)
                .setStatus(status)
                .build();
    }
}
