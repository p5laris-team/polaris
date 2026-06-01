package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.AuthService;
import p5laris.user.domain.application.UserService;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcController extends UserServiceGrpc.UserServiceImplBase {

    private final AuthService authService;
    private final UserService userService;

    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getGoogleAuthUrl(GetGoogleAuthUrlRequest request, StreamObserver<GetGoogleAuthUrlResponse> responseObserver) {
        try {
            String state = "oauth-state-token";
            String url = authService.getGoogleAuthUrl(request.getRedirectUri(), state);
            
            GetGoogleAuthUrlResponse response = GetGoogleAuthUrlResponse.newBuilder()
                    .setAuthorizationUrl(url)
                    .setState(state)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void loginGoogle(LoginGoogleRequest request, StreamObserver<LoginGoogleResponse> responseObserver) {
        try {
            AuthService.LoginResult result = authService.loginGoogle(request.getCode(), request.getRedirectUri(), request.getClientId());
            
            User protoUser = User.newBuilder()
                    .setId(result.user().getId())
                    .setEmail(result.user().getEmail())
                    .setNickname(result.user().getNickname())
                    .setProvider(result.user().getProvider())
                    .setRole(result.user().getRole())
                    .setStatus(result.user().getStatus())
                    .build();

            LoginGoogleResponse response = LoginGoogleResponse.newBuilder()
                    .setAccessToken(result.accessToken())
                    .setRefreshToken(result.refreshToken())
                    .setUser(protoUser)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<RefreshTokenResponse> responseObserver) {
        try {
            AuthService.RefreshResult result = authService.refreshToken(request.getRefreshToken());
            
            RefreshTokenResponse response = RefreshTokenResponse.newBuilder()
                    .setAccessToken(result.accessToken())
                    .setRefreshToken(result.refreshToken())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        try {
            authService.logout(request.getUserId(), request.getAccessToken());
            
            LogoutResponse response = LogoutResponse.newBuilder()
                    .setLoggedOut(true)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> responseObserver) {
        try {
            User protoUser = userService.getUser(request.getUserId());

            GetUserResponse response = GetUserResponse.newBuilder()
                    .setUser(protoUser)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listWeatherRegions(ListWeatherRegionsRequest request, StreamObserver<ListWeatherRegionsResponse> responseObserver) {
        try {
            responseObserver.onNext(userService.listWeatherRegions());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getWeatherRegion(GetWeatherRegionRequest request, StreamObserver<GetWeatherRegionResponse> responseObserver) {
        try {
            responseObserver.onNext(userService.getWeatherRegion(request.getUserId()));
            responseObserver.onCompleted();
        } catch (UserException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void updateWeatherRegion(UpdateWeatherRegionRequest request, StreamObserver<UpdateWeatherRegionResponse> responseObserver) {
        try {
            responseObserver.onNext(userService.updateWeatherRegion(request.getUserId(), request.getRegionCode()));
            responseObserver.onCompleted();
        } catch (UserException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private Status toStatus(UserException e) {
        if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
            return Status.NOT_FOUND;
        }
        if (e.getErrorCode() == UserErrorCode.INVALID_WEATHER_REGION) {
            return Status.INVALID_ARGUMENT;
        }
        return Status.INTERNAL;
    }
}
