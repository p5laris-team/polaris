# Polaris REST API 명세서

> 기준일: 2026-05-21
> 기준 문서: Polaris v0.7 PRD, 최신 ERD, 기존 API 초안, `polaris` 백엔드 gateway/proto 코드 대조

--- 

## 0. 공통 규칙

### 0.1 URL 규칙

```text
Base Pattern: /api/{domain}/v1/{resource}
```

예시:

```text
/api/auth/v1/google/sessions
/api/character/v1/characters/me
/api/mission/v1/missions/current
/api/item/v1/items
```

### 0.2 표기

| 표시 | 의미 |
|---|---|
| 🔐 | 인증 필요. `Authorization: Bearer {accessToken}` 필요 |
| 💾 | 캐싱 권장 API. 자주 바뀌지 않는 조회성 데이터 |
| ⚠️ | 동시성 민감 API. 중복 지급, 잔액 차감, 수량 차감, 일일 제한 처리 주의 |
| 🧩 | 프론트 구현에는 필요하지만 현재 백엔드 코드 기준 미구현 또는 응답 필드 보강 필요 |

### 0.3 응답 포맷

모든 API 응답은 `ApiResponse`로 감싼다. API별 Response 예시는 반복을 줄이기 위해 `data` 내부만 작성한다.

#### 성공 응답

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

#### 실패 응답

