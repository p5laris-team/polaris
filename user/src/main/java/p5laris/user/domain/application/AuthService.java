package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.user.core.auth.JwtProvider;
import p5laris.user.core.auth.TokenBlacklistService;
import p5laris.user.domain.application.event.UserEventLogEvent;
import p5laris.user.domain.application.oauth.GoogleOAuthClient;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.domain.repository.WalletRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final JwtProvider jwtProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApplicationEventPublisher eventPublisher;
    private final GoogleOAuthClient googleOAuthClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${oauth.google.client-id}")
    private String clientId;

    public String getGoogleAuthUrl(String redirectUri, String state) {
        return GOOGLE_AUTH_URL + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=email%20profile"
                + "&state=" + state;
    }

    public LoginResult loginGoogle(String code, String redirectUri, String clientIdParam) {
        try {
            String targetClientId = (clientIdParam != null && !clientIdParam.isBlank()) ? clientIdParam : this.clientId;
            boolean isWebClient = targetClientId.equals(this.clientId);

            GoogleOAuthClient.GoogleUserProfile profile = googleOAuthClient.fetchUserProfile(
                    code,
                    redirectUri,
                    targetClientId,
                    isWebClient
            );

            return transactionTemplate.execute(status -> completeGoogleLogin(profile));
        } catch (UserException e) {
            log.warn("Google login failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Google login failed", e);
            throw new RuntimeException("Google login failed", e);
        }
    }

    private LoginResult completeGoogleLogin(GoogleOAuthClient.GoogleUserProfile profile) {
        Optional<User> existingUser = userRepository.findByEmail(profile.email());
        boolean signedUp = existingUser.isEmpty();

        User user = existingUser.orElseGet(() -> {
            User newUser = User.builder()
                    .email(profile.email())
                    .nickname(profile.name())
                    .provider("GOOGLE")
                    .role("USER")
                    .status("ACTIVE")
                    .build();
            return userRepository.save(newUser);
        });

        if (walletRepository.findByUserId(user.getId()).isEmpty()) {
            walletRepository.save(Wallet.builder().userId(user.getId()).build());
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        user.updateRefreshToken(refreshToken);

        if (signedUp) {
            eventPublisher.publishEvent(UserEventLogEvent.userSignedUp(
                    user.getId(),
                    user.getProvider(),
                    user.getRole(),
                    user.getStatus()
            ));
        }
        eventPublisher.publishEvent(UserEventLogEvent.userLoggedIn(user.getId(), user.getProvider()));

        return new LoginResult(accessToken, refreshToken, user);
    }

    @Transactional
    public RefreshResult refreshToken(String refreshToken) {
        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UserException(UserErrorCode.INVALID_REFRESH_TOKEN));

        String newAccessToken = jwtProvider.generateAccessToken(user.getId());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());

        user.updateRefreshToken(newRefreshToken);

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        userRepository.findById(userId).ifPresent(User::clearRefreshToken);

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                java.util.Date expiration = jwtProvider.getExpiration(accessToken);
                tokenBlacklistService.blacklistToken(accessToken, expiration);
            } catch (Exception e) {
                log.warn("Invalid token during logout: {}", e.getMessage());
            }
        }
    }

    public record LoginResult(String accessToken, String refreshToken, User user) {
    }

    public record RefreshResult(String accessToken, String refreshToken) {
    }
}
