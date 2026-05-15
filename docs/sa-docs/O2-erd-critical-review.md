# O2. ERD 비판적 검토

## 문서 정보

| 항목 | 내용 |
|------|------|
| 문서명 | Polaris ERD 비판적 검토 |
| 작성일 | 2026-05-14 |
| 버전 | v1.0 |
| 목적 | ERD 설계의 약점 파악 및 개선 방안 제시 |
| 검토 관점 | 정규화, 인덱스, 확장성, 누락 엔티티 |
| 대상 독자 | 백엔드 개발자, 아키텍트, DBA |

---

## 📋 목차

1. [정규화 이슈](#1-정규화-이슈)
2. [인덱스 최적화 이슈](#2-인덱스-최적화-이슈)
3. [확장성 이슈](#3-확장성-이슈)
4. [누락된 엔티티](#4-누락된-엔티티)
5. [종합 개선 제안](#5-종합-개선-제안)

---

## 1. 정규화 이슈

### 🔴 이슈 1-1: users 테이블의 star_pieces 컬럼

**문제점:**
```sql
users {
    star_pieces INT NOT NULL DEFAULT 0
}
```

- `star_pieces`는 **파생 데이터** (star_piece_transactions의 합계)
- 데이터 중복 및 불일치 위험
- 트랜잭션 처리 시 동시성 문제 가능

**왜 문제인가?**
1. **정규화 위반**: 계산 가능한 값을 저장 (제3정규형 위반)
2. **동시성 문제**: 여러 트랜잭션이 동시에 `star_pieces`를 업데이트하면 Lost Update 발생 가능
3. **데이터 불일치**: `star_piece_transactions` 합계와 `users.star_pieces`가 다를 수 있음

**개선 방안:**

**A안: 컬럼 제거 + 실시간 계산 (정규화 준수)**
```sql
-- users 테이블에서 star_pieces 제거
ALTER TABLE users DROP COLUMN star_pieces;

-- 조회 시 실시간 계산
SELECT 
    u.*,
    COALESCE(SUM(spt.amount), 0) as star_pieces
FROM users u
LEFT JOIN star_piece_transactions spt ON u.id = spt.user_id
GROUP BY u.id;
```

**장점:**
- 데이터 일관성 보장
- 동시성 문제 없음

**단점:**
- 조회 성능 저하 (매번 SUM 계산)
- 인덱스 필요

**B안: Materialized View 사용 (추천)**
```sql
-- users 테이블에서 star_pieces 제거
ALTER TABLE users DROP COLUMN star_pieces;

-- Materialized View 생성
CREATE MATERIALIZED VIEW user_star_piece_balance AS
SELECT 
    user_id,
    COALESCE(SUM(amount), 0) as balance,
    MAX(created_at) as last_updated
FROM star_piece_transactions
GROUP BY user_id;

CREATE UNIQUE INDEX idx_user_star_piece_balance_user_id 
ON user_star_piece_balance(user_id);

-- 주기적으로 REFRESH (또는 트리거로 자동 갱신)
REFRESH MATERIALIZED VIEW CONCURRENTLY user_star_piece_balance;
```

**장점:**
- 조회 성능 우수
- 데이터 일관성 보장 (REFRESH 시점)

**단점:**
- REFRESH 주기 설정 필요
- 실시간성 약간 떨어짐

**C안: 현재 설계 유지 + 낙관적 락 (MVP 추천)**
```java
@Entity
public class User {
    @Version
    private Long version;  // 낙관적 락
    
    private Integer starPieces;
}

@Transactional
public void earnStarPieces(Long userId, int amount) {
    User user = userRepository.findById(userId)
        .orElseThrow();
    
    user.setStarPieces(user.getStarPieces() + amount);
    userRepository.save(user);  // version 체크
    
    // 트랜잭션 기록
    starPieceTransactionRepository.save(new Transaction(...));
}
```

**장점:**
- 조회 성능 최고
- 구현 단순

**단점:**
- 동시성 충돌 시 재시도 필요
- 데이터 불일치 가능성 (버그 발생 시)

**최종 권장: MVP는 C안, 운영 안정화 후 B안으로 전환**

---

### 🔴 이슈 1-2: user_characters의 상태 컬럼 (fullness, energy, affection)

**문제점:**
```sql
user_characters {
    fullness INT
    energy INT
    affection INT
    last_state_updated_at TIMESTAMP
}
```

- 상태 변화 이력이 없음
- 상태 감소 스케줄러 실패 시 복구 어려움
- 디버깅 및 분석 어려움

**왜 문제인가?**
1. **이력 부재**: 상태가 언제, 왜 변했는지 추적 불가
2. **복구 어려움**: 스케줄러 장애 시 어느 시점부터 재계산해야 할지 불명확
3. **분석 불가**: 사용자 행동 패턴 분석 어려움

**개선 방안:**

**A안: character_state_logs 테이블 추가 (추천)**
```sql
CREATE TABLE character_state_logs (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL,
    state_type VARCHAR(50) NOT NULL,  -- fullness, energy, affection
    value_before INT NOT NULL,
    value_after INT NOT NULL,
    change_reason VARCHAR(50) NOT NULL,  -- TIME_DECAY, CARE_ACTION, MISSION_COMPLETE
    changed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_character_state_logs_character_id 
    FOREIGN KEY (character_id) REFERENCES user_characters(id) ON DELETE CASCADE
);

CREATE INDEX idx_character_state_logs_character_id 
ON character_state_logs(character_id);

CREATE INDEX idx_character_state_logs_changed_at 
ON character_state_logs(changed_at);
```

**장점:**
- 완전한 이력 추적
- 디버깅 용이
- 분석 가능

**단점:**
- 테이블 크기 증가 (파티셔닝 필요)
- INSERT 오버헤드

**B안: 현재 설계 유지 + last_state_updated_at 활용**
```java
// 스케줄러에서 last_state_updated_at 기준으로 감소량 계산
public void decayStates() {
    List<UserCharacter> characters = characterRepository.findAll();
    
    for (UserCharacter character : characters) {
        Duration elapsed = Duration.between(
            character.getLastStateUpdatedAt(), 
            LocalDateTime.now()
        );
        
        int hours = (int) elapsed.toHours();
        int fullnessDecay = (hours / 6) * 10;  // 6시간마다 -10
        
        character.setFullness(Math.max(0, character.getFullness() - fullnessDecay));
        character.setLastStateUpdatedAt(LocalDateTime.now());
    }
}
```

**장점:**
- 추가 테이블 없음
- 구현 단순

**단점:**
- 이력 없음
- 복구 어려움

**최종 권장: MVP는 B안, 운영 안정화 후 A안 추가**

---

### 🟡 이슈 1-3: mission_templates의 Fallback 문구 중복

**문제점:**
```sql
mission_templates {
    default_message TEXT
    default_question TEXT
    default_response TEXT
}
```

- 캐릭터별로 다른 Fallback 문구가 필요한데 템플릿에 1개만 저장
- 캐릭터 추가 시 모든 템플릿 수정 필요

**왜 문제인가?**
1. **확장성 부족**: 캐릭터 4종 추가 시 모든 템플릿 업데이트 필요
2. **유지보수 어려움**: 캐릭터별 말투 변경 시 대량 업데이트

**개선 방안:**

**A안: mission_template_fallbacks 테이블 분리 (추천)**
```sql
CREATE TABLE mission_template_fallbacks (
    id BIGSERIAL PRIMARY KEY,
    mission_template_id BIGINT NOT NULL,
    character_type_id BIGINT NOT NULL,
    fallback_message TEXT NOT NULL,
    fallback_question TEXT NOT NULL,
    fallback_response TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_mission_template_fallbacks_template_id 
    FOREIGN KEY (mission_template_id) REFERENCES mission_templates(id) ON DELETE CASCADE,
    
    CONSTRAINT uk_mission_template_fallbacks 
    UNIQUE (mission_template_id, character_type_id)
);

CREATE INDEX idx_mission_template_fallbacks_template_id 
ON mission_template_fallbacks(mission_template_id);
```

**장점:**
- 캐릭터별 Fallback 관리 용이
- 확장성 우수

**단점:**
- 테이블 추가
- 조회 시 JOIN 필요

**B안: JSONB 컬럼 사용**
```sql
ALTER TABLE mission_templates 
ADD COLUMN fallback_by_character JSONB;

-- 예시 데이터
{
  "NOVA": {
    "message": "물 한 컵 마셔볼래? 나도 빛 좀 마셔볼게.",
    "question": "마시고 나서 조금 달라진 게 있어?",
    "response": "그걸 기억해둘게."
  },
  "MUMU": {
    "message": "무... 물.",
    "question": "무?",
    "response": "무!"
  }
}
```

**장점:**
- 테이블 추가 없음
- 유연한 구조

**단점:**
- JSONB 쿼리 복잡
- 타입 안정성 낮음

**최종 권장: MVP는 현재 설계 유지, 캐릭터 4종 이상 추가 시 A안 적용**

---

## 2. 인덱스 최적화 이슈

### 🔴 이슈 2-1: 복합 인덱스 부족

**문제점:**
자주 함께 조회되는 컬럼 조합에 복합 인덱스가 없음

**구체적 사례:**

#### 사례 1: user_missions 조회
```sql
-- 자주 실행될 쿼리
SELECT * FROM user_missions
WHERE user_id = ? 
  AND status = 'OFFERED'
  AND DATE(offered_at) = CURRENT_DATE
ORDER BY offered_at DESC;
```

**현재 인덱스:**
```sql
CREATE INDEX idx_user_missions_user_id ON user_missions(user_id);
CREATE INDEX idx_user_missions_status ON user_missions(status);
CREATE INDEX idx_user_missions_offered_at ON user_missions(offered_at);
```

**문제:** 3개의 단일 인덱스만 있어 옵티마이저가 1개만 선택 → 나머지는 풀스캔

**개선 방안:**
```sql
-- 복합 인덱스 추가
CREATE INDEX idx_user_missions_user_status_offered 
ON user_missions(user_id, status, offered_at DESC);

-- 기존 단일 인덱스는 유지 (다른 쿼리에서 사용)
```

**효과:**
- Index Scan으로 최적화
- 조회 성능 10배 이상 향상 예상

---

#### 사례 2: star_piece_transactions 조회
```sql
-- 사용자별 최근 거래 내역 조회
SELECT * FROM star_piece_transactions
WHERE user_id = ?
ORDER BY created_at DESC
LIMIT 20;
```

**현재 인덱스:**
```sql
CREATE INDEX idx_star_piece_transactions_user_id ON star_piece_transactions(user_id);
CREATE INDEX idx_star_piece_transactions_created_at ON star_piece_transactions(created_at);
```

**개선 방안:**
```sql
-- 복합 인덱스 추가
CREATE INDEX idx_star_piece_transactions_user_created 
ON star_piece_transactions(user_id, created_at DESC);
```

---

#### 사례 3: share_events 일일 보상 체크
```sql
-- 오늘 공유 보상 받았는지 확인
SELECT COUNT(*) FROM share_events
WHERE user_id = ?
  AND DATE(shared_at) = CURRENT_DATE
  AND reward_paid = TRUE;
```

**개선 방안:**
```sql
CREATE INDEX idx_share_events_user_shared_reward 
ON share_events(user_id, shared_at, reward_paid)
WHERE reward_paid = TRUE;  -- Partial Index
```

---

### 🟡 이슈 2-2: 파티셔닝 미적용

**문제점:**
시간 기반 로그 테이블들이 파티셔닝되지 않음

**대상 테이블:**
- `star_piece_transactions` (거래 내역)
- `character_care_logs` (돌봄 이력)
- `notification_logs` (알림 이력)
- `mission_recommendation_logs` (추천 로그)
- `ad_events` (광고 이벤트)

**왜 문제인가?**
1. **테이블 크기 증가**: 시간이 지날수록 테이블 크기 급증
2. **조회 성능 저하**: 풀스캔 시간 증가
3. **백업/복구 어려움**: 전체 테이블 백업 필요

**개선 방안:**

**A안: Range Partitioning (월별)**
```sql
-- star_piece_transactions 파티셔닝
CREATE TABLE star_piece_transactions (
    id BIGSERIAL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ...
) PARTITION BY RANGE (created_at);

-- 월별 파티션 생성
CREATE TABLE star_piece_transactions_2026_05 
PARTITION OF star_piece_transactions
FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE star_piece_transactions_2026_06 
PARTITION OF star_piece_transactions
FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- 자동 파티션 생성 스크립트 필요
```

**장점:**
- 오래된 데이터 아카이빙 용이
- 조회 성능 향상 (Partition Pruning)
- 백업/복구 효율적

**단점:**
- 파티션 관리 필요
- 초기 설정 복잡

**최종 권장: MVP 이후 트래픽 증가 시 적용**

---

### 🟡 이슈 2-3: JSONB 컬럼 인덱스 부족

**문제점:**
JSONB 컬럼에 인덱스가 없어 검색 불가

**대상 컬럼:**
- `user_profiles.survey_answers` (설문 답변)
- `mission_recommendation_logs.score_breakdown` (점수 상세)
- `notification_logs.data` (알림 데이터)
- `operation_alerts.details` (상세 정보)

**개선 방안:**

```sql
-- GIN 인덱스 생성 (전체 JSONB 검색)
CREATE INDEX idx_mission_recommendation_logs_score_breakdown 
ON mission_recommendation_logs USING GIN (score_breakdown);

-- 특정 키 검색용 인덱스
CREATE INDEX idx_user_profiles_burden_type 
ON user_profiles ((survey_answers->>'burden_type'));

-- 예시 쿼리
SELECT * FROM user_profiles
WHERE survey_answers->>'burden_type' = 'OUTDOOR';
```

**최종 권장: 실제 검색 요구사항 발생 시 추가**

---

## 3. 확장성 이슈

### 🔴 이슈 3-1: MSA 전환 시 트랜잭션 경계 불명확

**문제점:**
현재 설계는 모듈 간 FK가 없지만, 트랜잭션 경계가 불명확함

**구체적 사례:**

#### 사례 1: 미션 완료 처리
```java
@Transactional
public void completeMission(Long missionId, String answer) {
    // 1. Mission Module: 미션 완료
    mission.setStatus(COMPLETED);
    missionRepository.save(mission);
    
    // 2. User Module: 별조각 지급
    userService.earnStarPieces(userId, reward);  // 다른 모듈 호출
    
    // 3. Character Module: affection 증가
    characterService.increaseAffection(characterId, 5);  // 다른 모듈 호출
}
```

**문제:**
- 하나의 트랜잭션에서 여러 모듈 호출
- MSA 전환 시 분산 트랜잭션 필요 (Saga Pattern)

**개선 방안:**

**A안: 이벤트 기반으로 전환 (추천)**
```java
@Transactional
public void completeMission(Long missionId, String answer) {
    // 1. Mission Module: 미션 완료만 처리
    mission.setStatus(COMPLETED);
    missionRepository.save(mission);
    
    // 2. 이벤트 발행 (같은 트랜잭션)
    outboxRepository.save(new MissionCompletedEvent(...));
    
    // 트랜잭션 종료
}

// User Module에서 이벤트 구독
@EventListener
public void onMissionCompleted(MissionCompletedEvent event) {
    userService.earnStarPieces(event.getUserId(), event.getReward());
}
```

**장점:**
- 트랜잭션 경계 명확
- MSA 전환 용이
- 모듈 독립성 유지

**단점:**
- 최종 일관성 (즉시 일관성 아님)
- 복잡도 증가

**최종 권장: MVP 이후 이벤트 기반으로 전환**

---

### 🟡 이슈 3-2: 읽기 전용 복제본 미고려

**문제점:**
모든 조회가 Primary DB로 향함

**개선 방안:**

**A안: Read Replica 도입**
```java
@Transactional(readOnly = true)
public List<UserMission> getUserMissions(Long userId) {
    // readOnly = true → Read Replica로 라우팅
    return missionRepository.findByUserId(userId);
}
```

**설정:**
```yaml
spring:
  datasource:
    hikari:
      read-only: false
    replica:
      hikari:
        read-only: true
```

**최종 권장: 트래픽 증가 시 적용**

---

### 🟡 이슈 3-3: 캐시 전략 부재

**문제점:**
자주 조회되지만 변경이 적은 데이터에 캐시 미적용

**캐시 대상:**
- `character_types` (캐릭터 종류)
- `character_assets` (캐릭터 이미지)
- `mission_templates` (미션 템플릿)
- `items` (아이템 목록)
- `achievements` (업적 정의)

**개선 방안:**

```java
@Cacheable(value = "character_types", key = "#code")
public CharacterType findByCode(String code) {
    return characterTypeRepository.findByCode(code);
}

@CacheEvict(value = "character_types", allEntries = true)
public void updateCharacterType(CharacterType type) {
    characterTypeRepository.save(type);
}
```

**Redis 설정:**
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1시간
```

**최종 권장: MVP 이후 성능 이슈 발생 시 적용**

---

## 4. 누락된 엔티티

### 🔴 이슈 4-1: 사용자 활동 로그 부재

**문제점:**
사용자 행동 분석을 위한 로그 테이블이 없음

**필요한 이유:**
1. **사용자 행동 분석**: 어떤 기능을 많이 사용하는지
2. **이탈 분석**: 어느 단계에서 이탈하는지
3. **A/B 테스트**: 기능 개선 효과 측정
4. **보안 감사**: 의심스러운 활동 추적

**개선 방안:**

```sql
CREATE TABLE user_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,  -- nullable (비로그인 사용자)
    session_id VARCHAR(100),
    activity_type VARCHAR(50) NOT NULL,  -- PAGE_VIEW, BUTTON_CLICK, API_CALL
    activity_name VARCHAR(100) NOT NULL,  -- HOME_VIEW, MISSION_COMPLETE_CLICK
    metadata JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_user_activity_logs_user_id ON user_activity_logs(user_id);
CREATE INDEX idx_user_activity_logs_activity_type ON user_activity_logs(activity_type);
CREATE INDEX idx_user_activity_logs_created_at ON user_activity_logs(created_at);
```

**활동 유형 예시:**
- `PAGE_VIEW`: 화면 조회
- `MISSION_OFFERED`: 미션 제안
- `MISSION_REJECTED`: 미션 거절
- `MISSION_COMPLETED`: 미션 완료
- `ITEM_PURCHASED`: 아이템 구매
- `SHARE_CARD_CREATED`: 공유 카드 생성

**최종 권장: MVP 이후 분석 필요 시 추가**

---

### 🟡 이슈 4-2: 사용자 피드백/문의 테이블 부재

**문제점:**
사용자 피드백을 받을 수 있는 테이블이 없음

**개선 방안:**

```sql
CREATE TABLE user_feedbacks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    feedback_type VARCHAR(50) NOT NULL,  -- BUG_REPORT, FEATURE_REQUEST, GENERAL
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, IN_PROGRESS, RESOLVED, CLOSED
    priority VARCHAR(20),  -- LOW, MEDIUM, HIGH, CRITICAL
    assigned_to VARCHAR(100),  -- 담당자
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_feedbacks_user_id ON user_feedbacks(user_id);
CREATE INDEX idx_user_feedbacks_status ON user_feedbacks(status);
CREATE INDEX idx_user_feedbacks_created_at ON user_feedbacks(created_at);
```

**최종 권장: MVP 이후 고객 지원 필요 시 추가**

---

### 🟡 이슈 4-3: 출석 체크 테이블 부재

**문제점:**
출석 보상은 있지만 출석 이력을 추적하는 테이블이 없음

**개선 방안:**

```sql
CREATE TABLE user_attendances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    consecutive_days INT NOT NULL DEFAULT 1,
    reward_claimed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uk_user_attendances UNIQUE (user_id, attendance_date)
);

CREATE INDEX idx_user_attendances_user_id ON user_attendances(user_id);
CREATE INDEX idx_user_attendances_attendance_date ON user_attendances(attendance_date);
```

**기능:**
- 연속 출석일 추적
- 출석 보상 지급 여부
- 출석 통계 분석

**최종 권장: 출석 업적 구현 시 추가**

---

### 🟡 이슈 4-4: 사용자 차단/신고 테이블 부재

**문제점:**
향후 소셜 기능 추가 시 필요한 차단/신고 기능이 없음

**개선 방안:**

```sql
CREATE TABLE user_blocks (
    id BIGSERIAL PRIMARY KEY,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    reason VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uk_user_blocks UNIQUE (blocker_user_id, blocked_user_id)
);

CREATE TABLE user_reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_user_id BIGINT NOT NULL,
    reported_user_id BIGINT NOT NULL,
    report_type VARCHAR(50) NOT NULL,  -- SPAM, ABUSE, INAPPROPRIATE_CONTENT
    content TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**최종 권장: 소셜 기능 추가 시 구현**

---

### 🟡 이슈 4-5: 시스템 설정 테이블 부재

**문제점:**
운영 중 변경 가능한 설정값을 관리할 테이블이 없음

**개선 방안:**

```sql
CREATE TABLE system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    value_type VARCHAR(20) NOT NULL,  -- STRING, INT, BOOLEAN, JSON
    description TEXT,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 예시 데이터
INSERT INTO system_settings (setting_key, setting_value, value_type, description) VALUES
('mission.daily_limit', '15', 'INT', '하루 최대 미션 제안 수'),
('share.daily_reward_limit', '1', 'INT', '하루 공유 보상 제한'),
('maintenance.enabled', 'false', 'BOOLEAN', '점검 모드 활성화'),
('feature.achievement_enabled', 'false', 'BOOLEAN', '업적 기능 활성화');
```

**장점:**
- 코드 배포 없이 설정 변경 가능
- A/B 테스트 용이
- 긴급 기능 on/off 가능

**최종 권장: MVP 이후 운영 편의성 향상 시 추가**

---


## 5. 종합 개선 제안

### 우선순위별 개선 로드맵

#### 🔴 높음 (MVP 단계에서 고려)

| 이슈 | 개선 방안 | 예상 공수 | 효과 |
|------|-----------|-----------|------|
| users.star_pieces 동시성 | 낙관적 락 적용 | 1일 | 데이터 일관성 보장 |
| 복합 인덱스 추가 | user_missions, star_piece_transactions 등 | 0.5일 | 조회 성능 10배 향상 |
| 트랜잭션 경계 명확화 | 모듈별 트랜잭션 분리 | 2일 | MSA 전환 준비 |

**총 예상 공수: 3.5일**

---

#### 🟡 중간 (MVP 이후 1~3개월)

| 이슈 | 개선 방안 | 예상 공수 | 효과 |
|------|-----------|-----------|------|
| character_state_logs 추가 | 상태 변화 이력 테이블 | 2일 | 디버깅 및 분석 용이 |
| 이벤트 기반 전환 | Outbox/Inbox Pattern 적용 | 5일 | 모듈 독립성 향상 |
| 파티셔닝 적용 | 로그 테이블 월별 파티셔닝 | 3일 | 조회 성능 및 관리 용이 |
| 캐시 도입 | Redis 캐시 적용 | 2일 | 조회 성능 향상 |
| user_activity_logs 추가 | 사용자 행동 로그 | 2일 | 분석 및 개선 근거 |

**총 예상 공수: 14일**

---

#### 🟢 낮음 (운영 안정화 후)

| 이슈 | 개선 방안 | 예상 공수 | 효과 |
|------|-----------|-----------|------|
| Materialized View | star_pieces 실시간 계산 | 2일 | 데이터 정합성 완벽 |
| Read Replica | 읽기 전용 복제본 | 3일 | 읽기 성능 향상 |
| mission_template_fallbacks | 캐릭터별 Fallback 분리 | 2일 | 확장성 향상 |
| user_feedbacks 추가 | 피드백 관리 | 2일 | 고객 지원 강화 |
| user_attendances 추가 | 출석 이력 관리 | 1일 | 출석 업적 구현 |
| system_settings 추가 | 동적 설정 관리 | 1일 | 운영 편의성 |

**총 예상 공수: 11일**

---

### 개선 효과 예측

#### 성능 개선
| 항목 | 현재 | 개선 후 | 개선율 |
|------|------|---------|--------|
| 미션 목록 조회 | 100ms | 10ms | 90% ↓ |
| 별조각 거래 내역 | 80ms | 8ms | 90% ↓ |
| 캐릭터 정보 조회 | 50ms | 5ms (캐시) | 90% ↓ |

#### 데이터 정합성
| 항목 | 현재 | 개선 후 |
|------|------|---------|
| star_pieces 불일치 위험 | 중간 | 낮음 (낙관적 락) |
| 상태 이력 추적 | 불가능 | 가능 (state_logs) |
| 이벤트 중복 처리 | 가능 | 불가능 (Inbox) |

#### 확장성
| 항목 | 현재 | 개선 후 |
|------|------|---------|
| MSA 전환 난이도 | 높음 | 낮음 (이벤트 기반) |
| 모듈 독립성 | 중간 | 높음 |
| 트랜잭션 경계 | 불명확 | 명확 |

---

### 비용 대비 효과 분석

#### 높은 ROI (즉시 적용 권장)
1. **복합 인덱스 추가** (0.5일 → 성능 10배)
2. **낙관적 락 적용** (1일 → 데이터 정합성)
3. **트랜잭션 경계 명확화** (2일 → MSA 준비)

#### 중간 ROI (MVP 이후 적용)
1. **이벤트 기반 전환** (5일 → 모듈 독립성)
2. **파티셔닝** (3일 → 장기 성능)
3. **캐시 도입** (2일 → 조회 성능)

#### 낮은 ROI (필요 시 적용)
1. **Materialized View** (2일 → 완벽한 정합성)
2. **Read Replica** (3일 → 읽기 성능)
3. **부가 기능 테이블** (1~2일 → 기능 확장)

---

### 최종 권장 사항

#### MVP 단계 (2주)
```
✅ 필수 적용
- 복합 인덱스 추가 (0.5일)
- 낙관적 락 적용 (1일)
- 트랜잭션 경계 명확화 (2일)

❌ 적용 제외
- 이벤트 기반 (복잡도 증가)
- 파티셔닝 (데이터 적음)
- 부가 테이블 (기능 우선)
```

#### MVP 이후 1개월
```
✅ 적용 검토
- character_state_logs 추가
- user_activity_logs 추가
- 캐시 도입

⏰ 트래픽 모니터링 후 결정
- 파티셔닝
- Read Replica
```

#### MVP 이후 3개월
```
✅ 적용 검토
- 이벤트 기반 전환
- Materialized View
- mission_template_fallbacks 분리

⏰ 기능 확장 시 추가
- user_feedbacks
- user_attendances
- system_settings
```

---

## 결론

### 현재 ERD의 강점
1. ✅ **모듈 독립성**: FK 없이 ID만 저장하여 MSA 전환 준비
2. ✅ **명확한 도메인 분리**: 10개 모듈로 책임 분리
3. ✅ **기본 인덱스**: 주요 컬럼에 인덱스 적용
4. ✅ **확장 가능성**: 향후 기능 추가 고려

### 현재 ERD의 약점
1. ⚠️ **정규화 이슈**: star_pieces 중복, 상태 이력 부재
2. ⚠️ **인덱스 최적화**: 복합 인덱스 부족, 파티셔닝 미적용
3. ⚠️ **확장성 이슈**: 트랜잭션 경계 불명확, 캐시 전략 부재
4. ⚠️ **누락 엔티티**: 활동 로그, 피드백, 출석 등

### 개선 우선순위
1. 🔴 **즉시**: 복합 인덱스, 낙관적 락, 트랜잭션 경계 (3.5일)
2. 🟡 **1~3개월**: 이벤트 기반, 파티셔닝, 캐시, 로그 (14일)
3. 🟢 **안정화 후**: Materialized View, Read Replica, 부가 기능 (11일)

### 최종 평가
**현재 ERD는 MVP 구현에 적합하지만, 운영 안정화를 위해 단계적 개선이 필요합니다.**

---

**문서 작성 완료일**: 2026-05-14
