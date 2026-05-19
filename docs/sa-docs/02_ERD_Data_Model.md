# 07_ERD_Data_Model

---

# 1. 테이블 상세

## 1.1 `users`

사용자 계정 정보를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 사용자 ID |
| email | varchar unique | 이메일 |
| nickname | varchar | 닉네임 |
| provider | varchar | LOCAL / GOOGLE / KAKAO |
| role | varchar | USER / ADMIN |
| status | varchar | ACTIVE / WITHDRAWN / BLOCKED |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

<aside>

password_hash → 없앨까?
status → 지금을 필요 없는데… 우선 둘까?
`star_pieces`  보유 별조각 → Wallet 테이블로 분리? 혹은 User 테이블에 추가?

</aside>

### 제약 / 인덱스

```
unique(email)
index(status)
index(created_at)
```

### 비고

```
MVP에서는 사용자 프로필 상세값을 users에 모두 넣지 않는다.
온보딩 설문 기반 개인화 데이터는 user_onboarding_profiles에서 관리한다.
```

---

## 1.2 `onboarding_profiles`

캐릭터가 선택지형 설문으로 수집한 초기 개인화 데이터를 저장한다.

MVP에서는 질문 마스터 테이블을 만들지 않고, 정해진 설문 결과를 하나의 프로필 테이블에 저장한다. 질문지를 동적으로 운영하는 기능은 MVP 범위가 아니므로 `survey_questions` 같은 테이블은 만들지 않는다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 온보딩 프로필 ID |
| user_id | bigint FK unique | 사용자 ID |
| living_type | varchar nullable | LIVING_ALONE / WITH_FAMILY / WITH_ROOMMATE / OTHER |
| wake_up_time | varchar nullable | 예: 07:00, UNKNOWN |
| sleep_time | varchar nullable | 예: 24:00, UNKNOWN |
| preferred_mission_time | varchar nullable | MORNING / AFTERNOON / EVENING / NIGHT / ANYTIME |
| routine_goal | varchar nullable | WAKE_UP / CLEAN_ROOM / GO_OUT / SELF_CARE / STUDY / LIGHT_ACTIVITY |
| activity_preference | varchar nullable | INDOOR / OUTDOOR / BOTH |
| mission_intensity | varchar nullable | VERY_LIGHT / LIGHT / NORMAL |
| answers_json | jsonb nullable | 전체 설문 응답 snapshot |
| completed | boolean | 온보딩 완료 여부 |
| completed_at | timestamp nullable | 온보딩 완료 시각 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 설문 예시

```
Q1. 지금 생활 환경은 어떤가요?
- 혼자 살아요
- 가족과 살아요
- 룸메이트와 살아요
- 기타

Q2. 보통 몇 시쯤 일어나나요?
- 6~8시
- 8~10시
- 10시 이후
- 일정하지 않아요

Q3. 지금 만들고 싶은 루틴은 무엇인가요?
- 일어나기
- 방 정리
- 짧은 외출
- 자기 돌봄
- 공부/집중
- 가벼운 활동

Q4. 미션은 어느 정도가 좋아요?
- 정말 가벼운 것
- 5분 안에 할 수 있는 것
- 조금 움직이는 것

Q5. 실내/실외 중 무엇이 편한가요?
- 실내
- 실외
- 둘 다 괜찮아요
```

### 제약 / 인덱스

```
unique(user_id)
index(region_code)
index(completed)
```

---

## 1.3 `character_types`

MVP에서 제공하는 캐릭터 3종의 기본 정보를 저장한다.

캐릭터는 담당 기능이 다른 존재가 아니라, 성격·말투·정서가 다른 애착형 페르소나다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 캐릭터 타입 ID |
| code | varchar unique | NOVA / MUMU / JJORY |
| name | varchar | 노바 / 무무 / 쪼리 |
| summary | varchar | 선택 화면 한 줄 소개 |
| personality | text | 성격 설명 |
| speech_style | text | 말투 설명 |
| intro_message | text | 선택 화면 소개 문구 |
| sample_line | varchar | 대표 대사 |
| active | boolean | 활성 여부 |
| sort_order | int | 노출 순서 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

## `character_assets`

