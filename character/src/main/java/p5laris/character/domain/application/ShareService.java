package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.domain.entity.ShareCard;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.repository.ShareCardRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.grpc.MissionShareStatsClient;
import software.amazon.awssdk.core.exception.SdkException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Share service handling §9 share APIs.
 *
 * ShareCard / ShareLog entities live in the character module.
 * Wallet reward (star piece) is handled via:
 * TODO [Wallet Domain Integration]: call wallet service to credit star pieces on share reward.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private static final int SHARE_REWARD_STAR_PIECE = 10;
    private static final String PLACEHOLDER_HEADLINE = "Today, I shone a little.";
    private static final int HEADLINE_MAX_LENGTH = 100;
    private static final ZoneId SHARE_DATE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SHARE_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ShareCardRepository shareCardRepository;
    private final ShareLogRepository shareLogRepository;
    private final S3StorageService s3StorageService;
    private final ShareCardImageGenerator shareCardImageGenerator;
    private final MissionShareStatsClient missionShareStatsClient;
    private final UserCharacterRepository userCharacterRepository;

    @Value("${app.public-base-url:https://p5laris.life}")
    private String publicBaseUrl;

    // ---------- §9.1 CreateShareCard ----------

    @Transactional
    public ShareCardResult createShareCard(Long userId, Long characterId, String headline, String imageUrl) {
        String safeHeadline = normalizeAndValidateHeadline(headline);

        UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));
        if (!character.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        String resolvedImageKey = resolveShareCardImageKey(userId, character, safeHeadline, imageUrl);

        return shareCardRepository.findByUserIdAndImageUrl(userId, resolvedImageKey)
                .map(card -> {
                    String shareId = extractShareId(card.getShareUrl());
                    return new ShareCardResult(
                        card.getId(),
                        shareId,
                        s3StorageService.toPublicUrl(card.getImageUrl()),
                        buildShareUrl(shareId));
                })
                .orElseGet(() -> {
                    String shareId = "sh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    
                    // C 방식: 프론트가 전달한 URL 사용

                    ShareCard card = ShareCard.builder()
                            .userId(userId)
                            .characterId(characterId)
                            .imageUrl(resolvedImageKey)
                            .headline(safeHeadline)
                            .shareUrl(shareId)
                            .build();
                    shareCardRepository.save(card);

                    return new ShareCardResult(
                            card.getId(),
                            shareId,
                            s3StorageService.toPublicUrl(resolvedImageKey),
                            buildShareUrl(shareId));
                });
    }

    // ---------- §9.2 GetShareCard ----------

    /**
     * Returns share card detail for the owner.
     * API spec 9.2 GET /api/share/v1/share-cards/{shareCardId}
     */
    @Transactional(readOnly = true)
    public ShareCardDetailResult getShareCard(Long shareCardId, Long userId) {
        ShareCard card = shareCardRepository.findById(shareCardId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_CARD_NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_SHARE_CARD_OWNER);
        }

        UserCharacter character = userCharacterRepository.findById(card.getCharacterId())
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        return new ShareCardDetailResult(
                card.getId(),
                character.getName(),
                s3StorageService.toPublicUrl(card.getImageUrl()),
                buildShareUrl(extractShareId(card.getShareUrl())));
    }

    // ---------- §9.3 CreateShareEvent ----------

    /**
     * Records a share event. Awards daily star piece reward (once per day, idempotency-keyed).
     * API spec 9.3 POST /api/share/v1/share-events
     *
     * Wallet credit:
     * TODO [Wallet Domain Integration]: call wallet service to credit rewardStarPiece if rewardPaid=true.
     * Currently rewardPaid is determined and stored, but no actual credit is made.
     */
    @Transactional
    public ShareEventResult createShareEvent(Long userId, Long shareCardId,
                                             String platform, String shareType,
                                             String idempotencyKey) {
        ShareCard card = shareCardRepository.findById(shareCardId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_CARD_NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_SHARE_CARD_OWNER);
        }

        // Check daily reward eligibility (AGENTS.md §20.5)
        LocalDate today = LocalDate.now(SHARE_DATE_ZONE);
        String rewardIdempotencyKey = buildDailyShareRewardIdempotencyKey(userId, today);

        // Idempotency for daily reward: return previously recorded reward log if exists.
        var existingRewardLog = shareLogRepository.findByIdempotencyKey(rewardIdempotencyKey);
        if (existingRewardLog.isPresent()) {
            ShareLog log = existingRewardLog.get();
            return new ShareEventResult(log.getId(), log.isRewardPaid(), log.getRewardStarPiece(), 0);
        }

        boolean alreadyRewarded = shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(userId, today);
        boolean rewardPaid = !alreadyRewarded;
        int rewardAmount = rewardPaid ? SHARE_REWARD_STAR_PIECE : 0;

        String finalIdempotencyKey = rewardPaid
                ? rewardIdempotencyKey
                : (idempotencyKey != null && !idempotencyKey.isBlank()
                    ? idempotencyKey
                    : "SHARE_EVENT:" + userId + ":" + UUID.randomUUID());

        ShareLog shareLog = ShareLog.builder()
                .userId(userId)
                .characterId(card.getCharacterId())
                .shareCardId(shareCardId)
                .shareType(shareType)
                .platform(platform)
                .sharedAt(Instant.now())
                .shareDate(today)
                .rewardStarPiece(rewardAmount)
                .rewardPaid(rewardPaid)
                .idempotencyKey(finalIdempotencyKey)
                .build();
        shareLogRepository.save(shareLog);

        // TODO [Wallet Domain Integration]: if rewardPaid, call walletService.credit(userId, rewardAmount).
        // Example (uncomment after wallet domain is ready):
        // if (rewardPaid) {
        //     walletService.credit(userId, rewardAmount, "SHARE_REWARD");
        // }

        return new ShareEventResult(shareLog.getId(), rewardPaid, rewardAmount, 0);
    }

    // ---------- §9.4 GetShareLink (Public) ----------

    /**
     * Returns public share card info by shareId (no auth required).
     * API spec 9.4 GET /api/share/v1/share-links/{shareId}
     */
    @Transactional(readOnly = true)
    public ShareLinkResult getShareLink(String shareId) {
        ShareCard card = shareCardRepository.findByShareUrl(shareId)
                .or(() -> shareCardRepository.findByShareUrl(buildShareUrl(shareId)))
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.SHARE_LINK_NOT_FOUND));

        UserCharacter character = userCharacterRepository.findById(card.getCharacterId())
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        return new ShareLinkResult(
                shareId,
                character.getName(),
                s3StorageService.toPublicUrl(card.getImageUrl()),
                card.getHeadline() != null ? card.getHeadline() : PLACEHOLDER_HEADLINE,
                buildSignupUrl(shareId));
    }

    // ---------- §9.5 RecordShareClick (Public) ----------

    /**
     * Records a share link click for analytics (no auth required).
     * API spec 9.5 POST /api/share/v1/share-clicks
     *
     * MVP: simply log and return recorded=true.
     * TODO [Analytics Integration]: persist click log to analytics store if needed.
     */
    public ShareClickResult recordShareClick(String shareId, String referrer,
                                             String utmSource, String utmMedium,
                                             String utmCampaign) {
        log.info("Share click recorded: shareId={}, referrer={}, utm={}/{}/{}",
                shareId, referrer, utmSource, utmMedium, utmCampaign);
        return new ShareClickResult(shareId, true);
    }

    // ---------- Presigned URL 발급 ----------

    public S3StorageService.S3PresignedResult getSharePresignedUrl(Long userId, String extension) {
        return s3StorageService.generatePresignedUrlForShareCard(userId, extension);
    }

    // ---------- Internal helper ----------

    private String normalizeAndValidateHeadline(String headline) {
        String normalized = headline == null ? PLACEHOLDER_HEADLINE : headline.trim();
        if (normalized.isBlank()) {
            normalized = PLACEHOLDER_HEADLINE;
        }
        if (normalized.length() > HEADLINE_MAX_LENGTH) {
            throw new CharacterException(CharacterErrorCode.INVALID_SHARE_HEADLINE);
        }
        return normalized;
    }

    private String resolveShareCardImageKey(Long userId, UserCharacter character, String headline, String imageUrl) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return validateShareCardImageUrlAndExtractKey(userId, imageUrl);
        }
        log.warn("CreateShareCard called without imageUrl. Falling back to server-side image generation. userId={}, characterId={}",
                userId, character.getId());
        return generateAndUploadShareCardImage(userId, character, headline).key();
    }

    private String validateShareCardImageUrlAndExtractKey(Long userId, String imageUrl) {
        try {
            URI uri = URI.create(imageUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String expectedUserPrefix = "/share-cards/" + userId + "/";
            if (!"https".equalsIgnoreCase(scheme)
                    || s3StorageService.publicHost() == null
                    || !s3StorageService.publicHost().equalsIgnoreCase(host)
                    || path == null
                    || !path.startsWith(expectedUserPrefix)
                    || !hasAllowedImageExtension(path)) {
                throw new CharacterException(CharacterErrorCode.INVALID_SHARE_CARD_IMAGE_URL);
            }
            return s3StorageService.toObjectKey(uri.toString());
        } catch (IllegalArgumentException e) {
            throw new CharacterException(CharacterErrorCode.INVALID_SHARE_CARD_IMAGE_URL);
        }
    }

    private boolean hasAllowedImageExtension(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".png")
                || lowerPath.endsWith(".jpg")
                || lowerPath.endsWith(".jpeg")
                || lowerPath.endsWith(".webp");
    }

    private S3StorageService.S3UploadResult generateAndUploadShareCardImage(Long userId, UserCharacter character, String headline) {
        var missionStats = missionShareStatsClient.getTodayStatsOrDefault(userId);
        byte[] imageBytes = shareCardImageGenerator.generate(new ShareCardImageGenerator.ShareCardImageCommand(
                character.getName(),
                character.getCharacterType().getCode(),
                headline,
                missionStats.completedCount(),
                missionStats.earnedStarPiece()
        ));

        try {
            return s3StorageService.uploadShareCardImage(imageBytes, "image/png");
        } catch (SdkException | IllegalStateException e) {
            log.warn("Failed to upload generated share card image. userId={}, characterId={}", userId, character.getId(), e);
            throw new CharacterException(CharacterErrorCode.SHARE_CARD_IMAGE_UPLOAD_FAILED);
        }
    }

    private String extractShareId(String shareUrl) {
        int lastSlash = shareUrl.lastIndexOf('/');
        return lastSlash >= 0 ? shareUrl.substring(lastSlash + 1) : shareUrl;
    }

    private String buildShareUrl(String shareId) {
        return normalizedPublicBaseUrl() + "/share/" + shareId;
    }

    private String buildSignupUrl(String shareId) {
        return normalizedPublicBaseUrl() + "/signup?shareId=" + shareId;
    }

    private String normalizedPublicBaseUrl() {
        String normalized = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "https://p5laris.life"
                : publicBaseUrl.trim();
        return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String buildDailyShareRewardIdempotencyKey(Long userId, LocalDate shareDate) {
        return "SHARE_REWARD:" + userId + ":" + SHARE_DATE_FORMATTER.format(shareDate);
    }

    @Transactional(readOnly = true)
    public TodayShareEventStatusResult getTodayShareEventStatus(Long userId) {
        LocalDate today = LocalDate.now(SHARE_DATE_ZONE);
        boolean rewardClaimed = shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(userId, today);
        String lastSharedAt = shareLogRepository.findTopByUserIdAndShareDateOrderBySharedAtDesc(userId, today)
                .map(ShareLog::getSharedAt)
                .map(Instant::toString)
                .orElse("");
        return new TodayShareEventStatusResult(rewardClaimed, lastSharedAt);
    }

    // ---------- Result records ----------

    public record ShareCardResult(Long shareCardId, String shareId, String imageUrl, String shareUrl) {}
    public record ShareCardDetailResult(Long shareCardId, String characterName, String imageUrl, String shareUrl) {}
    public record ShareEventResult(Long shareEventId, boolean rewardPaid, int rewardStarPiece, int walletStarPiece) {}
    public record ShareLinkResult(String shareId, String characterName, String imageUrl, String headline, String signupUrl) {}
    public record ShareClickResult(String shareId, boolean recorded) {}
    public record TodayShareEventStatusResult(boolean rewardClaimed, String lastSharedAt) {}
}
