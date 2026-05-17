package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.GetUserRequest;
import com.p5laris.proto.user.v1.GetUserResponse;
import com.p5laris.proto.user.v1.User;
import com.p5laris.proto.user.v1.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.user.api.dto.UserDto;

@Service
public class UserGatewayService {

    @GrpcClient("user")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    // 내 정보 조회
    public UserDto getUser(Long userId) {
        GetUserResponse response = userServiceStub.getUser(
                GetUserRequest.newBuilder()
                        .setUserId(userId)
                        .build()
        );

        User protoUser = response.getUser();
        return UserDto.builder()
                .id(protoUser.getId())
                .email(protoUser.getEmail())
                .nickname(protoUser.getNickname())
                .provider(protoUser.getProvider())
                .role(protoUser.getRole())
                .status(protoUser.getStatus())
                .build();
    }
}
