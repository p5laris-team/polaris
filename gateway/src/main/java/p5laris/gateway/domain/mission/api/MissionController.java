package p5laris.gateway.domain.mission.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.infrastructure.grpc.MissionGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

/**
 * mission 도메인을 외부 REST API로 노출하는 gateway 컨트롤러다.
 *
 * <p>실제 미션 상태 전이와 보상 지급은 mission gRPC 서버가 담당하고,
 * 이 컨트롤러는 로그인한 userId를 붙여 내부 gRPC 호출로 전달하는 입구 역할만 한다.</p>
 */
@RestController
@RequestMapping("/api/mission")
@RequiredArgsConstructor
public class MissionController {

    private final MissionGatewayService missionGatewayService;

    /**
     * 로그인한 사용자의 현재 진행 미션 1개를 조회한다.
     */
    @GetMapping("/v1/missions/current")
    public ApiResponse<MissionDto.MissionResponse> getCurrentMission(
            @LoginUserId Long userId
    ) {
        return ApiResponse.success(missionGatewayService.getCurrentMission(userId));
    }

    /**
     * 현재 진행 중인 미션이 없을 때 다음 미션 1개를 생성해 제안한다.
     */
    @PostMapping("/v1/missions/today-focus/next")
    public ApiResponse<MissionDto.MissionResponse> createNextMission(
            @LoginUserId Long userId,
            @Valid @RequestBody MissionDto.CreateNextMissionRequest request
    ) {
        return ApiResponse.success(missionGatewayService.createNextMission(userId, request));
    }

    /**
     * 현재 제안된 미션을 거절 상태로 바꾼다.
     */
    @PostMapping("/v1/missions/{missionId}/rejections")
    public ApiResponse<MissionDto.RejectMissionResponse> rejectMission(
            @LoginUserId Long userId,
            @PathVariable Long missionId
    ) {
        return ApiResponse.success(missionGatewayService.rejectMission(userId, missionId));
    }

    /**
     * 미션 완료 전, 사용자에게 보여줄 완료 질문 세션을 시작한다.
     */
    @PostMapping("/v1/missions/{missionId}/completion-sessions")
    public ApiResponse<MissionDto.CompletionSessionResponse> startCompletionSession(
            @LoginUserId Long userId,
            @PathVariable Long missionId
    ) {
        return ApiResponse.success(missionGatewayService.startCompletionSession(userId, missionId));
    }

    /**
     * 완료 질문 답변을 제출하고 미션 완료 보상을 지급한다.
     */
    @PostMapping("/v1/missions/{missionId}/completion-answers")
    public ApiResponse<MissionDto.CompletionAnswerResponse> submitCompletionAnswer(
            @LoginUserId Long userId,
            @PathVariable Long missionId,
            @Valid @RequestBody MissionDto.SubmitCompletionAnswerRequest request
    ) {
        return ApiResponse.success(missionGatewayService.submitCompletionAnswer(userId, missionId, request));
    }
}
