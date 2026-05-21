package p5laris.character.domain.infrastructure.grpc;

import com.p5laris.proto.mission.v1.MissionServiceGrpc;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.GetTodayMissionsRequest;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MissionShareStatsClient {

    @GrpcClient("mission")
    private MissionServiceGrpc.MissionServiceBlockingStub missionStub;

    public ShareMissionStats getTodayStatsOrDefault(Long userId) {
        try {
            var response = missionStub.getTodayMissions(
                    GetTodayMissionsRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );

            int earnedStarPiece = response.getMissionsList().stream()
                    .filter(mission -> mission.getStatus() == MissionStatus.MISSION_STATUS_COMPLETED)
                    .mapToInt(mission -> mission.getRewardStarPiece())
                    .sum();

            return new ShareMissionStats(response.getCompletedCount(), earnedStarPiece);
        } catch (Exception e) {
            log.warn("Failed to load mission stats for share card. userId={}", userId, e);
            return new ShareMissionStats(0, 0);
        }
    }

    public record ShareMissionStats(int completedCount, int earnedStarPiece) {}
}