| 컬럼                | 타입 | 설명 |
|-------------------| --- | --- |
| id                | bigint PK | 캐릭터 타입 ID |
| character_type_id | varchar unique | NOVA / MUMU / JJORY |
| asset_type        | varchar | 이미지 타입 (기본, 기쁨, 슬픔..) |
| asset_url         | text | 캐릭터 이미지 URL |

### MVP seed

| code | name | summary |
| --- | --- | --- |
| NOVA | 노바 | 자기가 별이었다는 걸 까먹은 별알 |
| MUMU | 무무 | “무…”밖에 못 하는 작은 별나무 |
| JJORY | 쪼리 | 현관까지 가면 세계여행이라고 믿는 별쥐 |

### 제약 / 인덱스

```
unique(code)
index(active, sort_order)
```

---

## 1.4 `user_characters`

사용자가 생성한 캐릭터를 저장한다.

MVP에서는 사용자가 활성 캐릭터 1개를 키우는 구조로 시작한다.

| 컬럼 | 타입          | 설명                     |
| --- |-------------|------------------------|
| id | bigint PK   | 캐릭터 ID                 |
| user_id | bigint FK   | 사용자 ID                 |
| character_type_id | bigint FK   | 캐릭터 타입                 |
| name | varchar(10) | 캐릭터 이름                 |
| level | int         | 레벨                     |
| exp | int         | 누적 경험치                 |
| hunger_status | int         | 0 ~ 100                |
| energy_status | int         | 0 ~ 100 |
| affection_status | int     | 0 ~ 100 |
| active | boolean     | 현재 활성 캐릭터 여부           |
| eqquipped_skin_id | bigint      | 장착한 스킨 ID              |
| created_at | timestamp   | 생성일                    |
| updated_at | timestamp   | 수정일                    |

### 상태 3개

| 상태 | 의미 |
| --- | --- |
| hunger_status | 허기 → 먹이 |
| energy_status | 피로/졸림 → 배게 |
| affection_status | 애정 → 장난감 |

### 제약 / 인덱스

```
name length <= 10
index(user_id, active)
index(character_type_id)
partial unique(user_id) where active = true
```

### 비고

```
boredom, affection 등 추가 상태는 MVP에서 제외한다.
상태값은 0~100 숫자보다 GOOD/NORMAL/BAD 3단계 enum으로 시작한다.
캐릭터 상태 수치화가 필요해지면 별도 character_status_snapshots 확장을 검토한다.
```

---

## 1.5 `character_care_logs`

밥 주기, 재우기, 씻기기 등 캐릭터 상태 관리 기록을 저장한다.

| 컬럼 | 타입              | 설명 |
| --- |-----------------| --- |
| id | bigint PK       | 돌봄 로그 ID |
| user_id | bigint FK       | 사용자 ID |
| character_id | bigint FK       | 캐릭터 ID |
| item_id | bigint nullable | 사용한 소모성 아이템 ID |
| action_type | varchar         | FEED / SLEEP / PLAY |
| before_state_json | int             | 돌봄 전 상태 |
| after_state_json | int             | 돌봄 후 상태 |
| created_at | timestamp       | 생성일 |

## `share_cards`

내 캐릭터 공유 카드의 발급 이력을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 공유 카드 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 캐릭터 ID |
| share_id | varchar unique | 단축 공유 고유 식별자 (예: sh_abc123) |
| headline | varchar nullable | 카드에 삽입된 한 줄 각오/메시지 |
| image_url | varchar nullable | 렌더링된 카드 이미지 URL |
| share_url | varchar | 배포용 단축 공유 URL |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스
```
unique(share_id)
index(user_id, created_at)
```

---

## `share_events`

사용자의 공유 시도 이벤트 및 일일 1회 보상 수령 여부를 관리한다. (기존 `share_logs`에서 테이블명 및 설계 고도화)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 공유 이벤트 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 캐릭터 ID |
| share_card_id | bigint FK | 공유 카드 ID |
| share_type | varchar | 공유 타입 (WEB_SHARE_API, COPY_LINK 등) |
| platform | varchar | 공유 플랫폼 (X, KAKAOTALK, ETC) |
| reward_star_piece | int | 공유 보상 수량 |
| reward_paid | boolean | 보상 수령 여부 |
| idempotency_key | varchar unique | 공유 보상 중복 지급 방지 키 |
| created_at | timestamp | 생성일 (공유 시각) |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스
```
unique(idempotency_key)
index(user_id, created_at)
index(user_id, share_card_id)
```

