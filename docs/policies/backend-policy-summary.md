# 백엔드 개발자용 정책 정리

> 기준일: 2026-05-26
> 이 문서는 현재 `polaris` 백엔드 구현 기준으로 작성한다. 상세 API 계약은 `docs/sa-docs/01-API-spec.md`, 실제 테이블 구조는 `docs/sa-docs/02_ERD_Data_Model.md`를 따른다.

---

## 1. 인증 정책

| 항목 | 정책 |
| --- | --- |
| 기본 로그인 | Google OAuth2 우선 |
| 외부 REST 인증 | `Authorization: Bearer {accessToken}` |
| refresh token | `users.refresh_token`에 저장 |

---

## 2. 에셋 / URL 저장 정책

- DB에는 이미지 파일 자체를 저장하지 않는다.
- 캐릭터/스킨 에셋은 `character_assets.asset_url`, `skin_assets.asset_url`에 asset key를 저장한다.
- 공유 카드 이미지는 `share_cards.image_url`에 S3 object key를 저장한다.
- API 응답 시점에 환경별 public base URL 또는 S3 public domain과 key를 조합해 클라이언트가 사용할 URL을 만든다.
- `currentAssetUrl`은 서버가 현재 상태 기준으로 선택한 표시용 URL이다.
- `assetUrls`는 상태 전환과 프리로드를 위한 상태별 URL map이다.
- 두 필드가 함께 내려갈 때 `currentAssetUrl`은 `assetUrls` 중 현재 상태에 해당하는 값과 일치해야 한다.

---

## 3. 캐릭터 상태 정책

| 내부 지표 | 화면 표현 | 등급 기준 |
| --- | --- | --- |
| `fullness` | 든든함 / 출출함 / 배고픔 | GOOD: 70~100, NORMAL: 40~69, BAD: 0~39 |
| `energy` | 말짱함 / 졸림 / 피곤함 | GOOD: 70~100, NORMAL: 40~69, BAD: 0~39 |
| `affection` | 가까움 / 조용함 / 쓸쓸함 | GOOD: 70~100, NORMAL: 40~69, BAD: 0~39 |

### 공통 정책

- 내부 수치는 모두 높을수록 좋은 상태로 관리한다.
- 상태 수치는 0~100 범위를 벗어나지 않는다.
- 사용자는 활성 캐릭터 1개를 가진다.
- 스킨 장착 정보는 `user_characters.equipped_skin_id`를 단일 소스로 사용한다.
- 스킨이 장착되어 있으면 `skin_assets`, 기본 외형이면 `character_assets`를 기준으로 상태별 이미지를 선택한다.

---

## 4. 상태 감소 / 회복 정책

### 상태 감소

| 상태 | 감소 조건 | 감소량 |
| --- | --- | --- |
| `fullness` | 시간 경과 | 6시간마다 -10 |
| `energy` | 시간 경과 | 8시간마다 -10 |
| `affection` | 시간 경과 | 24시간마다 -10 |

### 돌봄 회복

| 액션 | 필요한 소모품 `effectType` | 회복 지표 | 현재 회복량 |
| --- | --- | --- | --- |
| `FEED` | `FOOD` | `fullness` | +30 |
| `SLEEP` | `REST` | `energy` | +30 |
| `PLAY` | `PLAY` | `affection` | +30 |

### 정합성

- 돌봄 액션은 보유한 `CONSUMABLE` 아이템을 사용해야 한다.
- 별조각을 직접 차감해 돌봄 액션을 수행하지 않는다. 별조각은 상점에서 아이템을 구매할 때 사용한다.
- 소모품 사용 시 item 모듈의 `UseItem` gRPC를 호출해 `user_items.quantity`를 1 감소시킨다.
- 돌봄 결과는 `character_care_logs`에 기록한다.
- 돌봄 액션은 `Idempotency-Key` 기준으로 중복 요청을 방어한다.

---

## 5. 온보딩 설문 정책

- 온보딩 응답은 `onboarding_profiles`에 저장한다.
- 설문 결과는 미션 추천과 AI 문구 생성 context로 사용한다.
- MVP에서는 질문 마스터 테이블을 따로 두지 않고, 정해진 설문 결과를 프로필 row에 저장한다.

---

## 6. 미션 제공 정책