요청 처리에 실패한 경우 `success`는 `false`, `data`는 `null`로 응답한다.  
실패 원인은 `error` 객체에 담아 반환한다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "timestamp": "2026-05-15T13:42:10+09:00",
    "status": 400,
    "code": "MISSION_INVALID_STATUS",
    "message": "현재 상태에서는 미션을 완료할 수 없습니다.",
    "path": "/api/mission/v1/missions/10/completion-answer"
  }
}
```

`retryAfterSeconds`는 기본 필드는 아니고, AI 요청 제한이나 일시적 제한 상황에서만 선택적으로 포함한다.
```json
{
  "success": false,
  "data": null,
  "error": {
    "timestamp": "2026-05-15T13:42:10+09:00",
    "status": 429,
    "code": "AI_RATE_LIMIT_EXCEEDED",
    "message": "잠시 후 다시 시도해 주세요.",
    "path": "/api/mission/v1/missions/current",
    "retryAfterSeconds": 30
  }
}
```

### 0.4 페이지네이션

목록 조회는 cursor 기반을 우선한다.

```json
{
  "items": [],
  "pageInfo": {
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasNext": true,
    "size": 20
  }
}
```

### 0.5 Idempotency

중복 요청이 위험한 API는 `idempotencyKey`를 받는다.

대상 예시:

- 미션 생성/제안
- 미션 완료 보상 지급
- 아이템 구매
- 캐릭터 돌봄 액션
- 공유 보상 지급
- 출석 보상 지급

### 0.6 프론트-백엔드 연동 갭

아래 표는 2026-05-21 기준 `polaris` 백엔드 코드와 `polaris-frontend` 실제 API 호출을 대조했을 때 아직 정리가 필요한 항목이다. 이미 백엔드 구현과 명세가 정렬된 API는 이 표에서 제외한다.

| 구분 | 프론트 필요 API/필드 | 현재 백엔드 코드 확인 | 프론트 영향 / 요청 |
|---|---|---|---|
| 홈 통합 조회 | `GET /api/home/v1/home` | gateway에 home 컨트롤러 없음 | 홈, 상단 요약, 현재 미션, 알림 개수 표시를 위해 백엔드 통합 조회 API 추가가 필요하다. 대안으로 프론트가 user/wallet/character/mission/notification을 조합 호출하도록 별도 결정해야 한다. |
| 현재 캐릭터 | `GET /api/character/v1/characters/me` 응답의 `states`, `currentAssetUrl` | 현재 응답은 `id`, `name`, `characterTypeCode`, `active`, `equippedSkin` 중심 | 캐릭터 상세/보관함이 캐릭터 상태와 현재 에셋을 바로 쓰려면 응답 보강이 필요하다. 아니면 프론트가 status/assets API를 추가 호출해야 한다. |
| 스킨 해제 | `PUT /api/character/v1/characters/{characterId}/equipped-skin` body `itemId: null` | proto `item_id`는 `int64`, gateway에서 null 처리 없음 | 보관함의 "해제" 버튼을 실제 API와 연결하려면 null 해제 정책 또는 별도 해제 endpoint가 필요하다. |
| 지갑 거래내역 | `GET /api/wallet/v1/wallets/me/transactions` | `star_piece_transactions` 테이블은 있으나 REST/gRPC 조회 없음 | 별조각 화면의 거래내역 리스트에 필요하다. cursor 기반 최신순 조회가 필요하다. |
| 상점 아이템 필드 | `GET /api/item/v1/items` 응답의 `characterTypeId`, `effectType` | entity에는 있으나 item proto/gateway 응답에는 없음 | 캐릭터별 스킨 필터와 소모품 라벨 표시를 안정적으로 처리하려면 응답 보강이 필요하다. |
| 보유 아이템 필드 | `GET /api/item/v1/user-items` 응답의 `characterTypeId`, `imageUrl` | 현재 응답은 `userItemId`, `itemId`, `name`, `itemType`, `effectType`, `quantity` | 보관함의 캐릭터별 스킨 필터와 이미지 렌더링에 필요하다. |
| 아이템 구매 멱등성 | `POST /api/item/v1/item-purchases`의 클라이언트 재시도 멱등키 | 현재 gateway가 `purchase-{userId}-{itemId}-{currentTimeMillis}`를 생성 | 같은 구매 요청 재시도 시 중복 차감 방지가 약하다. 실제 API 연동 전 client-provided `Idempotency-Key` 또는 body `idempotencyKey` 정책 결정이 필요하다. |
| 공유 보상 상태 | `GET /api/share/v1/share-events/today` | share controller에 today 상태 조회 없음 | 공유 카드 버튼 상태와 중복 보상 안내에 필요하다. |
| 알림 | `GET/PATCH /api/notification/v1/notifications` | gateway에 notification controller 없음 | 알림 목록, 읽음 처리, 홈 unreadCount에 필요하다. |
| 지갑 요약 부가 필드 | `GET /api/wallet/v1/wallets/me`의 `updatedAt` | 현재 백엔드 응답은 `starPiece`만 반환 | MVP 화면은 잔액만 쓰므로 `updatedAt`은 선택 필드로 둔다. |

---

## 1. API 전체 요약

| Method | Endpoint                                                        | 설명            | Request | Response | 인증 |
|--------|-----------------------------------------------------------------|---------------|---|---|---|
| GET    | `/api/auth/v1/google/authorization-url`                         | Google OAuth2 시작 URL 조회 | query | OAuth URL | Public |
| POST   | `/api/auth/v1/google/sessions`                                  | Google OAuth2 로그인 세션 생성 | body | token + user | Public |
| POST   | `⚠️ /api/auth/v1/token-refreshes`                               | 토큰 재발급        | body | token | Public |
| DELETE | `/api/auth/v1/sessions/current`                                 | 로그아웃          | none | logout result | 🔐 |
| GET    | `/api/user/v1/users/me`                                         | 내 정보 조회       | none | user | 🔐 |
| GET    | `🧩 /api/home/v1/home`                                           | 홈 화면 통합 조회    | none | home data | 🔐 |
| GET    | `💾 /api/character/v1/character-types`                          | 캐릭터 종류 조회     | query | character types | 🔐 |
| GET    | `💾 /api/character/v1/character-types/{characterTypeId}/assets` | 캐릭터 에셋 조회     | path | assets | 🔐 |
| POST   | `/api/character/v1/characters`                                  | 내 캐릭터 생성      | body | character | 🔐 |
| GET    | `🧩 /api/character/v1/characters/me`                             | 내 활성 캐릭터 조회   | none | character | 🔐 |
| PATCH  | `/api/character/v1/characters/{characterId}`                    | 캐릭터 이름 수정     | path + body | character | 🔐 |
| GET    | `/api/character/v1/characters/{characterId}/status`             | 캐릭터 상태 조회     | path | status | 🔐 |
| POST   | `⚠️ /api/character/v1/characters/{characterId}/care-logs`       | 돌봄 액션 수행      | path + body | care result | 🔐 |
| PUT    | `⚠️🧩 /api/character/v1/characters/{characterId}/equipped-skin` | 캐릭터 스킨 장착/해제 | path + body | equipped skin | 🔐 |
| GET    | `💾 /api/onboarding/v1/questions`                               | 온보딩 질문 목록 조회  | none | questions | 🔐 |
| GET    | `/api/onboarding/v1/profiles/me`                                | 내 온보딩 프로필 조회  | none | profile | 🔐 |
| PUT    | `/api/onboarding/v1/profiles/me`                                | 내 온보딩 프로필 저장/완료 | body | profile | 🔐 |
| GET    | `/api/mission/v1/missions/current`                              | 현재 제안 미션 조회   | query | mission | 🔐 |
| GET    | `/api/mission/v1/missions/today`                                 | 오늘 미션 스택 조회   | none | today missions | 🔐 |
| POST   | `/api/mission/v1/missions/today-focus/next`                     | 다음 미션 요청      | body | mission | 🔐 |
| POST   | `/api/mission/v1/missions/{missionId}/rejections`               | 미션 거절 기록 생성   | path + body | rejection | 🔐 |
| POST   | `/api/mission/v1/missions/{missionId}/completion-sessions`      | 완료 질문 세션 시작   | path + body | question | 🔐 |
| POST   | `⚠️ /api/mission/v1/missions/{missionId}/completion-answers`    | 완료 답변 제출 및 보상 지급 | path + body | completion result | 🔐 |
| GET    | `🧩 /api/wallet/v1/wallets/me`                                   | 별조각 잔액 조회     | none | wallet | 🔐 |
| GET    | `🧩 /api/wallet/v1/wallets/me/transactions`                      | 별조각 거래내역 조회   | query cursor | transactions | 🔐 |
| GET    | `💾🧩 /api/item/v1/items`                                       | 상점 아이템 목록 조회  | query cursor | items | 🔐 |
| GET    | `🧩 /api/item/v1/user-items`                                     | 내 보유 아이템 조회   | query cursor | user items | 🔐 |
| POST   | `⚠️ /api/item/v1/item-purchases`                                | 아이템 구매        | body | purchase result | 🔐 |
| GET    | `/api/share/v1/presigned-url`                                   | 프론트엔드 S3 직접 업로드용 임시 URL 발급 | query | presigned url | 🔐 |
| POST   | `/api/share/v1/share-cards`                                     | 공유 카드 생성 (업로드 이미지 URL 포함) | body | share card | 🔐 |
| POST   | `⚠️ /api/share/v1/share-events`                                 | 공유 시도 이벤트 생성 및 보상 처리 | body | share event | 🔐 |
| GET    | `🧩 /api/share/v1/share-events/today`                            | 오늘 공유 보상 여부 조회 | none | share status | 🔐 |
| GET    | `💾 /api/share/v1/share-links/{shareId}`                        | 공개 공유 링크 정보 조회 (클릭 로그 내재화) | path | shared card | Public |
| POST   | `⚠️ /api/attendance/v1/attendance-records`                      | 오늘 출석 기록 생성 및 보상 지급 | none | attendance | 🔐 |
| GET    | `/api/attendance/v1/attendance-records`                         | 출석 기록 조회      | query year, month | attendance list | 🔐 |
| GET    | `🧩 /api/notification/v1/notifications`                          | 알림 목록 조회      | query cursor | notifications | 🔐 |
| PATCH  | `🧩 /api/notification/v1/notifications/{notificationId}`         | 알림 읽음 처리      | path + body | notification | 🔐 |

---

## 2. 인증 / 사용자

인증은 Google OAuth2를 우선한다. 이메일/비밀번호 로그인은 선택 구현이다.

### 2.1 GET `/api/auth/v1/google/authorization-url`

**설명**  
프론트가 Google 로그인 버튼을 눌렀을 때 이동할 OAuth URL을 받는다.

**Request**

```json
{
  "redirectUri": "https://polaris.app/oauth/google/callback"
}
```

**Response**

```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "state": "oauth-state-token"
}
```

---

### 2.2 POST `/api/auth/v1/google/sessions`

**설명**  
Google OAuth callback에서 받은 `code`를 서버에 전달하고 서비스 토큰을 발급받는다.

**Request**

```json
{
  "code": "google-oauth-code",
  "state": "oauth-state-token",
  "redirectUri": "https://polaris.app/oauth/google/callback"
}
```

**Response**

```json
{
  "accessToken": "access.jwt",
  "refreshToken": "refresh.jwt",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "별따라걷기",
    "provider": "GOOGLE",
    "role": "USER"
  }
}
```

---

### 2.3 POST `⚠️ /api/auth/v1/token-refreshes`

**설명**  
Refresh Token으로 Access Token을 재발급한다.

**Request**

```json
{
  "refreshToken": "refresh.jwt"
}
```

**Response**

```json
{
  "accessToken": "new-access.jwt",
  "refreshToken": "new-refresh.jwt"
}
```

---

### 2.4 DELETE `/api/auth/v1/sessions/current` 🔐

**설명**  
현재 로그인 세션을 종료한다.

**Request**

```json
{}
```

**Response**

```json
{
  "loggedOut": true
}
```

---

### 2.5 GET `/api/user/v1/users/me` 🔐

**설명**  
현재 로그인한 사용자 정보를 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 1,
  "email": "user@example.com",
  "nickname": "별따라걷기",
  "provider": "GOOGLE",
  "role": "USER",
  "status": "ACTIVE"
}
```

