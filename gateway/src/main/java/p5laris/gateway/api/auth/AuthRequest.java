package p5laris.gateway.api.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

public class AuthRequest {
    @Getter
    @Setter
    public static class GoogleAuthUrl {
        @NotBlank
        private String redirectUri;
    }

    @Getter
    public static class LoginGoogle {
        @NotBlank
        private String code;
        @NotBlank
        private String state;
        @NotBlank
        private String redirectUri;
    }

    @Getter
    public static class RefreshToken {
        @NotBlank
        private String refreshToken;
    }
}
