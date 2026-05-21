package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.domain.entity.ShareCard;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.repository.ShareCardRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.application.R2StorageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final String BASE_SHARE_URL = "https://polaris.app/share/";
    private static final String BASE_SIGNUP_URL = "https://polaris.app/signup?shareId=";
    private static final String PLACEHOLDER_HEADLINE = "Today, I shone a little.";
    private static final ZoneId SHARE_DATE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SHARE_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ShareCardRepository shareCardRepository;
    private final ShareLogRepository shareLogRepository;
    private final R2StorageService r2StorageService;
    private final UserCharacterRepository userCharacterRepository;

    // ---------- §9.1 CreateShareCard ----------

    /**
     * Creates (or returns existing) share card for a user's character.
     * API spec 9.1 POST /api/share/v1/share-cards
     *
     * Option C: Frontend uploads image and passes imageUrl.
     * Idempotent: if a card already exists for (userId, characterId), update imageUrl and return it.
     */
    @Transactional
    public ShareCardResult createShareCard(Long userId, Long characterId, String uploadedImageUrl) {
        // Validate character ownership
        UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));
        if (!character.getUserId().equals(userId)) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        // Idempotent: reuse existing card for same (userId, characterId) and update image if provided
        return shareCardRepository.findByUserIdAndCharacterId(userId, characterId)
                .map(card -> {
                    if (uploadedImageUrl != null && !uploadedImageUrl.isBlank()
                            && (card.getImageUrl() == null || !uploadedImageUrl.equals(card.getImageUrl()))) {
                        card.updateImageUrl(uploadedImageUrl);
                    }
                    return new ShareCardResult(
                        card.getId(),
                        extractShareId(card.getShareUrl()),
                        card.getImageUrl(),
                        card.getShareUrl());
                })
                .orElseGet(() -> {
                    String shareId = "sh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    String shareUrl = BASE_SHARE_URL + shareId;
                    
                    // C 방식: 프론트가 전달한 URL 사용
                    String finalImageUrl = (uploadedImageUrl != null && !uploadedImageUrl.isEmpty()) 
                                            ? uploadedImageUrl 
                                            : "https://cdn.polaris.app/share-cards/placeholder.png";

                    ShareCard card = ShareCard.builder()
                            .userId(userId)
                            .characterId(characterId)
                            .imageUrl(finalImageUrl)
                            .shareUrl(shareUrl)
                            .build();
                    shareCardRepository.save(card);

                    return new ShareCardResult(card.getId(), shareId, finalImageUrl, shareUrl);
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
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.SHARE_CARD_NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.NOT_SHARE_CARD_OWNER);
        }

        UserCharacter character = userCharacterRepository.findById(card.getCharacterId())
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));

        return new ShareCardDetailResult(
                card.getId(),
                character.getName(),
                card.getImageUrl(),
                card.getShareUrl());
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
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.SHARE_CARD_NOT_FOUND));
        if (!card.getUserId().equals(userId)) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.NOT_SHARE_CARD_OWNER);
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
        String shareUrl = BASE_SHARE_URL + shareId;
        ShareCard card = shareCardRepository.findByShareUrl(shareUrl)
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.SHARE_LINK_NOT_FOUND));

        UserCharacter character = userCharacterRepository.findById(card.getCharacterId())
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));

        return new ShareLinkResult(
                shareId,
                character.getName(),
                card.getImageUrl(),
                PLACEHOLDER_HEADLINE,
                BASE_SIGNUP_URL + shareId);
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

    public R2StorageService.R2PresignedResult getSharePresignedUrl(Long userId, String extension) {
        return r2StorageService.generatePresignedUrlForShareCard(extension);
    }

    // ---------- Internal helper ----------

    private String extractShareId(String shareUrl) {
        int lastSlash = shareUrl.lastIndexOf('/');
        return lastSlash >= 0 ? shareUrl.substring(lastSlash + 1) : shareUrl;
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