---

## 3. 홈

### 3.1 GET `/api/home/v1/home` 🔐

**설명**  
홈 화면에 필요한 사용자, 지갑, 캐릭터, 현재 미션, 알림 요약을 한 번에 조회한다.

> 백엔드 코드 확인: 2026-05-21 기준 gateway에 해당 REST 컨트롤러가 없다. 프론트는 현재 단일 홈 조회 API를 기준으로 구현되어 있으므로, 백엔드 통합 조회 API를 추가하거나 프론트에서 개별 API 조합 호출로 전환하는 결정이 필요하다.

**Request**

```json
{}
```

**Response**

```json
{
  "user": {
    "id": 1,
    "nickname": "별따라걷기"
  },
  "wallet": {
    "starPiece": 120
  },
  "character": {
    "id": 10,
    "name": "작은노바",
    "characterTypeCode": "NOVA",
    "currentAssetUrl": "https://cdn.polaris.app/nova/idle.png",
    "states": {
      "hunger": { "value": 80, "label": "든든함", "grade": "GOOD" },
      "energy": { "value": 55, "label": "졸림", "grade": "NORMAL" },
      "affection": { "value": 35, "label": "쓸쓸함", "grade": "BAD" }
    }
  },
  "currentMission": {
    "id": 100,
    "title": "물 한 컵 마시기",
    "characterMessage": "물 한 컵 마셔볼래? 나도 빛 좀 마셔볼게.",
    "status": "OFFERED",
    "rewardStarPiece": 5
  },
  "notifications": {
    "unreadCount": 2
  }
}
```

---

## 4. 캐릭터

### 4.1 GET `💾 /api/character/v1/character-types` 🔐

**설명**  
선택 가능한 캐릭터 타입 목록을 조회한다. MVP 캐릭터는 노바, 무무, 쪼리 3종이다.

**Request**

```json
{
  "active": true
}
```

**Response**

```json
{
  "items": [
    {
      "id": 1,
      "code": "NOVA",
      "name": "노바",
      "summary": "자기가 별이었다는 걸 까먹은 별알",
      "sampleLine": "오늘도… 있었네.",
      "sortOrder": 1
    }
  ]
}
```

---

### 4.2 GET `💾 /api/character/v1/character-types/{characterTypeId}/assets` 🔐

**설명**  
캐릭터 타입별 이미지 에셋을 조회한다.

**Request**

```json
{
  "characterTypeId": 1
}
```

**Response**

```json
{
  "characterTypeId": 1,
  "items": [
    {
      "assetType": "IDLE",
      "assetUrl": "https://cdn.polaris.app/nova/idle.png"
    },
    {
      "assetType": "STATE",
      "assetUrl": "https://cdn.polaris.app/nova/lonely.png"
    }
  ]
}
```

---

### 4.3 POST `/api/character/v1/characters` 🔐

**설명**  
사용자의 활성 캐릭터를 생성한다. MVP에서는 사용자당 활성 캐릭터 1개를 기준으로 한다.

**Request**

```json
{
  "characterTypeId": 1,
  "name": "작은노바"
}
```

**Response**

```json
{
  "id": 10,
  "name": "작은노바",
  "characterTypeCode": "NOVA",
  "active": true,
  "states": {
    "hunger": 70,
    "energy": 70,
    "affection": 50
  },
  "createdAt": "2026-05-15T18:00:00+09:00"
}
```

---

### 4.4 GET `/api/character/v1/characters/me` 🔐

**설명**  
내 활성 캐릭터를 조회한다.

> 백엔드 코드 확인: 현재 gateway 응답에는 `states`, `currentAssetUrl`이 포함되지 않는다. 프론트에서 캐릭터 상태/에셋을 함께 쓰는 화면은 `GET /api/character/v1/characters/{characterId}/status`, `GET /api/character/v1/character-types/{characterTypeId}/assets` 조합 호출로 갈지, 이 응답을 보강할지 결정이 필요하다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 10,
  "name": "작은노바",
  "characterTypeCode": "NOVA",
  "active": true,
  "equippedSkin": {
    "itemId": 3,
    "name": "말랑 별빛 스킨"
  }
}
```

---

### 4.5 PATCH `/api/character/v1/characters/{characterId}` 🔐

**설명**  
캐릭터 이름을 수정한다. 이름은 1~10자로 제한한다.

**Request**

```json
{
  "name": "노바별"
}
```

**Response**

```json
{
  "id": 10,
  "name": "노바별",
  "updatedAt": "2026-05-15T18:05:00+09:00"
}
```

---

### 4.6 GET `/api/character/v1/characters/{characterId}/status` 🔐

**설명**  
캐릭터 상태값과 화면 표시용 라벨을 조회한다.

**Request**

```json
{
  "characterId": 10
}
```

**Response**

```json
{
  "characterId": 10,
  "states": {
    "hunger": { "value": 80, "label": "든든함", "grade": "GOOD" },
    "energy": { "value": 55, "label": "졸림", "grade": "NORMAL" },
    "affection": { "value": 35, "label": "쓸쓸함", "grade": "BAD" }
  }
}
```

---

### 4.7 POST `⚠️ /api/character/v1/characters/{characterId}/care-logs` 🔐

**설명**  
밥 주기, 재우기, 놀아주기 같은 돌봄 액션을 수행한다. MVP 클라이언트는 돌봄 액션에 맞는 소모품 `itemId`를 전달하며, 백엔드는 `user_items.quantity`를 1개 차감한다.
중복 요청 및 재시도를 방지하기 위해 반드시 `Idempotency-Key` 헤더를 함께 전송해야 합니다.

| actionType | 필요한 effectType | 예시 소모품 |
|---|---|---|
| `FEED` | `FOOD` | 별사탕밥 |
| `SLEEP` | `REST` | 구름 베개 |
| `PLAY` | `PLAY` | 별 장난감 |

**Headers**

| Name | Type | Description | Required |
|---|---|---|---|
| `Idempotency-Key` | String | 중복 요청 및 재시도 방지용 고유 멱등키 (예: UUID 또는 고유 문자열) | Yes |

**Request**

```json
{
  "actionType": "FEED",
  "itemId": 21
}
```

**Response**

```json
{
  "careLogId": 300,
  "characterId": 10,
  "actionType": "FEED",
  "consumed": {
    "itemId": 21,
    "quantity": 1
  },
  "beforeStates": {
    "hunger": 50,
    "energy": 55,
    "affection": 35
  },
  "afterStates": {
    "hunger": 80,
    "energy": 55,
    "affection": 35
  },
  "characterMessage": "먹는 중… 빛도 맛이 있구나."
}
```

---

### 4.8 PUT `⚠️ /api/character/v1/characters/{characterId}/equipped-skin` 🔐

**설명**  
캐릭터에 스킨을 장착하거나 기본 외형으로 해제한다. 스킨은 한 번에 하나만 적용한다.

- `itemId`가 숫자이면 해당 보유 스킨을 장착한다.
- `itemId`가 `null`이면 현재 장착 스킨을 해제하고 기본 외형으로 되돌린다.
- 클라이언트는 `GET /api/character/v1/characters/me` 응답의 `equippedSkin.itemId`와 `GET /api/item/v1/user-items` 응답의 `itemId`를 비교해 장착 여부를 표시한다.

> 백엔드 코드 확인: 현재 proto의 `item_id`는 `int64`라 `null` 표현이 없고, gateway도 `itemId: null` 해제를 처리하지 않는다. 프론트 보관함의 해제 버튼을 실제 API와 연결하려면 null 해제 정책 또는 별도 해제 endpoint가 필요하다.

**Request**

장착:

```json
{
  "itemId": 3
}
```

해제:

```json
{
  "itemId": null
}
```

**Response**

장착:

```json
{
  "characterId": 10,
  "equippedSkin": {
    "itemId": 3,
    "name": "말랑 별빛 스킨"
  },
  "updatedAt": "2026-05-15T18:40:00+09:00"
}
```

해제:

```json
{
  "characterId": 10,
  "equippedSkin": null,
  "updatedAt": "2026-05-15T18:45:00+09:00"
}
```

---

## 5. 온보딩

### 5.1 GET `💾 /api/onboarding/v1/questions` 🔐

**설명**  
온보딩 고정 질문 목록을 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "items": [
    {
      "key": "livingType",
      "question": "지금 생활 환경은 어떤가요?",
      "type": "SINGLE_CHOICE",
      "options": [
        { "value": "LIVING_ALONE", "label": "혼자 살아요" },
        { "value": "WITH_FAMILY", "label": "가족과 살아요" }
      ]
    }
  ]
}
```

