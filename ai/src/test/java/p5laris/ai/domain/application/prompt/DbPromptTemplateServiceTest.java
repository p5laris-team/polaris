package p5laris.ai.domain.application.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.ai.domain.domain.entity.PromptTemplate;
import p5laris.ai.domain.domain.enums.PromptCategory;
import p5laris.ai.domain.domain.repository.PromptTemplateRepository;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbPromptTemplateServiceTest {

    @Test
    void active_DB_템플릿을_system_user_프롬프트로_렌더링한다() throws Exception {
        PromptTemplateRepository repository = mock(PromptTemplateRepository.class);
        PromptTemplate template = promptTemplate("""
                [[SYSTEM]]
                안녕 {{characterName}}

                [[USER]]
                최근 입력: {{userMessage}}
                """);
        when(repository.findFirstByCategoryAndActiveTrueOrderByVersionDescIdDesc(PromptCategory.CHARACTER_TALK))
                .thenReturn(Optional.of(template));

        DbPromptTemplateService service = new DbPromptTemplateService(repository, new PromptRenderer());

        RenderedPrompt rendered = service.render(
                PromptCategory.CHARACTER_TALK,
                Map.of(
                        "characterName", "무다리",
                        "userMessage", "오늘 힘들었어"
                ),
                new RenderedPrompt("fallback-system", "fallback-user")
        );

        assertThat(rendered.systemPrompt()).isEqualTo("안녕 무다리");
        assertThat(rendered.userPrompt()).isEqualTo("최근 입력: 오늘 힘들었어");
    }

    @Test
    void active_DB_템플릿_형식이_깨져_있으면_기본_프롬프트를_사용한다() throws Exception {
        PromptTemplateRepository repository = mock(PromptTemplateRepository.class);
        when(repository.findFirstByCategoryAndActiveTrueOrderByVersionDescIdDesc(PromptCategory.CHARACTER_TALK))
                .thenReturn(Optional.of(promptTemplate("섹션 마커가 없는 템플릿")));

        DbPromptTemplateService service = new DbPromptTemplateService(repository, new PromptRenderer());

        RenderedPrompt rendered = service.render(
                PromptCategory.CHARACTER_TALK,
                Map.of(),
                new RenderedPrompt("fallback-system", "fallback-user")
        );

        assertThat(rendered.systemPrompt()).isEqualTo("fallback-system");
        assertThat(rendered.userPrompt()).isEqualTo("fallback-user");
    }

    private PromptTemplate promptTemplate(String templateText) throws Exception {
        Constructor<PromptTemplate> constructor = PromptTemplate.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PromptTemplate template = constructor.newInstance();
        ReflectionTestUtils.setField(template, "id", 1L);
        ReflectionTestUtils.setField(template, "template", templateText);
        return template;
    }
}
