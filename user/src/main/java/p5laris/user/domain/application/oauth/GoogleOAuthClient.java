package p5laris.user.domain.application.oauth;

public interface GoogleOAuthClient {

    GoogleUserProfile fetchUserProfile(String code, String redirectUri, String clientId, boolean includeClientSecret);

    record GoogleUserProfile(String email, String name) {
    }
}