---

### 5.2 GET `/api/onboarding/v1/profiles/me` 🔐

**설명**  
내 온보딩 프로필과 완료 여부를 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "completed": true,
  "livingType": "LIVING_ALONE",
  "wakeUpTime": "08:00",
  "sleepTime": "24:00",
  "routineGoal": "SELF_CARE",
  "activityPreference": "INDOOR",
  "missionIntensity": "LIGHT",
  "completedAt": "2026-05-15T18:10:00+09:00"
}
```

---

### 5.3 PUT `/api/onboarding/v1/profiles/me` 🔐

**설명**  
온보딩 답변을 저장한다. `completed=true`이면 미션 기능 진입이 가능해진다.

**Request**

```json
{
  "livingType": "LIVING_ALONE",
  "wakeUpTime": "08:00",
  "sleepTime": "24:00",
  "preferredMissionTime": "EVENING",
  "routineGoal": "SELF_CARE",
  "activityPreference": "INDOOR",
  "missionIntensity": "LIGHT",
  "answers": {
    "tonePreference": "GENTLE"
  },
  "completed": true
}
```

**Response**

```json
{
  "completed": true,
  "missionAvailable": true,
  "completedAt": "2026-05-15T18:10:00+09:00"
}
```

---

## 6. 미션

미션은 사용자가 직접 목록에서 고르는 구조가 아니라, 서버가 현재 미션 1개를 제안하는 구조다. 오늘 제안된 미션은 stack으로 저장된다.

미션 조회와 상태 변경의 소유권 기준은 `characterId`가 아니라 로그인한 `userId`다. `characterId`는 "어떤 캐릭터가 이 미션을 제안했는지"를 남기는 기록용 값으로 사용한다.

MVP 정책:

```text
한 유저는 하루에 OFFERED/ANSWERING 상태 미션을 동시에 1개만 가진다.
하루 미션 제안은 최대 15개까지 가능하다.
미션 완료는 완료 버튼 클릭 후 완료 질문 1개에 텍스트 답변을 제출해야 처리된다.
완료 답변은 1자 이상 300자 이하로 입력한다.
```

입력 검증:

```text
missionId path variable은 1 이상의 숫자여야 한다.
다음 미션 요청의 characterId는 필수이며 1 이상의 숫자여야 한다.
다음 미션 요청의 lastMissionId는 선택값이며, 전달하는 경우 0 이상의 숫자여야 한다.
처음 미션 요청처럼 직전 미션이 없으면 lastMissionId는 생략하거나 null로 보낼 수 있다.
완료 답변 answer는 공백만으로 구성될 수 없고 300자를 초과할 수 없다.
입력값 형식 오류, JSON body 누락/파싱 오류, path variable 타입 오류는 INVALID_INPUT_VALUE로 응답한다.
```

### 6.1 GET `/api/mission/v1/missions/current` 🔐

**설명**
오늘 기준 로그인한 유저의 현재 미션을 조회한다. `OFFERED` 또는 `ANSWERING` 상태의 미션이 현재 미션이다.

`characterId`는 조회 조건으로 사용하지 않는다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 100,
  "missionDate": "2026-05-20",
  "stackOrder": 2,
  "title": "물 한 컵 마시기",
  "description": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
  "characterMessage": "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "category": "BASIC_ROUTINE",
  "difficulty": "EASY",
  "rewardStarPiece": 10,
  "status": "OFFERED"
}
```

---

### 6.2 GET `/api/mission/v1/missions/today` 🔐

**설명**
오늘 기준 로그인한 유저에게 제안된 미션 스택을 조회한다. 하루 최대 제안 수가 15개이므로 pagination은 제공하지 않는다.

목록은 `stackOrder` 오름차순으로 반환한다. `currentMissionId`는 현재 진행 중인 `OFFERED` 또는 `ANSWERING` 상태 미션의 id이며, 현재 미션이 없으면 `null`이다.

`offeredCount`는 현재 `OFFERED` 상태인 미션 수가 아니라, 오늘 유저에게 제안된 전체 미션 stack 개수다. 따라서 `remainingOfferCount`는 `maxDailyOffers - offeredCount`로 계산한다.