---

## `share_clicks`

외부 유저가 공유 링크를 타고 들어온 유입 분석 로그를 수집한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 클릭 로그 ID |
| share_id | varchar | 대상 공유 식별자 |
| referrer | varchar nullable | 유입 이전 페이지 경로 |
| utm_source | varchar nullable | 마케팅 소스 (예: x, kakaotalk) |
| utm_medium | varchar nullable | 마케팅 매체 (예: social, chat) |
| utm_campaign | varchar nullable | 마케팅 캠페인명 (예: character_card) |
| ip_address | varchar nullable | 클라이언트 IP (어뷰징 방지용) |
| created_at | timestamp | 생성일 (클릭 시각) |

### 제약 / 인덱스
```
index(share_id, created_at)
index(utm_source, created_at)
```

---

### MVP 돌봄 액션

| action_type | 설명 |
| --- | --- |
| FEED | 밥 주기 |
| SLEEP | 재우기 |
| WASH | 씻기기 |

### 제약 / 인덱스 (기타)

```
index(user_id, created_at)
index(character_id, created_at)
```

---

## 1.6 `wallets`

사용자의 별조각 잔액을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 지갑 ID |
| user_id | bigint FK unique | 사용자 ID |
| star_piece | int | 별조각 잔액 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약

```
unique(user_id)
check(star_piece >= 0)
```

### 비고

```
별조각은 MVP에서 무료 재화다.
별조각은 미션 완료, 출석, 업적, SNS 공유 보상으로 획득한다.
별조각 구매 기능은 MVP에서 구현하지 않는다.
Mock 구매, Mock 지급, 테스트용 결제 버튼도 만들지 않는다.
```

---

## 1.7 `star_piece_transactions`

별조각 지급/사용 원장을 저장한다.

별조각 잔액이 변경되는 모든 작업은 반드시 이 테이블에 기록한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 거래 ID |
| user_id | bigint FK | 사용자 ID |
| transaction_type | varchar | EARN / SPEND / ADJUST |
| amount | int | 증감량. 사용은 음수 |
| balance_after | int | 거래 후 잔액 |
| reason | varchar | MISSION_REWARD / ITEM_PURCHASE / ATTENDANCE / ACHIEVEMENT / SHARE_REWARD / CARE_ACTION |
| ref_type | varchar nullable | MISSION / ITEM / ATTENDANCE / ACHIEVEMENT / SHARE / CARE |
| ref_id | bigint nullable | 참조 ID |
| idempotency_key | varchar unique | 중복 방지 키 |
| created_at | timestamp | 생성일 |

### reason

| reason | 설명 |
| --- | --- |
| MISSION_REWARD | 미션 완료 보상 |
| ITEM_PURCHASE | 아이템 구매 |
| ATTENDANCE | 출석 보상 |
| ACHIEVEMENT | 업적 보상 |
| SHARE_REWARD | SNS 공유 시도 보상 |
| CARE_ACTION | 캐릭터 돌봄 액션 별조각 사용 |

### idempotency key 예시

```
MISSION_REWARD:{missionId}
ATTENDANCE:{userId}:{yyyyMMdd}
ACHIEVEMENT:{userAchievementId}
ITEM_PURCHASE:{purchaseRequestId}
SHARE_REWARD:{userId}:{shareDate}
CARE_ACTION:{careLogId}
```

### 제약 / 인덱스

```
unique(idempotency_key)
index(user_id, created_at)
index(reason, created_at)
index(ref_type, ref_id)
```

### 비고

```
MOCK_PURCHASE, PAYMENT_CHARGE, CASH_PURCHASE 같은 reason은 MVP enum에 넣지 않는다.
결제 기능이 실제로 필요한 시점에 별도 migration과 정책 검토 후 추가한다.
```

---

## 1.8 `mission_templates`

AI fallback과 미션 생성을 위한 seed 미션 템플릿을 저장한다.

