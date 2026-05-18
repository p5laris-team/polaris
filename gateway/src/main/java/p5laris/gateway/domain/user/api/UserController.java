package p5laris.gateway.domain.user.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.user.api.dto.UserDto;
import p5laris.gateway.domain.user.infrastructure.grpc.UserGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserGatewayService userGatewayService;

    /**
     * 내 정보 조회
     * @param userId
     * @return
     */
    @GetMapping("/v1/users/me")
    public ApiResponse<UserDto> getMe(@LoginUserId Long userId) {
        return ApiResponse.success(userGatewayService.getUser(userId));
    }
}