mission REST 응답에는 AI fallback 여부를 노출하지 않는다. fallback 여부는 `ai_mission_generations`, `ai_usage_logs`, event-log의 `AI_FALLBACK_USED`에서 운영 분석용으로 추적한다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionDate": "2026-05-21",
  "maxDailyOffers": 15,
  "offeredCount": 4,
  "completedCount": 2,
  "rejectedCount": 1,
  "remainingOfferCount": 11,
  "currentMissionId": 104,
  "missions": [
    {
      "id": 101,
      "stackOrder": 1,
      "title": "물 한 컵 마시기",
      "category": "BASIC_ROUTINE",
      "difficulty": "EASY",
      "rewardStarPiece": 10,
      "status": "COMPLETED",
      "characterMessage": "잘했어. 작은 시작도 별조각이 됐어.",
      "createdAt": "2026-05-21T09:10:00+09:00",
      "completedAt": "2026-05-21T09:15:00+09:00",
      "rejectedAt": null
    },
    {
      "id": 104,
      "stackOrder": 4,
      "title": "창문 열고 숨 고르기",
      "category": "BASIC_ROUTINE",
      "difficulty": "EASY",
      "rewardStarPiece": 10,
      "status": "OFFERED",
      "characterMessage": "무... 오늘의 작은 별을 찾은 것 같아요.",
      "createdAt": "2026-05-21T11:30:00+09:00",
      "completedAt": null,
      "rejectedAt": null
    }
  ]
}
```

---

### 6.3 POST `/api/mission/v1/missions/today-focus/next` 🔐

**설명**
다음 미션을 생성해 현재 미션으로 제안한다.

진행 중인 미션(`OFFERED`, `ANSWERING`)이 이미 있으면 새 미션을 만들 수 없다. 현재 미션을 거절하거나 완료한 뒤 다시 호출해야 한다.

`characterId`는 제안 캐릭터 기록용이며, 현재 미션 중복 판단은 `userId` 기준으로 수행한다.

**Request**

```json
{
  "characterId": 10,
  "lastMissionId": 101
}
```

`characterId`는 미션 제안 캐릭터를 기록하기 위해 전달한다. `lastMissionId`는 클라이언트가 마지막으로 보고 있던 미션을 함께 보내기 위한 필드다. 첫 미션 요청처럼 직전 미션이 없으면 `lastMissionId`는 생략하거나 `null`로 보낼 수 있다. 진행 중 미션 존재 여부는 서버 상태 기준으로 확인한다.

**Response**

```json
{
  "id": 101,
  "missionDate": "2026-05-20",
  "stackOrder": 3,
  "title": "책상 위 물건 하나 치우기",
  "description": "책상 위에서 물건 하나만 제자리로 옮겨보세요.",
  "characterMessage": "물건 하나만 치워도 공간이 조금 숨을 쉴 수 있어.",
  "category": "SPACE_RESET",
  "difficulty": "EASY",
  "rewardStarPiece": 10,
  "status": "OFFERED"
}
```

---

### 6.4 POST `/api/mission/v1/missions/{missionId}/rejections` 🔐

**설명**
현재 제안된 미션을 거절한다. 거절은 실패가 아니며, 별조각 차감도 없다.

거절 권한 확인은 `userId + missionId` 기준으로 수행한다. `characterId`는 소유권 조건으로 사용하지 않는다.

거절 후 다음 미션은 이 API에서 자동 생성하지 않는다. 클라이언트는 필요하면 `POST /api/mission/v1/missions/today-focus/next`를 다시 호출한다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionId": 101,
  "status": "REJECTED",
  "rejectedAt": "2026-05-20T18:30:00+09:00",
  "characterMessage": "괜찮아요. 다른 별을 찾아볼게요."
}
```

---

### 6.5 POST `/api/mission/v1/missions/{missionId}/completion-sessions` 🔐

**설명**
사용자가 완료 버튼을 눌렀을 때 완료 질문 1개를 시작한다. 이 시점에는 아직 보상을 지급하지 않는다.

