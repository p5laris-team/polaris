package p5laris.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 데이터베이스의 등록 시간(created_at)과 수정 시간(updated_at)을 자동으로 기록하고 관리하는
 * JPA Auditing 용 공통 추상 엔티티 클래스입니다.
 * 
 * 여러 모듈의 엔티티 클래스가 상속받아 일관되게 감시(Auditing)를 수행하도록 단일화되었습니다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