AI가 완전 자유 생성하는 것이 아니라 템플릿 기반으로 미션을 선정하고, 캐릭터 말투로 문구를 변환하는 구조다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 미션 템플릿 ID |
| base_title | varchar | 기본 미션 제목 |
| base_description | text | 기본 설명 |
| category | varchar | BASIC_ROUTINE / SPACE_RESET / BODY_CARE / OUTDOOR_LIGHT / MIND_RECORD / REST_RECOVERY |
| difficulty | varchar | EASY / NORMAL |
| reward_star_piece | int | 기본 별조각 보상 |
| reward_exp | int | 기본 EXP 보상 |
| active | boolean | 활성 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

<aside>

base_message, base_question, base_response 필요? 어딘가에는 있어야함 관리하는 다른 테이블이 있는지 확인 하기
폴백을 어떻게 처리 할지 협의가 필요 할 것 같다 

</aside>

### 제약 / 인덱스

```
index(active, category, difficulty)
```

### MVP seed 예시

| category | base_title | reward_star_piece |
| --- | --- | --- |
| BASIC_ROUTINE | 물 한 컵 마시기 | 5 |
| BASIC_ROUTINE | 양치하기 | 5 |
| SPACE_RESET | 창문 3분 열기 | 7 |
| SPACE_RESET | 책상 위 물건 하나 치우기 | 7 |
| OUTDOOR_LIGHT | 하늘 한 번 보고 오기 | 10 |
| MIND_RECORD | 오늘 기분 한 단어 적기 | 6 |

---

## 1.9 `prompt_templates`

AI 미션 생성과 캐릭터 말투 변환에 사용할 프롬프트 템플릿을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 프롬프트 ID |
| name | varchar | 템플릿 이름 |
| category | varchar | MISSION_GENERATION / CHARACTER_TONE / COMPLETION_QA / FALLBACK |
| template | text | 프롬프트 본문 |
| version | int | 버전 |
| active | boolean | 활성 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

파일로 프롬프트를 관리 할지, DB로 관리 할지 선택해야함
개발 진행 하면서 어떤게 좋을지 협의 필요

### 제약 / 인덱스

```
unique(name, version)
index(category, active)
```

---

## 1.10 `ai_mission_generations`

AI가 미션을 생성/선정한 결과를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | AI 생성 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 제안한 캐릭터 ID |
| prompt_template_id | bigint FK | 사용한 프롬프트 ID |
| request_context_json | jsonb | 온보딩, 최근 미션, 거절, 날씨, 캐릭터 상태 등 입력 요약 |
| response_json | jsonb | AI 구조화 응답 |
| selected_template_id | bigint nullable | 선택된 seed 미션 템플릿 |
| status | varchar | SUCCESS / FALLBACK / FAILED |
| fallback_used | boolean | fallback 사용 여부 |
| model | varchar | 사용 모델 |
| created_at | timestamp | 생성일 |


prompt_template_id는 prompt_template을 테이블로 관리하지 않고 파일로 관리 한다면은 파일명이 들어간다
AI가 미션을 발행하다 실패한 원인(SQL 문법 에러등)에 대해 기록이 필요할지 생각해 봐야한다 

### 제약 / 인덱스

```
index(user_id, created_at)
index(status, created_at)
index(model, created_at)
```

### 비고

```
AI 응답은 저장 전에 구조화 출력 검증을 통과해야 한다.
AI가 없는 미션 템플릿이나 잘못된 보상을 생성하면 fallback으로 대체한다.
```

---

## 1.11 `user_missions`

사용자에게 실제 제안된 미션 인스턴스다.

