package p5laris.ai.domain.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.ai.domain.application.dto.CharacterTalkDiariesResult;
import p5laris.ai.domain.application.dto.CharacterTalkDiaryItem;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.dto.CharacterTalkMessageItem;
import p5laris.ai.domain.application.dto.CharacterTalkMessagesResult;
import p5laris.ai.domain.application.dto.PreparedCharacterTalkContext;
import p5laris.ai.domain.application.dto.TextEmbeddingCommand;
import p5laris.ai.domain.application.dto.TextEmbeddingResult;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.memory.CharacterTalkDiarySummary;
import p5laris.ai.domain.application.memory.CharacterTalkMemoryHit;
import p5laris.ai.domain.application.memory.CharacterTalkSessionSummary;
import p5laris.common.utils.EmbeddingVectorUtils;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkSessionStatus;
import p5laris.ai.domain.domain.repository.CharacterTalkMessageRepository;
import p5laris.ai.domain.domain.repository.CharacterTalkSessionRepository;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.config.AiEmbeddingProperties;
import p5laris.ai.domain.infrastructure.repository.CharacterTalkMemoryJdbcRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 蹂꾩튇援???붿쓽 硫?고꽩 ?몄뀡, 理쒓렐 ???window, ?κ린 湲곗뼲 寃?됱쓣 ?대떦?쒕떎.
 *
 * DB 湲곕줉怨??몃? AI provider ?몄텧??遺꾨━??????앹꽦 ?몃옖??뀡??湲몄뼱吏吏 ?딄쾶 ?쒕떎.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterTalkHistoryService {

    private static final int MAX_DIARY_RANGE_DAYS = 31;

    private final CharacterTalkSessionRepository sessionRepository;
    private final CharacterTalkMessageRepository messageRepository;
    private final CharacterTalkMemoryJdbcRepository memoryJdbcRepository;
    private final CharacterTalkMessageWriter messageWriter;
    private final CharacterTalkSessionSummarizer sessionSummarizer;
    private final AiTextEmbeddingService textEmbeddingService;
    private final AiCharacterTalkProperties properties;
    private final AiEmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PreparedCharacterTalkContext prepare(CharacterTalkGenerationCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        summarizeExpiredSessions(command, now);

        SessionResolution resolution = resolveSession(command, now);
        CharacterTalkSession session = resolution.session();
        LocalDateTime nextExpiresAt = now.plusMinutes(properties.normalizedSessionTtlMinutes());

        List<CharacterTalkMessage> previousMessages = findPromptWindowMessages(session);
        List<CharacterTalkMemoryHit> memoryHits = searchMemories(command);
        String historyJson = toHistoryJson(previousMessages);
        String memoryJson = toMemoryJson(memoryHits);

        messageWriter.recordUserMessage(session.getId(), command, now, nextExpiresAt);

        return new PreparedCharacterTalkContext(
                session,
                session.getSessionId(),
                resolution.newSession(),
                nextExpiresAt,
                historyJson,
                memoryJson,
                properties.normalizedHistoryMaxTurns(),
                properties.normalizedMemorySearchTopK(),
                memoryHits.size()
        );
    }

    public void recordAssistantResponse(
            PreparedCharacterTalkContext context,
            CharacterTalkGenerationCommand command,
            String assistantText,
            boolean fallbackUsed,
            AiTokenUsage tokenUsage
    ) {
        if (context == null || assistantText == null || assistantText.isBlank()) {
            return;
        }

        messageWriter.recordAssistantResponse(
                context.session().getId(),
                command,
                assistantText,
                fallbackUsed,
                tokenUsage
        );
    }

    @Transactional(readOnly = true)
    public CharacterTalkMessagesResult getDailyMessages(Long userId, Long characterId, String dateText) {
        LocalDate date = parseDateOrDefault(dateText, LocalDate.now(clock));
        LocalDateTime startAt = date.atStartOfDay();
        LocalDateTime endAt = date.plusDays(1).atStartOfDay();
        List<CharacterTalkMessage> messages = messageRepository.findDailyMessages(
                userId,
                characterId,
                startAt,
                endAt
        );
        List<CharacterTalkMessageItem> items = messages.stream()
                .map(this::toMessageItem)
                .toList();
        String latestSessionId = messages.isEmpty()
                ? ""
                : messages.get(messages.size() - 1).getSession().getSessionId();
        return new CharacterTalkMessagesResult(characterId, date, latestSessionId, items);
    }

    @Transactional(readOnly = true)
    public CharacterTalkDiariesResult getDiaries(
            Long userId,
            Long characterId,
            String fromDateText,
            String toDateText
    ) {
        LocalDate toDate = parseDateOrDefault(toDateText, LocalDate.now(clock));
        LocalDate fromDate = parseDateOrDefault(fromDateText, toDate.minusDays(6));
        if (fromDate.isAfter(toDate)) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_DIARY_RANGE_DAYS) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }

        List<CharacterTalkDiaryItem> items = memoryJdbcRepository
                .findDiarySummaries(userId, characterId, fromDate, toDate)
                .stream()
                .map(this::toDiaryItem)
                .toList();
        return new CharacterTalkDiariesResult(characterId, fromDate, toDate, items);
    }

    public void cleanupExpiredData() {
        if (!properties.isCleanupEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int summarizedSessions = summarizeExpiredSessions(now);
        LocalDateTime messageCutoff = now.minusHours(properties.normalizedMessageRetentionHours());
        LocalDateTime sessionCutoff = now.minusDays(properties.normalizedSessionRetentionDays());
        int deletedMessages = messageRepository.deleteMessagesBefore(
                messageCutoff,
                CharacterTalkSessionStatus.ACTIVE
        );
        int deletedSessions = sessionRepository.deleteClosedSessionsBefore(
                CharacterTalkSessionStatus.ACTIVE,
                sessionCutoff
        );
        if (summarizedSessions > 0 || deletedMessages > 0 || deletedSessions > 0) {
            log.info("蹂꾩튇援????蹂닿? ?뺤콉 ?뺣━ ?꾨즺. summarizedSessions={}, deletedMessages={}, deletedSessions={}",
                    summarizedSessions, deletedMessages, deletedSessions);
        }
    }

    public void summarizeDailyBoundarySessions() {
        if (!properties.isCleanupEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int summarizedSessions = summarizeExpiredSessions(now);
        if (summarizedSessions > 0) {
            log.info("별친구 대화 일일 기억 생성 완료. summarizedSessions={}", summarizedSessions);
        }
    }

    private void summarizeExpiredSessions(CharacterTalkGenerationCommand command, LocalDateTime now) {
        LocalDateTime todayStart = startOfDay(now);
        List<CharacterTalkSession> sessions = sessionRepository
                .findSummarizableSessionsForCharacter(
                        command.userId(),
                        command.characterId(),
                        CharacterTalkSessionStatus.ACTIVE,
                        now,
                        todayStart,
                        PageRequest.of(0, 10)
                );
        for (CharacterTalkSession session : sessions) {
            summarizeSession(session);
        }
    }

    private int summarizeExpiredSessions(LocalDateTime now) {
        LocalDateTime todayStart = startOfDay(now);
        List<CharacterTalkSession> sessions = sessionRepository
                .findSummarizableSessions(
                        CharacterTalkSessionStatus.ACTIVE,
                        now,
                        todayStart,
                        PageRequest.of(0, 10)
                );
        int summarized = 0;
        for (CharacterTalkSession session : sessions) {
            if (summarizeSession(session)) {
                summarized += 1;
            }
        }
        return summarized;
    }

    private boolean summarizeSession(CharacterTalkSession session) {
        try {
            List<CharacterTalkMessage> messages = messageRepository.findBySessionIdOrderBySequenceAsc(session.getId());
            CharacterTalkSessionSummary summary = sessionSummarizer.summarize(session, messages);
            if (summary.isBlank()) {
                markExpired(session);
                return false;
            }

            String contextSummary = summary.resolvedContextSummary();
            String diaryText = summary.resolvedDiaryText();
            TextEmbeddingResult embedding = textEmbeddingService.generateTextEmbedding(new TextEmbeddingCommand(
                    session.getUserId(),
                    contextSummary,
                    embeddingProperties.resolvedModel(),
                    embeddingProperties.getDimension(),
                    "character-talk-memory-" + session.getSessionId()
            ));
            List<Float> normalized = EmbeddingVectorUtils.normalize(embedding.values(), embedding.dimension());
            memoryJdbcRepository.upsertSessionSummary(
                    session.getUserId(),
                    session.getCharacterId(),
                    session.getId(),
                    contextSummary,
                    diaryText,
                    embedding.model(),
                    embedding.dimension(),
                    normalized
            );
            markMemoryReady(session);
            return true;
        } catch (Exception e) {
            log.warn("蹂꾩튇援?????몄뀡 湲곗뼲???ㅽ뙣. sessionId={}, characterId={}, ?덉쇅?대옒??{}",
                    session.getSessionId(), session.getCharacterId(), e.getClass().getSimpleName());
            markExpired(session);
            return false;
        }
    }

    protected void markExpired(CharacterTalkSession session) {
        session.markExpired();
        sessionRepository.save(session);
    }

    protected void markMemoryReady(CharacterTalkSession session) {
        session.markMemoryReady(LocalDateTime.now(clock));
        sessionRepository.save(session);
    }

    protected SessionResolution resolveSession(CharacterTalkGenerationCommand command, LocalDateTime now) {
        LocalDateTime expiresAt = now.plusMinutes(properties.normalizedSessionTtlMinutes());
        LocalDateTime todayStart = startOfDay(now);
        String requestedSessionId = normalizeText(command.sessionId());
        if (!requestedSessionId.isBlank()) {
            return sessionRepository.findBySessionId(requestedSessionId)
                    .map(session -> resolveRequestedSession(command, session, now, expiresAt, todayStart))
                    .orElseGet(() -> createNewSession(command, now, expiresAt));
        }

        return sessionRepository
                .findReusableSession(
                        command.userId(),
                        command.characterId(),
                        CharacterTalkSessionStatus.ACTIVE,
                        now,
                        todayStart
                )
                .map(session -> {
                    session.refresh(now, expiresAt);
                    return new SessionResolution(sessionRepository.save(session), false);
                })
                .orElseGet(() -> createNewSession(command, now, expiresAt));
    }

    private SessionResolution resolveRequestedSession(
            CharacterTalkGenerationCommand command,
            CharacterTalkSession session,
            LocalDateTime now,
            LocalDateTime expiresAt,
            LocalDateTime todayStart
    ) {
        if (!session.isOwnedBy(command.userId(), command.characterId())) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
        if (session.isActiveAt(now) && session.getStartedAt().isBefore(todayStart)) {
            summarizeSession(session);
            return createNewSession(command, now, expiresAt);
        }
        if (!session.isActiveAt(now)) {
            return createNewSession(command, now, expiresAt);
        }
        session.refresh(now, expiresAt);
        return new SessionResolution(sessionRepository.save(session), false);
    }

    private SessionResolution createNewSession(
            CharacterTalkGenerationCommand command,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        CharacterTalkSession session = CharacterTalkSession.create(
                UUID.randomUUID().toString(),
                command.userId(),
                command.characterId(),
                normalizeCharacterType(command.characterType()),
                now,
                expiresAt
        );
        return new SessionResolution(sessionRepository.save(session), true);
    }

    private List<CharacterTalkMessage> findPromptWindowMessages(CharacterTalkSession session) {
        int maxMessages = properties.normalizedHistoryMaxTurns() * 2;
        List<CharacterTalkMessage> recent = messageRepository.findRecentMessages(
                session.getId(),
                PageRequest.of(0, maxMessages)
        );
        List<CharacterTalkMessage> selected = new ArrayList<>(recent);
        selected.sort(Comparator.comparingInt(CharacterTalkMessage::getSequence));
        return selected;
    }

    private List<CharacterTalkMemoryHit> searchMemories(CharacterTalkGenerationCommand command) {
        int topK = properties.normalizedMemorySearchTopK();
        if (topK <= 0 || command.userMessage() == null || command.userMessage().isBlank()) {
            return List.of();
        }
        try {
            TextEmbeddingResult embedding = textEmbeddingService.generateTextEmbedding(new TextEmbeddingCommand(
                    command.userId(),
                    command.userMessage(),
                    embeddingProperties.resolvedModel(),
                    embeddingProperties.getDimension(),
                    "character-talk-memory-query-" + command.requestId()
            ));
            List<Float> normalized = EmbeddingVectorUtils.normalize(embedding.values(), embedding.dimension());
            return memoryJdbcRepository.searchSimilar(
                    command.userId(),
                    command.characterId(),
                    embedding.model(),
                    embedding.dimension(),
                    normalized,
                    topK,
                    properties.normalizedMemorySimilarityThreshold()
            );
        } catch (Exception e) {
            log.warn("蹂꾩튇援?????κ린 湲곗뼲 寃???ㅽ뙣. requestId={}, characterId={}, ?덉쇅?대옒??{}",
                    command.requestId(), command.characterId(), e.getClass().getSimpleName());
            return List.of();
        }
    }

    private String toHistoryJson(List<CharacterTalkMessage> messages) {
        List<Map<String, Object>> body = messages.stream()
                .map(message -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("role", message.getRole().name().toLowerCase(Locale.ROOT));
                    item.put("content", message.getContent());
                    item.put("sequence", message.getSequence());
                    return item;
                })
                .toList();
        return toJson(body);
    }

    private CharacterTalkMessageItem toMessageItem(CharacterTalkMessage message) {
        return new CharacterTalkMessageItem(
                message.getRole().name().toLowerCase(Locale.ROOT),
                message.getContent(),
                message.getSequence(),
                message.getRequestId(),
                message.isFallbackUsed(),
                message.getCreatedAt(),
                message.getSession().getSessionId()
        );
    }

    private CharacterTalkDiaryItem toDiaryItem(CharacterTalkDiarySummary summary) {
        return new CharacterTalkDiaryItem(
                summary.date(),
                summary.summary(),
                summary.sourceSessionId(),
                summary.createdAt()
        );
    }

    private String toMemoryJson(List<CharacterTalkMemoryHit> hits) {
        List<Map<String, Object>> body = hits.stream()
                .map(hit -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("summary", hit.summary());
                    item.put("similarity", Math.max(0.0d, 1.0d - hit.distance()));
                    item.put("createdAt", hit.createdAt().toString());
                    return item;
                })
                .toList();
        return toJson(body);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String normalizeCharacterType(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDate parseDateOrDefault(String value, LocalDate defaultDate) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            return defaultDate;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
    }

    private LocalDateTime startOfDay(LocalDateTime now) {
        return now.toLocalDate().atStartOfDay();
    }

    private record SessionResolution(CharacterTalkSession session, boolean newSession) {
    }
}
