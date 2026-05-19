package p5laris.ai.domain.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.dto.MissionTextGenerationResult;
import p5laris.ai.domain.application.generator.MissionTextGenerator;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.enums.AiUsageStatus;
import p5laris.ai.domain.domain.enums.PromptCategory;
import p5laris.ai.domain.domain.policy.MissionTextValidationPolicy;
import p5laris.ai.domain.domain.repository.AiUsageLogRepository;
import p5laris.ai.domain.domain.repository.PromptTemplateRepository;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 미션 문구 생성 유스케이스의 중심 서비스다.
 *
 * 이 서비스는 "어떤 provider를 썼는지"보다 Polaris 정책을 우선한다.
 * seed 미션의 제목/보상/카테고리는 바꾸지 않고, 캐릭터 말투 문구 3개만 생성한 뒤 검증하고 저장한다.
 */
@Service
@RequiredArgsConstructor
public class AiMissionTextService {

    private final MissionTextGenerator missionTextGenerator;
    private final MissionTextValidationPolicy missionTextValidationPolicy;
    private final AiMissionTextPersistenceService aiMissionTextPersistenceService;
    private final PromptTemplateRepository promptTemplateRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiProviderProperties aiProviderProperties;
    private final ObjectMapper objectMapper;

    /**
     * 선택된 seed 미션을 캐릭터 말투의 제안 문구/완료 질문/완료 반응으로 변환한다.
     *
     * 이번 PR에서는 외부 AI를 호출하지 않고 LocalMissionTextGenerator를 사용한다.
     * 다음 PR에서 Gemini/OpenAI generator가 추가되어도 이 메서드는 동일한 결과 DTO만 다루면 된다.
     */
    public MissionTextGenerationResult generateMissionTexts(MissionTextGenerationCommand command) {
        validateRequest(command);
        validateDuplicatedRequest(command.requestId());

        long startedAt = System.nanoTime();
        PromptTemplate promptTemplate = findCharacterTonePromptTemplate();
        MissionTextCandidate candidate;
        AiGenerationStatus status;
        AiUsageStatus usageStatus;
        boolean fallbackUsed;
        AiErrorType errorType = null;

        try {
            candidate = missionTextGenerator.generate(command);
            missionTextValidationPolicy.validate(candidate);
            status = AiGenerationStatus.SUCCESS;
            usageStatus = AiUsageStatus.SUCCESS;
            fallbackUsed = false;
        } catch (FallbackRequiredException e) {
            errorType = e.getErrorType();
            candidate = fallbackCandidate(command);
            validateFallbackCandidate(candidate);
            status = AiGenerationStatus.FALLBACK;
            usageStatus = AiUsageStatus.FALLBACK;
            fallbackUsed = true;
        }

        AiMissionGeneration generation = saveResult(command, promptTemplate, candidate, status, usageStatus, fallbackUsed, startedAt, errorType);

        return new MissionTextGenerationResult(
                generation.getId(),
                status,
                candidate.characterMessage(),
                candidate.completionQuestion(),
                candidate.completionCharacterResponse(),
                fallbackUsed,
                errorType,
                command.requestId()
        );
    }

    // gRPC로 들어온 요청이 AI 문구 생성에 필요한 최소 정보를 모두 갖고 있는지 확인한다.
    private void validateRequest(MissionTextGenerationCommand command) {
        if (command == null
                || isNotPositive(command.userId())
                || isNotPositive(command.characterId())
                || isNotPositive(command.missionTemplateId())
                || isBlank(command.characterType())
                || isBlank(command.baseTitle())
                || isBlank(command.baseDescription())
                || isBlank(command.category())
                || isBlank(command.difficulty())
                || isBlank(command.fallbackCharacterMessage())
                || isBlank(command.fallbackQuestion())
                || isBlank(command.fallbackCompletionResponse())
                || isBlank(command.requestId())) {
            throw new AiException(AiErrorCode.AI_INVALID_REQUEST);
        }
    }

    // request_id는 ai_usage_logs에서 unique로 관리한다. 같은 요청을 두 번 저장하지 않기 위한 사전 검사다.
    private void validateDuplicatedRequest(String requestId) {
        if (aiUsageLogRepository.findByRequestId(requestId).isPresent()) {
            throw new AiException(AiErrorCode.AI_DUPLICATED_REQUEST);
        }
    }