`OFFERED` 상태 미션은 `ANSWERING` 상태로 전환된다. 이미 `ANSWERING` 상태이면 기존 질문을 다시 반환하므로, 모바일 중복 클릭이나 네트워크 재시도에도 같은 질문 세션을 유지한다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionId": 101,
  "status": "ANSWERING",
  "question": {
    "id": 501,
    "text": "어떤 물건을 치웠어?",
    "inputType": "TEXT",
    "minLength": 1,
    "maxLength": 300
  }
}
```

---

### 6.6 POST `⚠️ /api/mission/v1/missions/{missionId}/completion-answers` 🔐

**설명**
완료 질문에 답변한다. 답변 저장 후 미션을 `COMPLETED`로 전환한다.

완료 답변은 1자 이상 300자 이하로 입력한다. 답변 전문은 `mission_completion_answers`에 저장하고, event-log에는 답변 전문을 남기지 않는다.

미션 완료 보상은 1회만 지급된다. 응답의 `reward`는 지급된 보상량을, `wallet`은 보상 반영 후 지갑 스냅샷을 의미한다.

**Request**

```json
{
  "answer": "책상 위에 있던 컵을 싱크대에 가져다 놨어."
}
```

**Response**

```json
{
  "missionId": 101,
  "status": "COMPLETED",
  "answer": {
    "text": "책상 위에 있던 컵을 싱크대에 가져다 놨어.",
    "answeredAt": "2026-05-20T18:35:00+09:00"
  },
  "reward": {
    "starPiece": 10,
    "affection": 5
  },
  "wallet": {
    "starPiece": 127
  },
  "characterMessage": "작은 정리도 오늘의 별조각으로 남겨둘게."
}
```

---

### 6.7 내부 gRPC `AiService.GenerateMissionTexts`

**설명**
선택된 미션 템플릿을 캐릭터 말투 기반 문구로 변환한다. 외부 클라이언트가 직접 호출하는 REST API가 아니라, mission 또는 gateway 내부에서 사용하는 AI gRPC API다.

AI는 seed 미션의 제목, 설명, 카테고리, 난이도, 보상을 임의로 바꾸지 않는다. 아래 3개 문구만 생성하거나 fallback 문구를 반환한다.

```text
제안 문구 characterMessage
완료 질문 completionQuestion
완료 후 캐릭터 반응 completionCharacterResponse
```

외부 provider 오류, 응답 구조 오류, 정책 위반 등으로 생성 결과를 사용할 수 없으면 미션 템플릿의 fallback 문구를 사용한다.

`requestId`는 AI 생성 요청의 멱등 기준이다. 같은 `requestId`와 같은 요청 본문이 다시 들어오면 기존 `ai_mission_generations` 결과를 반환하고, 같은 `requestId`가 다른 요청 본문과 함께 들어오면 충돌로 처리한다. mission 모듈은 매 호출마다 랜덤 UUID를 만들지 않고, 같은 미션 문구 생성 시도에 같은 `requestId`를 사용한다.

외부 AI provider를 사용하는 경우 ai 모듈은 `requestId` 멱등 결과를 먼저 확인한 뒤 Redis 기반 rate limit을 확인한다. rate limit 초과 또는 Redis rate limit 저장소 장애가 발생하면 외부 provider를 호출하지 않고 fallback 문구를 반환한다.

**gRPC Request**

```json
{
  "userId": 1,
  "characterId": 10,
  "characterType": "NOVA",
  "missionTemplateId": 12,
  "baseTitle": "물 한 컵 마시기",
  "baseDescription": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
  "category": "BASIC_ROUTINE",
  "difficulty": "EASY",
  "fallbackCharacterMessage": "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "fallbackQuestion": "물 마시고 나서 기분이 조금 달라졌어?",
  "fallbackCompletionResponse": "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
  "onboardingContextJson": "{\"routineGoal\":\"SELF_CARE\"}",
  "recentMissionContextJson": "{\"recentRejected\":[]}",
  "requestId": "MISSION_TEXT:2f3a4b..."
}
```

**gRPC Response**

```json
{
  "aiGenerationId": 55,
  "status": "SUCCESS",
  "characterMessage": "천천히 물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "completionQuestion": "물 마시고 나서 기분이 조금 달라졌어?",
  "completionCharacterResponse": "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
  "fallbackUsed": false,
  "requestId": "MISSION_TEXT:2f3a4b..."
}
```

fallback 응답 예시:

```json
{
  "aiGenerationId": 56,
  "status": "FALLBACK",
  "characterMessage": "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "completionQuestion": "물 마시고 나서 기분이 조금 달라졌어?",
  "completionCharacterResponse": "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
  "fallbackUsed": true,
  "errorType": "INVALID_OUTPUT",
  "requestId": "MISSION_TEXT:2f3a4b..."
}
```

AI 생성 결과는 `ai_mission_generations`, 사용 로그는 `ai_usage_logs`에 저장한다. `ai_mission_generations.request_id`는 생성 결과 재사용 기준이고, `ai_usage_logs.request_id`는 해당 생성 시도의 사용량/지연 추적 기준이다. fallback이 사용되면 event-log에 `AI_FALLBACK_USED`를 남긴다. mission REST 응답에는 `fallbackUsed`를 노출하지 않고, 운영 분석은 저장된 생성 결과와 로그를 기준으로 한다.

---

## 7. 별조각 지갑

### 7.1 GET `/api/wallet/v1/wallets/me` 🔐

**설명**  
현재 별조각 잔액을 조회한다.

> 백엔드 코드 확인: 현재 gateway 응답은 `starPiece`만 반환한다. `updatedAt`은 MVP 화면에서 사용하지 않으므로 선택 필드로 둔다.

**Request**

```json
{}
```

**Response**

```json
{
  "starPiece": 127
}
```

---

### 7.2 GET `/api/wallet/v1/wallets/me/transactions` 🔐

**설명**
내 별조각 획득/사용 거래내역을 최신순으로 조회한다. 별조각 화면에서 잔액과 함께 노출한다.

> 백엔드 코드 확인: `star_piece_transactions` 테이블은 있으나 2026-05-21 기준 gateway/proto에 거래내역 조회 API가 없다. 별조각 화면 실제 연동 전에 추가가 필요하다.

**Request (Query Parameters)**

```json
{
  "cursor": null,
  "size": 20
}
```

**Response**

```json
{
  "items": [
    {
      "id": 1203,
      "amount": 10,
      "balanceAfter": 240,
      "reason": "ATTENDANCE_REWARD",
      "description": "오늘 출석 보상",
      "sourceType": "ATTENDANCE",
      "sourceId": 302,
      "occurredAt": "2026-05-21T10:50:00+09:00"
    },
    {
      "id": 1204,
      "amount": -60,
      "balanceAfter": 180,
      "reason": "ITEM_PURCHASE",
      "description": "말랑 별빛 스킨 구매",
      "sourceType": "ITEM",
      "sourceId": 3,
      "occurredAt": "2026-05-21T11:20:00+09:00"
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasNext": false,
    "size": 20
  }
}
```

---

## 8. 아이템 / 상점

### 8.1 GET `💾 /api/item/v1/items` 🔐

**설명**  
판매 중인 아이템 목록을 조회한다. 가격은 현금이 아니라 별조각 가격이다.
- **모바일 스크롤 UI 대응**: 페이지 누락이나 중복 노출을 방지하기 위해 **커서(Cursor) 기반 페이징**을 적용합니다.
- **탭 필터링**: 화면에 탭(`SKIN`, `CONSUMABLE`) 별로 구분하여 보여주기 위해 쿼리 파라미터 `itemType` 필터링을 제공합니다.
- **캐릭터별 스킨**: `characterTypeId`가 있으면 해당 캐릭터 전용 스킨이며, `null`이면 공용 스킨입니다. 클라이언트는 현재 캐릭터 타입과 비교해 노출 여부를 판단합니다.

> 백엔드 코드 확인: `items` 테이블에는 `effect_type`, `character_type_id`가 있으나, 현재 item proto/gateway 응답에는 `effectType`, `characterTypeId`가 포함되지 않는다. 상점 탭/캐릭터별 스킨 노출을 안정적으로 맞추려면 응답 필드 보강이 필요하다.

**Request (Query Parameters)**

```json
{
  "itemType": "SKIN", 
  "active": true,     
  "cursor": null,    
  "size": 20         
}
```

**Response**

```json
{
  "items": [
    {
      "id": 3,
      "name": "말랑 별빛 스킨",
      "description": "말랑말랑하게 빛나는 스킨입니다.",
      "itemType": "SKIN",
      "characterTypeId": 2,
      "effectType": null,
      "price": 60,
      "imageUrl": "https://cdn.polaris.app/items/skin-soft-star.png",
      "owned": false 
    }
  ],
  "pageInfo": {
    "nextCursor": "eyJpZCI6M30=",
    "hasNext": false,
    "size": 20
  }
}
```

---

### 8.2 GET `/api/item/v1/user-items` 🔐

**설명**  
내가 보유한 아이템과 수량을 조회한다.

- **소모성 아이템**: 보유 개수(`quantity`)가 표현됩니다.
- **장착형 아이템(스킨)**: 장착 여부는 이 API에 포함되지 않습니다. 클라이언트가 보유한 캐릭터 정보(`equipped_skin_id`)와 비교하여 장착 상태를 판단합니다.
- **돌봄 사용**: `itemType=CONSUMABLE`로 조회한 뒤 `effectType`이 돌봄 액션과 맞는 아이템의 `itemId`를 `POST /api/character/v1/characters/{characterId}/care-logs`에 전달합니다.

> 백엔드 코드 확인: 현재 user-items 응답에는 `effectType`은 있으나 `characterTypeId`, `imageUrl`이 없다. 보관함의 캐릭터별 스킨 필터와 이미지 렌더링을 위해 응답 보강이 필요하다.

> **장착 여부 판단 방식 (클라이언트 싱크)**  
> `GET /api/character/v1/characters/me` 응답의 `equippedSkin.itemId` 와  
> 이 API 응답의 `itemId` 를 클라이언트에서 비교합니다.  
> `itemId === equippedSkin.itemId` → 장착된 스킨
> `equippedSkin === null` → 기본 외형 상태
> 스킨 노출 범위는 `characterTypeId`로 판단하며, 현재 캐릭터 타입과 일치하거나 `null`인 공용 스킨만 보여줍니다.

**Request (Query Parameters)**

```json
{
  "itemType": "CONSUMABLE", 
  "cursor": null,
  "size": 20
}
```

**Response**

```json
{
  "items": [
    {
      "userItemId": 40,
      "itemId": 5,
      "name": "별 장난감",
      "itemType": "CONSUMABLE",
      "characterTypeId": null,
      "effectType": "PLAY",
      "quantity": 3,
      "imageUrl": "https://cdn.polaris.app/items/star-toy.png"
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasNext": false,
    "size": 20
  }
}
```

---

### 8.3 POST `⚠️ /api/item/v1/item-purchases` 🔐

**설명**  
별조각으로 아이템을 구매한다.
- **스킨 아이템 구매 정책**: 유저당 최대 **1개**만 구매 가능합니다. 이미 보유 중인 경우 `ITEM_ALREADY_OWNED` (400) 에러가 발생합니다.
- **소모성 아이템 구매 정책**: 수량(`quantity`)을 선택하여 다량 구매가 가능하며, 구매 시 기존 보유량에 누적됩니다.
- **트랜잭션**: 별조각 차감, 거래 내역 기록(`star_piece_transactions`), 보유 아이템(`user_items`) 추가/업데이트는 단일 트랜잭션으로 처리되어 정합성을 유지합니다.
- **재화 부족**: 지갑 잔액이 부족하면 `STAR_PIECE_NOT_ENOUGH` (400) 에러가 발생합니다.

> 백엔드 코드 확인: 현재 gateway가 요청마다 시간 기반 멱등키를 생성하므로 클라이언트 재시도 중복 차감을 완전히 막기 어렵다. 실제 연동 전 `Idempotency-Key` 헤더 또는 body `idempotencyKey` 중 하나로 클라이언트가 안정적인 멱등키를 전달하는 정책 결정이 필요하다.

**Request**

```json
{
  "itemId": 3,
  "quantity": 1              
}
```

**Response**

```json
{
  "purchaseId": 700,
  "itemId": 3,
  "name": "말랑 별빛 스킨",
  "quantity": 1,
  "price": 60,
  "wallet": {
    "starPiece": 67
  },
  "transactionId": 901
}
```

---

## 9. 공유

### 9.1 GET `/api/share/v1/presigned-url` 🔐

**설명**  
프론트엔드에서 렌더링된 캐릭터 이미지를 AWS S3에 직접 업로드하기 위해, 쓰기 권한이 포함된 1회용 임시 URL(Presigned URL)을 발급받는다. 서버 부하 없이 이미지 업로드가 가능하다.

**Request (Query String)**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| extension | string | X | 확장자. 기본값은 "png". "jpg", "jpeg" 등 허용 |

**Response**

```json
{
  "presignedUrl": "https://<bucket>.s3.ap-northeast-2.amazonaws.com/share-cards/UUID.png?X-Amz-Signature=...",
  "imageUrl": "https://cdn.polaris.app/share-cards/UUID.png"
}
```
* **`presignedUrl`**: 프론트엔드가 이미지를 쏠(HTTP PUT) URL. 약 10분 후 만료된다.
* **`imageUrl`**: 업로드 완료 후, 최종적으로 웹상에 노출될 공개 URL. 이후 9.2 API 호출 시 이 값을 사용한다.

---

### 9.2 POST `/api/share/v1/share-cards` 🔐

**설명**  
내 캐릭터 공유 카드를 생성한다. 프론트엔드가 9.1에서 발급받은 `presignedUrl`로 이미지를 업로드한 뒤, 함께 받은 공개 URL(`imageUrl`)을 이 API에 전달한다.

**Request**

```json
{
  "characterId": 10,
  "imageUrl": "https://cdn.polaris.app/share-cards/UUID.png"
}
```

**Response**

```json
{
  "shareCardId": 800,
  "shareId": "sh_abc123",
  "imageUrl": "https://cdn.polaris.app/share-cards/800.png",
  "shareUrl": "https://polaris.app/share/sh_abc123"
}
```

---

### 9.3 POST `⚠️ /api/share/v1/share-events` 🔐

**설명**  
사용자가 공유 버튼을 눌렀다는 이벤트를 저장한다. 실제 외부 SNS 게시 여부는 MVP에서 검증하지 않고, 하루 1회 공유 시도 보상을 지급한다.

**Request**

```json
{
  "shareCardId": 800,
  "platform": "X",
  "shareType": "WEB_SHARE_API",
  "idempotencyKey": "share-reward-20260515-uuid"
}
```

**Response**

```json
{
  "shareEventId": 810,
  "rewardPaid": true,
  "rewardStarPiece": 10,
  "wallet": {
    "starPiece": 77
  }
}
```

---

### 9.4 GET `/api/share/v1/share-events/today` 🔐

**설명**  
로그인한 유저가 오늘 날짜의 공유 보상을 이미 수령했는지 여부를 조회한다. 프론트엔드 버튼 상태 및 배너 표시에 사용된다.

> 백엔드 코드 확인: 현재 share controller에는 해당 endpoint가 없다. 공유 카드 화면의 보상 상태 표시를 실제 데이터로 연결하려면 추가가 필요하다.

**Request**

```json
{}
```

**Response**

```json
{
  "rewardClaimed": true,
  "lastSharedAt": "2026-05-19T09:22:24.400Z"
}
```

---

### 9.5 GET `💾 /api/share/v1/share-links/{shareId}` Public

**설명**  
외부 사용자가 공유 링크로 들어왔을 때 카드 정보를 조회한다.

**Request**

```json
{
  "shareId": "sh_abc123"
}
```

**Response**

```json
{
  "shareId": "sh_abc123",
  "characterName": "노바별",
  "imageUrl": "https://cdn.polaris.app/share-cards/800.png",
  "headline": "오늘도 조금 반짝였음.",
  "signupUrl": "https://polaris.app/signup?shareId=sh_abc123"
}
```

---

## 10. 출석

### 10.1 POST `⚠️ /api/attendance/v1/attendance-records` 🔐

**설명**  
오늘 출석을 기록하고 출석 보상을 지급한다. 하루 1회만 생성되어야 한다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 1,
  "attendanceDate": "2026-05-15",
  "rewardStarPiece": 10,
  "streakCount": 1
}
```

---

### 10.2 GET `/api/attendance/v1/attendance-records` 🔐

**설명**  
달력 UI에 매칭하기 위해 특정 월(Month)의 내 출석 기록 리스트를 조회한다.

**Request**

```json
{
  "year": 2026,
  "month": 5
}
```

**Response**

```json
{
  "records": [
    {
      "id": 1,
      "attendanceDate": "2026-05-01",
      "rewardStarPiece": 10,
      "streakCount": 1
    },
    {
      "id": 2,
      "attendanceDate": "2026-05-15",
      "rewardStarPiece": 10,
      "streakCount": 2
    }
  ]
}
```

---

## 11. 알림

### 11.1 GET `/api/notification/v1/notifications` 🔐

**설명**  
앱 내부 알림 목록을 조회한다.

> 백엔드 코드 확인: 현재 gateway에 notification controller가 없다. 알림 목록, 읽음 처리, 홈 unreadCount를 실제 데이터로 연결하려면 notification REST API 구현이 필요하다.

**Request**

```json
{
  "read": false,
  "cursor": null,
  "size": 20
}
```

**Response**

```json
{
  "items": [
    {
      "id": 600,
      "notificationType": "MISSION",
      "title": "작은 미션 하나가 기다리고 있어요",
      "message": "오늘 별조각 하나가 아직 안 태어났어.",
      "targetType": "MISSION",
      "targetId": 101,
      "read": false,
      "createdAt": "2026-05-15T18:00:00+09:00"
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasNext": false,
    "size": 20
  }
}
```

---

### 11.2 PATCH `/api/notification/v1/notifications/{notificationId}` 🔐

**설명**  
알림을 읽음 처리한다.

**Request**

```json
{
  "read": true
}
```

**Response**

```json
{
  "id": 600,
  "read": true,
  "updatedAt": "2026-05-15T18:45:00+09:00"
}
```
---

### 11.3 POST `/api/notification/v1/subscriptions/` 🔐

**설명**  
FCM 푸쉬 알림을 허용하고 토큰을 저장해 구독을 시작한다.

**Request**

```json
{
  "token": "FCM-token"
}
```

**Response**

```json
{
  "id": 600,
  "read": true,
  "createdAt": "2026-05-15T18:45:00+09:00"
}
```
---

## 12. 주요 상태 / Enum

### 12.1 미션 상태

| 상태 | 의미 |
|---|---|
| `GENERATED` | 생성됐지만 아직 사용자에게 노출되지 않음 |
| `OFFERED` | 현재 사용자에게 제안됨 |
| `ANSWERING` | 완료 클릭 후 질문 답변 중 |
| `COMPLETED` | 답변 완료 후 보상 지급 완료 |
| `REJECTED` | 사용자가 거절 |
| `EXPIRED` | 날짜 변경 등으로 만료 |

### 12.2 캐릭터 상태

| 필드 | 의미 | 화면 라벨 예시 |
|---|---|---|
| `hunger` | 높을수록 든든함 | 든든함 / 출출함 / 배고픔 |
| `energy` | 높을수록 기운 있음 | 말짱함 / 졸림 / 피곤함 |
| `affection` | 높을수록 가까움 | 가까움 / 조용함 / 쓸쓸함 |

### 12.3 아이템

| 필드 | 값 |
|---|---|
| `itemType` | `SKIN`, `CONSUMABLE` |
| `effectType` | `FOOD`, `REST`, `PLAY` |
| `actionType` | `FEED`, `SLEEP`, `PLAY` |

### 12.4 별조각 거래 사유

| reason | 설명 |
|---|---|
| `MISSION_REWARD` | 미션 완료 보상 |
| `ITEM_PURCHASE` | 아이템 구매 |
| `ATTENDANCE_REWARD` | 출석 보상 |
| `SHARE_REWARD` | 공유 시도 보상 |
| `CARE_ACTION` | 별조각 직접 차감형 돌봄 정책 도입 시 사용. MVP 돌봄은 소모품 수량 차감 기준 |

### 12.5 AI 문구 생성 상태

| 상태 | 의미 |
|---|---|
| `SUCCESS` | AI 또는 local generator 문구를 사용 |
| `FALLBACK` | 생성 결과를 사용할 수 없어 미션 템플릿 fallback 문구 사용 |
| `FAILED` | 문구 생성 실패 |

### 12.6 AI 에러 타입

| 타입 | 의미 |
|---|---|
| `TIMEOUT` | provider 응답 지연 |
| `RATE_LIMIT` | provider 요청 제한 |
| `RATE_LIMIT_UNAVAILABLE` | rate limit 저장소 장애로 provider 호출 차단 |
| `INVALID_OUTPUT` | 응답 구조 오류 |
| `POLICY_VIOLATION` | 서비스 문구 정책 위반 |
| `PROVIDER_ERROR` | provider 내부 오류 |
| `UNKNOWN` | 분류되지 않은 오류 |

---

## 13. 주요 에러 코드

| 코드 | 상황 |
|---|---|
| `UNAUTHORIZED` | 인증 실패 |
| `FORBIDDEN` | 권한 없음 |
| `USER_NOT_FOUND` | 사용자 없음 |
| `CHARACTER_NOT_FOUND` | 캐릭터 없음 |
| `CHARACTER_NAME_INVALID` | 캐릭터 이름 형식 오류 |
| `ONBOARDING_REQUIRED` | 온보딩 미완료로 미션 사용 불가 |
| `MISSION_NOT_FOUND` | 미션 없음 |
| `MISSION_TEMPLATE_NOT_FOUND` | 사용할 수 있는 미션 템플릿 없음 |
| `MISSION_INVALID_STATUS` | 상태 전이 불가 |
| `MISSION_ACTIVE_ALREADY_EXISTS` | 이미 진행 중인 미션 존재 |
| `MISSION_DAILY_LIMIT_EXCEEDED` | 일일 미션 제안 제한 초과 |
| `MISSION_ALREADY_COMPLETED` | 이미 완료된 미션 |
| `MISSION_ANSWER_INVALID` | 완료 답변 길이 오류 |
| `MISSION_SERVICE_UNAVAILABLE` | 미션 서비스 일시 장애 |
| `STAR_PIECE_NOT_ENOUGH` | 별조각 부족 |
| `DUPLICATED_IDEMPOTENCY_KEY` | 중복 요청 |
| `ITEM_NOT_FOUND` | 아이템 없음 |
| `ITEM_ALREADY_OWNED` | 이미 보유한 장착형 아이템 |
| `ITEM_NOT_OWNED` | 보유하지 않은 아이템 |
| `ITEM_QUANTITY_NOT_ENOUGH` | 소모품 수량 부족 |
| `ATTENDANCE_ALREADY_CHECKED` | 오늘 출석 완료 |
| `SHARE_REWARD_ALREADY_PAID` | 오늘 공유 보상 지급 완료 |
| `AI_INVALID_REQUEST` | AI 문구 생성 요청 값 오류 |
| `AI_FALLBACK_INVALID` | fallback 문구 정책 검증 실패 |
| `AI_GENERATION_FAILED` | AI 문구 생성 실패 |
| `AI_DUPLICATED_REQUEST` | 이미 처리된 AI 요청 ID |

---



