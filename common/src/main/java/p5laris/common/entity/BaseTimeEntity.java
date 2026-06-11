package p5laris.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 데이터베이스의 등록 시간(created_at)만 기록하고 수정 시간(updated_at) 컬럼은 없는
 * 읽기 전용/로그성 엔티티를 위한 JPA Auditing용 공통 추상 엔티티 클래스입니다.
 * 
 * ai 모듈 등 수정 내역이 없고 등록 시각만 저장해야 하는 엔티티들의 정합성을 보장하기 위해 사용합니다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
