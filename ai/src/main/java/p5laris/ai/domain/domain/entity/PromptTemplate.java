package p5laris.ai.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.ai.domain.domain.enums.PromptCategory;
import p5laris.common.entity.BaseEntity;

/**
 * AI 프롬프트 템플릿을 DB에서 버전 관리하기 위한 엔티티다.
 *
 * 생성 이력에는 사용한 템플릿 id만 남기고, 실제 provider 프롬프트 조립은 infrastructure 계층에서 수행한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prompt_templates")
public class PromptTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PromptCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String template;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean active;
}