- 미션은 사용자에게 한 번에 하나의 현재 미션으로 제공한다.
- 오늘 제안된 미션은 `user_missions.stack_order`로 stack을 관리한다.
- 사용자는 현재 미션을 완료하거나 거절할 수 있다.
- 거절하면 다음 미션을 받을 수 있다.
- 하루 최대 제안 수는 15개다.
- 현재 미션은 해당 날짜의 최신 `OFFERED` 또는 `ANSWERING` 상태 미션이다.

### 미션 템플릿 선택

- 완전 자유 생성이 아니라 `seed 미션 템플릿 선택 + 캐릭터 말투 변환` 구조로 동작한다.
- 후보 미션은 `active=true`인 `mission_templates`만 사용한다.
- 오늘 이미 사용된 템플릿은 다시 선택하지 않는다.
- 템플릿 순서는 `userId + missionDate + templateId` 기반 해시로 정렬해, 같은 사용자의 같은 날짜에는 안정적이고 날짜가 바뀌면 달라지는 랜덤 순서를 만든다.
- AI는 seed 미션의 제목, 카테고리, 난이도, 보상을 바꾸지 않고 제안 문구, 완료 질문, 완료 반응만 생성한다.
- AI 호출 실패 또는 응답 검증 실패 시 `mission_templates`의 fallback 문구를 사용한다.

---

## 7. 미션 상태 / 완료 정책

| 상태 | 의미 |
| --- | --- |
| `GENERATED` | 생성됐지만 아직 사용자에게 노출되지 않음 |
| `OFFERED` | 현재 또는 과거에 사용자에게 제안됨 |
| `ANSWERING` | 완료 클릭 후 질문 답변 중 |
| `COMPLETED` | 답변 저장 후 완료 처리됨 |
| `REJECTED` | 사용자가 거절 |
| `EXPIRED` | 날짜 변경 또는 제한 조건으로 만료 |

### 완료 / 답변

- 완료 버튼 클릭 시 미션 상태는 `ANSWERING`이 된다.
- 질문은 1개만 제공한다.
- 답변 방식은 텍스트 입력이다.
- 답변은 최소 1자 이상, 최대 300자 이하로 제한한다.
- 답변 완료 후 미션 상태는 `COMPLETED`가 된다.
- 미션 완료 보상은 `MISSION_REWARD:{missionId}` 멱등키로 1회만 지급한다.
- 보상 지급 요청은 `mission_reward_outbox`에 기록하고, wallet 모듈 지급 성공 시 `user_missions.idempotency_key`에 같은 marker를 저장한다.
- 일시 실패한 보상은 outbox 스케줄러가 `next_attempt_at`, `attempt_count` 기준으로 재처리한다.

---

## 8. 별조각 정책

### 획득처

| 획득처 | 보상 | 현재 처리 |
| --- | --- | --- |
| 미션 완료 | 10 | wallet 적립 + `star_piece_transactions` 기록 |
| 출석 | 10 | wallet 적립 + `attendance_records`, `star_piece_transactions` 기록 |
| SNS 공유 시도 | 10 | `share_logs`에 일일 보상 대상 여부 기록. wallet 적립은 현재 연동 전 |

### 사용처

| 사용처 | 정책 |
| --- | --- |
| 아이템 구매 | `items.price * quantity`만큼 별조각 차감 |
| 돌봄 액션 | 별조각 직접 차감 없음. 구매한 소모품을 사용 |

### 원장 정책

- 별조각 증감은 반드시 `star_piece_transactions`에 기록한다.
- 아이템 구매 실패 시 별조각은 차감하지 않는다.
- 결제 또는 현금 구매를 통한 별조각 지급은 MVP 범위에 없다.

---

## 9. 아이템 정책

| 구분 | 정책 |
| --- | --- |
| `SKIN` | 장착형 아이템 |
| `CONSUMABLE` | 돌봄 액션에 사용하는 소모품 |
| `FOOD` | `FEED` 액션에 사용하는 effectType |
| `REST` | `SLEEP` 액션에 사용하는 effectType |
| `PLAY` | `PLAY` 액션에 사용하는 effectType |

### 구매 / 보유 / 장착

- 아이템 구매는 body의 `idempotencyKey`로 중복 차감을 방어한다.
- 구매 성공 시 `user_items` 보유 수량을 생성 또는 증가시키고, `item_purchase_histories`에 구매 이력을 남긴다.
- 스킨은 중복 구매할 수 없다.
- 소모성 아이템은 같은 row의 `quantity`를 증가시킨다.
- 스킨 장착 상태는 item 모듈이 아니라 character 모듈의 `user_characters.equipped_skin_id`가 단일 소스다.

