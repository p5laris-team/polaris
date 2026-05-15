package p5laris.gateway.domain.mission.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.mission.api.dto.MissionDto;
import p5laris.gateway.domain.mission.infrastructure.grpc.MissionGatewayService;

@RestController
public class MissionController {

    private final MissionGatewayService missionGatewayService;

    public MissionController(MissionGatewayService missionGatewayService) {
        this.missionGatewayService = missionGatewayService;
    }

    @GetMapping("/api/mission")
    public MissionDto getMission(MissionDto missionDto) {
        return new MissionDto(missionGatewayService.getMission(missionDto.msg()));
    }
}
