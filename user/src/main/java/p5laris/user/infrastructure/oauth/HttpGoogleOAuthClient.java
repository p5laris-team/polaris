package p5laris.user.infrastructure.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import p5laris.user.domain.application.oauth.GoogleOAuthClient;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class HttpGoogleOAuthClient implements GoogleOAuthClient {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String DEFAULT_NAME = "\uBCC4\uB530\uB77C\uAC77\uAE30";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String clientSecret;
    private final Duration requestTimeout;

    public HttpGoogleOAuthClient(
            ObjectMapper objectMapper,
            @Value("${oauth.google.client-secret}") String clientSecret,
            @Value("${oauth.google.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${oauth.google.request-timeout-ms:3000}") long requestTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.clientSecret = clientSecret;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public GoogleUserProfile fetchUserProfile(String code, String redirectUri, String clientId, boolean includeClientSecret) {
        String accessToken = requestAccessToken(code, redirectUri, clientId, includeClientSecret);
        return requestUserProfile(accessToken);
    }

    private String requestAccessToken(String code, String redirectUri, String clientId, boolean includeClientSecret) {
        StringBuilder body = new StringBuilder()
                .append("code=").append(encode(code))
                .append("&client_id=").append(encode(clientId))
                .append("&redirect_uri=").append(encode(redirectUri))
                .append("&grant_type=authorization_code");

        if (includeClientSecret) {
            body.append("&client_secret=").append(encode(clientSecret));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GOOGLE_TOKEN_URL))
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        JsonNode tokenNode = sendJson(request, "token");
        JsonNode accessTokenNode = tokenNode.get("access_token");
        if (accessTokenNode == null || accessTokenNode.asText().isBlank()) {
            throw new UserException(UserErrorCode.INVALID_OAUTH_CODE);
        }
        return accessTokenNode.asText();
    }

    private GoogleUserProfile requestUserProfile(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GOOGLE_USERINFO_URL))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        JsonNode userNode = sendJson(request, "userinfo");
        JsonNode emailNode = userNode.get("email");
        if (emailNode == null || emailNode.asText().isBlank()) {
            throw new UserException(UserErrorCode.INVALID_OAUTH_CODE);
        }

        String name = userNode.hasNonNull("name") ? userNode.get("name").asText() : DEFAULT_NAME;
        return new GoogleUserProfile(emailNode.asText(), name);
    }

    private JsonNode sendJson(HttpRequest request, String apiName) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Google OAuth {} API failed. status={}, body={}", apiName, response.statusCode(), response.body());
                throw new UserException(UserErrorCode.INVALID_OAUTH_CODE);
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UserException(UserErrorCode.INVALID_OAUTH_CODE);
        } catch (IOException e) {
            log.warn("Google OAuth {} API request failed: {}", apiName, e.getMessage());
            throw new UserException(UserErrorCode.INVALID_OAUTH_CODE);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