    // 현재 활성화된 캐릭터 말투 프롬프트 템플릿을 가져온다. 없으면 null로 저장해도 생성 자체는 가능하다.
    private PromptTemplate findCharacterTonePromptTemplate() {
        return promptTemplateRepository
                .findFirstByCategoryAndActiveTrueOrderByVersionDescIdDesc(PromptCategory.CHARACTER_TONE)
                .orElse(null);
    }

    // provider 또는 local generator 결과가 정책 검증에 실패하면 mission template의 fallback 문구를 그대로 사용한다.
    private MissionTextCandidate fallbackCandidate(MissionTextGenerationCommand command) {
        return new MissionTextCandidate(
                command.fallbackCharacterMessage(),
                command.fallbackQuestion(),
                command.fallbackCompletionResponse()
        );
    }

    // fallback 문구도 사용자에게 그대로 노출되므로 동일한 안전 검증을 통과해야 한다.
    private void validateFallbackCandidate(MissionTextCandidate candidate) {
        try {
            missionTextValidationPolicy.validate(candidate);
        } catch (FallbackRequiredException e) {
            throw new AiException(AiErrorCode.AI_FALLBACK_INVALID);
        }
    }

    private AiMissionGeneration saveResult(
            MissionTextGenerationCommand command,
            PromptTemplate promptTemplate,
            MissionTextCandidate candidate,
            AiGenerationStatus status,
            AiUsageStatus usageStatus,
            boolean fallbackUsed,
            long startedAt,
            AiErrorType errorType
    ) {
        try {
            return aiMissionTextPersistenceService.saveGenerationAndUsageLog(
                    command,
                    promptTemplate,
                    toRequestContextJson(command),
                    toResponseJson(candidate),
                    status,
                    usageStatus,
                    fallbackUsed,
                    aiProviderProperties.resolvedModel(),
                    elapsedMillis(startedAt),
                    errorType
            );
        } catch (DataIntegrityViolationException e) {
            throw new AiException(AiErrorCode.AI_DUPLICATED_REQUEST);
        }
    }

    // AI 입력 context를 JSONB에 저장하기 위한 snapshot으로 만든다.
    private String toRequestContextJson(MissionTextGenerationCommand command) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("characterType", command.characterType());
        context.put("baseTitle", command.baseTitle());
        context.put("baseDescription", command.baseDescription());
        context.put("category", command.category());
        context.put("difficulty", command.difficulty());
        context.put("fallbackCharacterMessage", command.fallbackCharacterMessage());
        context.put("fallbackQuestion", command.fallbackQuestion());
        context.put("fallbackCompletionResponse", command.fallbackCompletionResponse());
        context.put("onboardingContext", parseJsonOrEmptyObject(command.onboardingContextJson()));
        context.put("recentMissionContext", parseJsonOrEmptyObject(command.recentMissionContextJson()));
        context.put("requestId", command.requestId());
        return toJson(context);
    }

    // AI 출력도 JSONB에 저장해 나중에 fallback 비율과 문구 품질을 추적할 수 있게 한다.
    private String toResponseJson(MissionTextCandidate candidate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("characterMessage", candidate.characterMessage());
        response.put("completionQuestion", candidate.completionQuestion());
        response.put("completionCharacterResponse", candidate.completionCharacterResponse());
        return toJson(response);
    }

    // Map을 JSON 문자열로 바꾼다. 이 문자열은 DB의 JSONB 컬럼에 저장된다.
    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AiException(AiErrorCode.AI_GENERATION_FAILED);
        }
    }

    // context 문자열이 JSON이면 구조를 살려 저장하고, 비어 있으면 빈 객체로 저장한다.
    private JsonNode parseJsonOrEmptyObject(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    // 사용 로그에 남길 provider 처리 시간을 millisecond로 계산한다.
    private int elapsedMillis(long startedAt) {
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        if (elapsedMillis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) elapsedMillis;
    }

    private boolean isNotPositive(Long value) {
        return value == null || value <= 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