미션은 하루에 여러 개가 stack으로 쌓이지만, 사용자에게는 한 번에 하나의 현재 미션을 보여준다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 미션 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 미션을 제안한 캐릭터 ID |
| mission_template_id | bigint FK nullable | 미션 템플릿 ID |
| ai_generation_id | bigint FK nullable | AI 생성 로그 ID |
| mission_date | date | 기준일 |
| stack_order | int | 해당 날짜의 몇 번째 제안인지 |
| title | varchar | 미션 제목 |
| description | text | 미션 설명 |
| character_message | text | 캐릭터 말투 제안 문구 |
| category | varchar | 미션 카테고리 |
| difficulty | varchar | 난이도 |
| reward_star_piece | int | 보상 별조각 |
| reward_exp | int | 보상 EXP |
| status | varchar | GENERATED / OFFERED / COMPLETION_QA / COMPLETED / REJECTED / EXPIRED |
| offered_at | timestamp nullable | 제안 시각 |
| completion_started_at | timestamp nullable | 완료 Q&A 시작 시각 |
| completed_at | timestamp nullable | 완료 시각 |
| rejected_at | timestamp nullable | 거절 시각 |
| expired_at | timestamp nullable | 만료 시각 |
| idempotency_key | varchar unique nullable | 완료 보상 중복 방지 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 상태

| status | 설명 |
| --- | --- |
| GENERATED | 생성되었지만 아직 사용자에게 표시 전 |
| OFFERED | 현재 또는 과거에 사용자에게 제안됨 |
| COMPLETION_QA | 사용자가 완료를 눌렀고 2문항 Q&A 진행 중 |
| COMPLETED | Q&A 완료 후 보상 지급 완료 |
| REJECTED | 사용자가 거절 |
| EXPIRED | 날짜 변경 등으로 만료 |

### 제약 / 인덱스

```
unique(user_id, mission_date, stack_order)
unique(idempotency_key)
index(user_id, mission_date, status)
index(user_id, created_at)
index(ai_generation_id)
```

### 정책

```
하루 최대 미션 제안 수는 application에서 10~15개로 제한한다.
현재 미션은 해당 날짜의 최신 OFFERED 또는 COMPLETION_QA 상태 미션으로 조회한다.
미션 완료 보상은 COMPLETED 전환 시 1회만 지급한다.
```

---

## 1.12 `mission_completion_answers`

미션 완료 후 캐릭터가 던지는 1문항 질의응답을 저장한다.

단순 완료 클릭만으로 보상이 지급되지 않도록, 사용자는 미션과 관련된 질문 1개에 텍스트로 답변해야 한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 완료 답변 ID |
| mission_id | bigint FK | 미션 ID |
| user_id | bigint FK | 사용자 ID |
| question_text | text | 캐릭터가 던진 질문 |
| answer_text | text nullable | 사용자 답변 |
| answered_at | timestamp nullable | 답변 시각 |
| created_at | timestamp | 생성일 |

### 질문 예시

```
산책 미션:
1. 오늘 하늘 색깔은 어땠어?
2. 밖에 나갔을 때 가장 먼저 본 건 뭐였어?

물 마시기 미션:
1. 물 마시고 나서 기분이 조금 달라졌어?
2. 다음에도 이 미션이면 할 수 있을 것 같아?

정리 미션:
1. 어떤 물건을 치웠어?
2. 치우고 나서 공간이 조금 달라 보였어?
```

### 제약 / 인덱스

```
unique(mission_id, question_order)
index(user_id, created_at)
check(question_order in 1, 2)
```

### 보상 지급 조건

```
mission_completion_answers에 question_order 1, 2가 모두 answer_text를 가진다.
missions.status를 COMPLETED로 변경한다.
별조각/EXP 지급 트랜잭션을 실행한다.
```

---

## 1.13 `mission_interactions`

미션 조회, 거절, 완료 등 사용자 반응 데이터를 저장한다.

개인화 추천, 거절률 분석, 완료율 분석에 사용한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 상호작용 ID |
| mission_id | bigint FK | 미션 ID |
| user_id | bigint FK | 사용자 ID |
| interaction_type | varchar | VIEWED / COMPLETION_STARTED / COMPLETED / REJECTED / EXPIRED |
| metadata_json | jsonb nullable | 날씨, 시간대, 캐릭터 상태 등 추가 context |
| created_at | timestamp | 생성일 |

<aside>

user_missions와 1대1 매칭 되는 테이블이다
interaction_type, metadata_json를 user_missions에 추가 해도 되지만
지금처럼 테이블로 분리 할 수도 있다
테이블로 분리 했을 때 어떤 이점이 있는지
(개인화 추천, 거절률 분석, 완료율 분석 등에 왜 유리한지)
알아볼 필요가 있다

