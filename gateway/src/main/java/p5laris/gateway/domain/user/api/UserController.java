package p5laris.gateway.domain.user.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.user.api.dto.UserDto;
import p5laris.gateway.domain.user.infrastructure.grpc.UserGatewayService;

@RestController
public class UserController {

    private final UserGatewayService userGatewayService;

    public UserController(UserGatewayService userGatewayService) {
        this.userGatewayService = userGatewayService;
    }

    @GetMapping("/api/user")
    public UserDto getUser(UserDto userDto) {
        return new UserDto(userGatewayService.getUser(userDto.msg()));
    }
}
