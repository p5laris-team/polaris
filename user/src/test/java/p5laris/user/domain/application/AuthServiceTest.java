package p5laris.user.domain.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.user.core.auth.JwtProvider;
import p5laris.user.core.auth.TokenBlacklistService;
import p5laris.user.domain.application.oauth.GoogleOAuthClient;
import p5laris.user.domain.domain.entity.User;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.domain.repository.WalletRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    void loginGoogle_fetchesGoogleProfileBeforeTransaction() {
        AuthService authService = authService();
        GoogleOAuthClient.GoogleUserProfile profile =
                new GoogleOAuthClient.GoogleUserProfile("tester@p5laris.life", "tester");
        User user = User.builder()
                .email(profile.email())
                .nickname(profile.name())
                .provider("GOOGLE")
                .role("USER")
                .status("ACTIVE")
                .build();

        when(googleOAuthClient.fetchUserProfile("code", "http://localhost/callback", "web-client", true))
                .thenReturn(profile);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(userRepository.findByEmail(profile.email())).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(null)).thenReturn(Optional.empty());
        when(jwtProvider.generateAccessToken(null)).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(null)).thenReturn("refresh-token");

        AuthService.LoginResult result = authService.loginGoogle("code", "http://localhost/callback", null);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(googleOAuthClient).fetchUserProfile("code", "http://localhost/callback", "web-client", true);
        verify(transactionTemplate).execute(any());
    }

    @Test
    void loginGoogle_doesNotOpenTransactionWhenGoogleClientFails() {
        AuthService authService = authService();
        when(googleOAuthClient.fetchUserProfile(eq("bad-code"), any(), any(), eq(true)))
                .thenThrow(new UserException(UserErrorCode.INVALID_OAUTH_CODE));

        assertThatThrownBy(() -> authService.loginGoogle("bad-code", "http://localhost/callback", null))
                .isInstanceOf(UserException.class);

        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void refreshToken_success() {
        AuthService authService = authService();
        User user = User.builder()
                .email("tester@p5laris.life")
                .build();
        ReflectionTestUtils.setField(user, "id", 123L);

        when(userRepository.findByRefreshToken("valid-token")).thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(123L)).thenReturn("new-access-token");
        when(jwtProvider.generateRefreshToken(123L)).thenReturn("new-refresh-token");

        AuthService.RefreshResult result = authService.refreshToken("valid-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(user.getRefreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void refreshToken_throwsUserException_whenTokenInvalid() {
        AuthService authService = authService();
        when(userRepository.findByRefreshToken("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("invalid-token"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining(UserErrorCode.INVALID_REFRESH_TOKEN.getMessage());
    }

    private AuthService authService() {
        AuthService authService = new AuthService(
                userRepository,
                walletRepository,
                jwtProvider,
                tokenBlacklistService,
                eventPublisher,
                googleOAuthClient,
                transactionTemplate
        );
        ReflectionTestUtils.setField(authService, "clientId", "web-client");
        return authService;
    }
}