mission_rejection_logs 로 할지 아니면 전체 미션 반응 데이터로 쌓을지…

mission_recommendation_logs → 이 테이블 필요한지? (미션 추천 로그)

</aside>

### 제약 / 인덱스

```
index(user_id, created_at)
index(mission_id, interaction_type)
index(interaction_type, created_at)
```

---

## 1.14 `ai_usage_logs`

AI 요청 비용, 지연, 실패율을 추적한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | AI 사용 로그 ID |
| user_id | bigint FK nullable | 사용자 ID |
| request_id | varchar unique | 요청 ID |
| model | varchar | 사용 모델 |
| prompt_tokens | int | 입력 토큰 |
| completion_tokens | int | 출력 토큰 |
| total_tokens | int | 총 토큰 |
| latency_ms | int | 응답 지연 |
| status | varchar | SUCCESS / FAILED / FALLBACK / RATE_LIMITED |
| error_type | varchar nullable | TIMEOUT / RATE_LIMIT / SERVER_ERROR / INVALID_OUTPUT |
| created_at | timestamp | 생성일 |

### 제약 / 인덱스

```
unique(request_id)
index(user_id, created_at)
index(model, created_at)
index(status, created_at)
```

---

## 1.15 `items`

상점에서 구매 가능한 아이템 마스터다.

| 컬럼                | 타입               | 설명                 |
|-------------------|------------------|--------------------|
| id                | bigint PK        | 아이템 ID             |
| name              | varchar          | 아이템명               |
| description       | text nullable    | 아이템 설명             |
| item_type         | varchar          | SKIN / CONSUMABLE  |
| price             | int              | 별조각 가격             |
| effect            | int              | 사용 효과              |
| effect_type       | varchar          | FOOD / REST / PLAY |
| image_url         | varchar nullable | 이미지 URL            |
| active            | boolean          | 판매 여부              |
| created_at        | timestamp        | 생성일                |
| updated_at        | timestamp        | 수정일                |
| character_type_id | bigint FK        | 캐릭터 타입 (특정 캐릭터용 아이템 구분) |

### 정책

```
SKIN/ACCESSORY/BACKGROUND은 장착형 아이템이다.
FOOD/SOAP/REST는 소모성 아이템이다.
모든 장착형 아이템은 MVP에서는 공통 아이템으로 간주한다.
캐릭터별 전용 장비 제한은 MVP에서 제외한다.
아이템 가격은 현금 가격이 아니라 별조각 가격이다.
```

### 제약 / 인덱스

```
index(active, category)
index(item_type)
```

---

## 1.16 `user_items`

사용자가 보유한 아이템과 장착 여부를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 보유 아이템 ID |
| user_id | bigint FK | 사용자 ID |
| item_id | bigint FK | 아이템 ID |
| quantity | int | 보유 수량 |
| equipped | boolean | 장착 여부 |
| acquired_at | timestamp | 최초 획득일 |
| updated_at | timestamp | 수정일 |

캐릭터에서 장비를 장착하는 컬럼을 보유하고 있기 때문에
user_items에서는 어떤 캐릭터가 장착을 했는지 정의하진 않는다
도메인이 나누어지는 상황을 상정에서 equipped를 관리 할지 판단하자


### 제약 / 인덱스

```
unique(user_id, item_id)
index(user_id, equipped)
index(equipped_character_id)
```

### 정책

```
장착형 아이템은 중복 구매할 수 없다.
소모성 아이템은 같은 row의 quantity를 증가시킨다.
별조각이 부족하면 구매할 수 없다.
아이템 구매와 별조각 차감은 하나의 트랜잭션으로 처리한다.
장착 슬롯당 하나만 장착하는 규칙은 application에서 검증한다.
소모품 사용 시 quantity를 1 감소시키고 character_care_logs를 생성한다.
```

---

## 1.17 `achievements`  → MVP 이후