---

## 10. 공유 카드 / 공유 보상 정책

### 공유 카드 생성

- 공유카드는 프론트엔드가 미리보기 화면을 canvas PNG로 생성한다.
- 백엔드는 presigned PUT URL을 발급하고, 프론트엔드는 S3에 직접 업로드한다.
- `POST /api/share/v1/share-cards`에서 백엔드는 전달받은 이미지 URL의 소유자/경로를 검증한다.
- DB에는 공유 이미지 object key와 공유 식별자를 저장하고, 응답에서 public URL로 조립한다.
- 같은 사용자가 같은 이미지로 공유 카드 생성을 재시도하면 기존 공유 카드를 반환한다.

### 공유 보상

- 공유 보상 기준은 공유 이미지 콘텐츠가 아니라 서버에 기록된 공유 이벤트다.
- `share_logs`는 공유 시도, 일일 보상 여부, 멱등키를 저장한다.
- 공유 보상은 사용자별 하루 1회만 `reward_paid=true`가 될 수 있다.
- 현재 공유 보상은 wallet 적립 연동 전 단계라 응답의 `wallet.starPiece`는 0일 수 있다.
- 실제 외부 SNS 게시 여부는 MVP에서 검증하지 않는다.

### 공유 유입

- 공개 공유 링크 클릭 API는 현재 DB 테이블을 만들지 않고 애플리케이션 로그를 남긴 뒤 `recorded=true`를 반환한다.
- OG 공유 HTML은 `share_cards.image_url`에서 조립한 공개 이미지 URL을 `og:image`로 사용한다.

---

## 11. 알림 정책

### 앱 내부 알림

- 앱 내부 알림은 `notifications`에 저장한다.
- mission, character 등 내부 서비스는 `NotificationService.SendPushNotification` gRPC로 알림 생성을 요청한다.
- 홈 화면의 `notifications.unreadCount`는 `NotificationService.GetUnreadNotificationCount` gRPC로 조회한다.
- 읽음 여부는 `notifications.is_read`, `read_at`으로 관리한다.

### FCM 푸시

- FCM registration token은 `fcm_device_tokens`에 저장한다.
- 같은 token hash가 다시 등록되면 기존 토큰 정보를 갱신한다.
- 알림 row는 먼저 저장하고, FCM 발송은 비동기로 처리한다.
- FCM 발송 성공/실패/스킵 결과는 `notification_push_deliveries`에 기록한다.
- 유효하지 않은 토큰은 비활성화한다.

### 발송 판단

- 최종 FCM 발송 여부는 백엔드가 결정한다.
- `push_enabled=false`면 FCM 발송을 건너뛴다.
- 미션 제안 알림은 `mission_offer_enabled`, 캐릭터 상태 알림은 `character_state_enabled`, 출석/일일 리마인더는 `daily_reminder_enabled`를 따른다.
- `quiet_hours_enabled=true`이고 현재 시간이 `quiet_hours_start`~`quiet_hours_end` 범위면 FCM 발송을 건너뛴다.
- 기본 방해 금지 시간 값은 22:00~08:00이며, 기본 설정에서는 방해 금지 시간이 꺼져 있다.
- `daily_push_limit`은 사용자 설정 값으로 저장한다.
- 알림 문구는 죄책감을 유발하지 않고 캐릭터 말투를 반영한다.

---

## 12. 이벤트 로그 정책

- 운영 분석과 디버깅용 이벤트는 `event_logs`에 저장한다.
- `event_id`는 멱등 처리를 위한 UUID다.
- 이벤트 발생 서비스는 `source_service`로 구분한다.
- 이벤트 상세 속성은 `properties_json`, 발생 환경은 `context_json`에 저장한다.
- 실제 발생 시각은 `occurred_at`, DB 저장 시각은 `created_at`으로 분리한다.

---

## 13. MVP 제외 / 이후 확장

- 별조각 현금 구매
- 결제 테이블 및 PG 연동
- 업적 보상 지급
- 광고 API 및 광고 보상
- `mission_interactions` 분석 테이블
- 공유 클릭 전용 DB 테이블
- 이메일/비밀번호 로그인
