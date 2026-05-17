package p5laris.gateway.api.auth;

import com.p5laris.proto.user.v1.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.web.bind.annotation.*;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/auth/v1")
@RequiredArgsConstructor
public class AuthController {

    @GrpcClient("user")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @GetMapping("/google/authorization-url")
    public ApiResponse<AuthResponse.GoogleAuthUrl> getAuthorizationUrl(@Valid @ModelAttribute AuthRequest.GoogleAuthUrl request) {
        GetGoogleAuthUrlRequest grpcRequest = GetGoogleAuthUrlRequest.newBuilder()
                .setRedirectUri(request.getRedirectUri())
                .build();

        GetGoogleAuthUrlResponse grpcResponse = userServiceStub.getGoogleAuthUrl(grpcRequest);

        return ApiResponse.success(AuthResponse.GoogleAuthUrl.builder()
                .authorizationUrl(grpcResponse.getAuthorizationUrl())
                .state(grpcResponse.getState())
                .build());
    }

    @PostMapping("/google/sessions")
    public ApiResponse<AuthResponse.LoginGoogle> loginGoogle(@Valid @RequestBody AuthRequest.LoginGoogle request) {
        LoginGoogleRequest grpcRequest = LoginGoogleRequest.newBuilder()
                .setCode(request.getCode())
                .setState(request.getState())
                .setRedirectUri(request.getRedirectUri())
                .build();

        LoginGoogleResponse grpcResponse = userServiceStub.loginGoogle(grpcRequest);

        User protoUser = grpcResponse.getUser();
        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(protoUser.getId())
                .email(protoUser.getEmail())
                .nickname(protoUser.getNickname())
                .provider(protoUser.getProvider())
                .role(protoUser.getRole())
                .build();

        return ApiResponse.success(AuthResponse.LoginGoogle.builder()
                .accessToken(grpcResponse.getAccessToken())
                .refreshToken(grpcResponse.getRefreshToken())
                .user(userDto)
                .build());
    }

    @PostMapping("/token-refreshes")
    public ApiResponse<AuthResponse.RefreshToken> refreshToken(@Valid @RequestBody AuthRequest.RefreshToken request) {
        RefreshTokenRequest grpcRequest = RefreshTokenRequest.newBuilder()
                .setRefreshToken(request.getRefreshToken())
                .build();

        RefreshTokenResponse grpcResponse = userServiceStub.refreshToken(grpcRequest);

        return ApiResponse.success(AuthResponse.RefreshToken.builder()
                .accessToken(grpcResponse.getAccessToken())
                .refreshToken(grpcResponse.getRefreshToken())
                .build());
    }

    @DeleteMapping("/sessions/current")
    public ApiResponse<AuthResponse.Logout> logout(@LoginUserId Long userId) {
        LogoutRequest grpcRequest = LogoutRequest.newBuilder()
                .setUserId(userId)
                .build();

        LogoutResponse grpcResponse = userServiceStub.logout(grpcRequest);

        return ApiResponse.success(AuthResponse.Logout.builder()
                .loggedOut(grpcResponse.getLoggedOut())
                .build());
    }
}