업적 마스터를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 업적 ID |
| code | varchar unique | 업적 코드 |
| title | varchar | 업적명 |
| description | text | 설명 |
| achievement_type | varchar | DAILY / WEEKLY / ATTENDANCE / COLLECTION / ITEM / MISSION |
| condition_json | jsonb | 달성 조건 |
| reset_cycle | varchar | NONE / DAILY / WEEKLY |
| reward_star_piece | int | 보상 별조각 |
| active | boolean | 활성 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(code)
index(active, achievement_type)
```

---

## 1.18 `user_achievements` → MVP 이후

사용자별 업적 진행도를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 사용자 업적 ID |
| user_id | bigint FK | 사용자 ID |
| achievement_id | bigint FK | 업적 ID |
| progress | int | 진행도 |
| completed | boolean | 완료 여부 |
| reward_claimed | boolean | 보상 수령 여부 |
| completed_at | timestamp nullable | 완료일 |
| reward_claimed_at | timestamp nullable | 보상 수령일 |
| period_key | varchar nullable | DAILY/WEEKLY 업적 기간 키. 예: 2026-05-13, 2026-W20 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(user_id, achievement_id, period_key)
index(user_id, completed)
index(user_id, reward_claimed)
```

### 비고

```
reset_cycle이 NONE이면 period_key는 'NONE' 또는 null 정책 중 하나로 통일한다.
MVP에서는 'NONE' 문자열을 추천한다.
```

---

## 1.19 `attendance_records`

출석 기록을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 출석 ID |
| user_id | bigint FK | 사용자 ID |
| attendance_date | date | 출석일 |
| streak_count | int | 연속 출석 수 |
| reward_star_piece | int | 출석 보상 |
| created_at | timestamp | 생성일 |

### 제약 / 인덱스

```
unique(user_id, attendance_date)
index(user_id, attendance_date)
```
---

## 1.20 `notifications`

앱 내부 알림을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 알림 ID |
| user_id | bigint FK | 수신 사용자 ID |
| character_id | bigint FK nullable | 말한 캐릭터 ID |
| notification_type | varchar | MISSION / CARE / ACHIEVEMENT / ATTENDANCE / SHARE / SYSTEM |
| title | varchar | 제목 |
| message | text | 내용 |
| target_type | varchar nullable | MISSION / CHARACTER / ITEM / ACHIEVEMENT / SHARE |
| target_id | bigint nullable | 이동 대상 ID |
| read | boolean | 읽음 여부 |
| sent_at | timestamp nullable | 발송 시각 |
| created_at | timestamp | 생성일 |

### 제약 / 인덱스

```
index(user_id, read, created_at)
index(notification_type, created_at)
```

---

## 1.21 `push_subscriptions`

Web Push 알림 구독 정보를 저장한다.

푸시 알림이 일정상 밀리면 이 테이블은 migration 후순위로 둘 수 있다. 다만 알림 MVP를 포함한다면 필요한 최소 테이블이다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 구독 ID |
| user_id | bigint FK | 사용자 ID |
| endpoint | text | Push endpoint |
| p256dh | text | Push key |
| auth | text | Auth secret |
| active | boolean | 활성 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
index(user_id, active)
```

---

## 1.22 `event_logs`

제품 이벤트와 운영 지표 계산용 로그를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 이벤트 ID |
| user_id | bigint nullable | 사용자 ID |
| event_type | varchar | 이벤트 타입 |
| ref_type | varchar nullable | 참조 타입 |
| ref_id | bigint nullable | 참조 ID |
| metadata_json | jsonb nullable | 추가 데이터 |
| created_at | timestamp | 생성일 |

### 주요 event_type

```
USER_SIGNED_UP
ONBOARDING_COMPLETED
CHARACTER_CREATED
MISSION_GENERATED
MISSION_OFFERED
MISSION_REJECTED
MISSION_COMPLETION_QA_STARTED
MISSION_COMPLETED
STAR_PIECE_EARNED
STAR_PIECE_SPENT
ITEM_PURCHASED
ITEM_EQUIPPED
CARE_ACTION_PERFORMED
ACHIEVEMENT_COMPLETED
ATTENDANCE_CHECKED
CHARACTER_CARD_SHARED
NOTIFICATION_CREATED
NOTIFICATION_CLICKED
AI_MISSION_GENERATION_FAILED
AI_FALLBACK_USED
```

### 제약 / 인덱스

```
index(event_type, created_at)
index(user_id, created_at)
index(ref_type, ref_id)
```

---

