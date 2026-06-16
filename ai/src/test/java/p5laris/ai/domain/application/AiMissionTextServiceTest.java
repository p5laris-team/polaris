package p5laris.ai.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.dto.MissionTextGenerationResult;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.generator.MissionTextGenerator;
import p5laris.ai.domain.application.generator.MissionTextGenerationOutput;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;
import p5laris.ai.domain.domain.policy.MissionTextValidationPolicy;
import p5laris.ai.domain.domain.repository.AiMissionGenerationRepository;
import p5laris.ai.domain.domain.repository.PromptTemplateRepository;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;
import p5laris.ai.domain.infrastructure.provider.RuleBasedMissionTextGenerator;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMissionTextServiceTest {

    @Mock
    private MissionTextGenerator missionTextGenerator;

    @Mock
    private RuleBasedMissionTextGenerator ruleBasedMissionTextGenerator;

    @Mock
    private MissionTextValidationPolicy missionTextValidationPolicy;

    @Mock
    private AiMissionTextPersistenceService persistenceService;

    @Mock
    private PromptTemplateRepository promptTemplateRepository;

    @Mock
    private AiMissionGenerationRepository generationRepository;

    private AiMissionTextService service;

    @BeforeEach
    void setUp() {
        AiProviderProperties providerProperties = new AiProviderProperties();
        providerProperties.setType("local");
        providerProperties.setModel("local-tone-v1");

        service = new AiMissionTextService(
                missionTextGenerator,
                ruleBasedMissionTextGenerator,
                missionTextValidationPolicy,
                persistenceService,
                promptTemplateRepository,
                generationRepository,
                providerProperties,
                new ObjectMapper()
        );
    }

    @Test
    void generatedCandidateIsValidatedAndSaved() {
        MissionTextGenerationCommand command = validCommand("NOVA");
        MissionTextCandidate candidate = candidate(command);
        AtomicReference<AiTokenUsage> savedTokenUsage = new AtomicReference<>();
        when(generationRepository.findByRequestId(command.requestId())).thenReturn(Optional.empty());
        when(missionTextGenerator.generateWithUsage(command))
                .thenReturn(new MissionTextGenerationOutput(candidate, new AiTokenUsage(11, 7, 18)));
        when(persistenceService.saveGenerationAndUsageLog(
                eq(command), any(), any(), any(), any(), any(), any(), any(Boolean.class),
                any(), any(), any(Integer.class), any(), any()
        )).thenAnswer(invocation -> {
            savedTokenUsage.set((AiTokenUsage) invocation.getArguments()[11]);
            return generationFromInvocation(invocation.getArguments(), 101L);
        });

        MissionTextGenerationResult result = service.generateMissionTexts(command);

        assertThat(result.aiGenerationId()).isEqualTo(101L);
        assertThat(result.status()).isEqualTo(AiGenerationStatus.SUCCESS);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.characterMessage()).isEqualTo(candidate.characterMessage());
        verify(missionTextValidationPolicy).validate(candidate, "NOVA");
        verify(ruleBasedMissionTextGenerator, never()).generate(any());
        assertThat(savedTokenUsage.get().promptTokens()).isEqualTo(11);
        assertThat(savedTokenUsage.get().completionTokens()).isEqualTo(7);
        assertThat(savedTokenUsage.get().totalTokens()).isEqualTo(18);
    }

    @Test
    void invalidRequestIsRejectedBeforeCallingDependencies() {
        MissionTextGenerationCommand invalid = new MissionTextGenerationCommand(
                null, 2L, "NOVA", "Polaris", 3L,
                "title", "description", "ROUTINE", "EASY",
                "fallback", "question", "response", "{}", "{}", UUID.randomUUID().toString()
        );

        assertThatThrownBy(() -> service.generateMissionTexts(invalid))
                .isInstanceOf(AiException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_INVALID_REQUEST);

        verify(generationRepository, never()).findByRequestId(any());
        verify(missionTextGenerator, never()).generateWithUsage(any());
    }

    @Test
    void providerFailureUsesValidatedFallback() {
        MissionTextGenerationCommand command = validCommand("JJORY");
        MissionTextCandidate fallback = candidate(command);
        when(generationRepository.findByRequestId(command.requestId())).thenReturn(Optional.empty());
        when(missionTextGenerator.generateWithUsage(command))
                .thenThrow(new FallbackRequiredException(AiErrorType.PROVIDER_ERROR, "provider unavailable"));
        when(ruleBasedMissionTextGenerator.generate(command)).thenReturn(fallback);
        stubSavedGeneration(102L);

        MissionTextGenerationResult result = service.generateMissionTexts(command);

        assertThat(result.status()).isEqualTo(AiGenerationStatus.FALLBACK);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.errorType()).isEqualTo(AiErrorType.PROVIDER_ERROR);
        verify(missionTextValidationPolicy).validate(fallback, "JJORY");
    }

    @Test
    void invalidFallbackIsNotPersisted() {
        MissionTextGenerationCommand command = validCommand("UNKNOWN");
        when(generationRepository.findByRequestId(command.requestId())).thenReturn(Optional.empty());
        when(missionTextGenerator.generateWithUsage(command))
                .thenThrow(new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "invalid output"));
        when(ruleBasedMissionTextGenerator.generate(command))
                .thenThrow(new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "unsupported tone"));
        doThrow(new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "unsafe fallback"))
                .when(missionTextValidationPolicy).validate(any(MissionTextCandidate.class), eq("UNKNOWN"));

        assertThatThrownBy(() -> service.generateMissionTexts(command))
                .isInstanceOf(AiException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_FALLBACK_INVALID);

        verify(persistenceService, never()).saveGenerationAndUsageLog(
                any(), any(), any(), any(), any(), any(), any(), any(Boolean.class),
                any(), any(), any(Integer.class), any(), any()
        );
    }

    @Test
    void sameRequestIdAndBodyReuseStoredGeneration() {
        MissionTextGenerationCommand command = validCommand("NOVA");
        MissionTextCandidate candidate = candidate(command);
        AtomicReference<AiMissionGeneration> saved = new AtomicReference<>();
        when(generationRepository.findByRequestId(command.requestId()))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(missionTextGenerator.generateWithUsage(command))
                .thenReturn(MissionTextGenerationOutput.withoutUsage(candidate));
        when(persistenceService.saveGenerationAndUsageLog(
                eq(command), any(), any(), any(), any(), any(), any(), any(Boolean.class),
                any(), any(), any(Integer.class), any(), any()
        )).thenAnswer(invocation -> {
            AiMissionGeneration generation = generationFromInvocation(invocation.getArguments(), 103L);
            saved.set(generation);
            return generation;
        });

        MissionTextGenerationResult first = service.generateMissionTexts(command);
        MissionTextGenerationResult second = service.generateMissionTexts(command);

        assertThat(second).isEqualTo(first);
        verify(missionTextGenerator).generateWithUsage(command);
    }

    @Test
    void sameRequestIdWithDifferentBodyIsRejected() {
        MissionTextGenerationCommand command = validCommand("NOVA");
        AiMissionGeneration existing = generation(
                104L, command, "different-request-hash", candidate(command),
                AiGenerationStatus.SUCCESS, false, null
        );
        when(generationRepository.findByRequestId(command.requestId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.generateMissionTexts(command))
                .isInstanceOf(AiException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_REQUEST_CONFLICT);

        verify(missionTextGenerator, never()).generateWithUsage(any());
    }

    @Test
    void concurrentDuplicateSaveReusesWinner() {
        MissionTextGenerationCommand command = validCommand("NOVA");
        MissionTextCandidate candidate = candidate(command);
        AtomicReference<AiMissionGeneration> winner = new AtomicReference<>();
        when(generationRepository.findByRequestId(command.requestId()))
                .thenAnswer(invocation -> Optional.ofNullable(winner.get()));
        when(missionTextGenerator.generateWithUsage(command))
                .thenReturn(MissionTextGenerationOutput.withoutUsage(candidate));
        when(persistenceService.saveGenerationAndUsageLog(
                eq(command), any(), any(), any(), any(), any(), any(), any(Boolean.class),
                any(), any(), any(Integer.class), any(), any()
        )).thenAnswer(invocation -> {
            winner.set(generationFromInvocation(invocation.getArguments(), 105L));
            throw new DataIntegrityViolationException("request_id unique violation");
        });

        MissionTextGenerationResult result = service.generateMissionTexts(command);

        assertThat(result.aiGenerationId()).isEqualTo(105L);
        assertThat(result.requestId()).isEqualTo(command.requestId());
    }

    private void stubSavedGeneration(long id) {
        when(persistenceService.saveGenerationAndUsageLog(
                any(), any(), any(), any(), any(), any(), any(), any(Boolean.class),
                any(), any(), any(Integer.class), any(), any()
        )).thenAnswer(invocation -> generationFromInvocation(invocation.getArguments(), id));
    }

    private AiMissionGeneration generationFromInvocation(Object[] arguments, long id) {
        MissionTextGenerationCommand command = (MissionTextGenerationCommand) arguments[0];
        String requestHash = (String) arguments[2];
        String responseJson = (String) arguments[4];
        AiGenerationStatus status = (AiGenerationStatus) arguments[5];
        boolean fallbackUsed = (boolean) arguments[7];
        String model = (String) arguments[9];
        AiErrorType errorType = (AiErrorType) arguments[12];

        AiMissionGeneration generation = AiMissionGeneration.create(
                command.userId(),
                command.characterId(),
                null,
                command.requestId(),
                requestHash,
                (String) arguments[3],
                responseJson,
                command.missionTemplateId(),
                status,
                fallbackUsed,
                model,
                errorType
        );
        ReflectionTestUtils.setField(generation, "id", id);
        return generation;
    }

    private AiMissionGeneration generation(
            long id,
            MissionTextGenerationCommand command,
            String requestHash,
            MissionTextCandidate candidate,
            AiGenerationStatus status,
            boolean fallbackUsed,
            AiErrorType errorType
    ) {
        try {
            String responseJson = new ObjectMapper().writeValueAsString(candidate);
            AiMissionGeneration generation = AiMissionGeneration.create(
                    command.userId(),
                    command.characterId(),
                    null,
                    command.requestId(),
                    requestHash,
                    "{}",
                    responseJson,
                    command.missionTemplateId(),
                    status,
                    fallbackUsed,
                    "local-tone-v1",
                    errorType
            );
            ReflectionTestUtils.setField(generation, "id", id);
            return generation;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MissionTextCandidate candidate(MissionTextGenerationCommand command) {
        return new MissionTextCandidate(
                command.baseTitle(),
                command.baseDescription(),
                "character message",
                "completion question",
                "completion response",
                command.category(),
                command.difficulty()
        );
    }

    private MissionTextGenerationCommand validCommand(String characterType) {
        return new MissionTextGenerationCommand(
                1L,
                2L,
                characterType,
                "Polaris",
                3L,
                "Drink water",
                "Drink a glass of water now.",
                "BASIC_ROUTINE",
                "EASY",
                "fallback message",
                "fallback question",
                "fallback response",
                "{\"routineGoal\":\"WAKE_UP\"}",
                "{\"recentRejected\":[]}",
                UUID.randomUUID().toString()
        );
    }
}
