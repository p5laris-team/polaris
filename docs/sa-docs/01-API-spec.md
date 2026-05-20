# Polaris REST API 명세서

> 기준일: 2026-05-15  
> 기준 문서: Polaris v0.7 PRD, 최신 ERD, 기존 API 초안

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

---

## 1. API 전체 요약

| Method | Endpoint                                                        | 설명            | Request | Response | 인증 |
|--------|-----------------------------------------------------------------|---------------|---|---|---|
| GET    | `/api/auth/v1/google/authorization-url`                         | Google OAuth2 시작 URL 조회 | query | OAuth URL | Public |
| POST   | `/api/auth/v1/google/sessions`                                  | Google OAuth2 로그인 세션 생성 | body | token + user | Public |
| POST   | `⚠️ /api/auth/v1/token-refreshes`                               | 토큰 재발급        | body | token | Public |
| DELETE | `/api/auth/v1/sessions/current`                                 | 로그아웃          | none | logout result | 🔐 |
| GET    | `/api/user/v1/users/me`                                         | 내 정보 조회       | none | user | 🔐 |
| GET    | `/api/home/v1/home`                                             | 홈 화면 통합 조회    | none | home data | 🔐 |
| GET    | `💾 /api/character/v1/character-types`                          | 캐릭터 종류 조회     | query | character types | 🔐 |
| GET    | `💾 /api/character/v1/character-types/{characterTypeId}/assets` | 캐릭터 에셋 조회     | path | assets | 🔐 |
| POST   | `/api/character/v1/characters`                                  | 내 캐릭터 생성      | body | character | 🔐 |
| GET    | `/api/character/v1/characters/me`                               | 내 활성 캐릭터 조회   | none | character | 🔐 |
| PATCH  | `/api/character/v1/characters/{characterId}`                    | 캐릭터 이름 수정     | path + body | character | 🔐 |
| GET    | `/api/character/v1/characters/{characterId}/status`             | 캐릭터 상태 조회     | path | status | 🔐 |
| POST   | `⚠️ /api/character/v1/characters/{characterId}/care-logs`       | 돌봄 액션 수행      | path + body | care result | 🔐 |
| PUT    | `⚠️ /api/character/v1/characters/{characterId}/equipped-skin`   | 캐릭터 스킨 장착     | path + body | equipped skin | 🔐 |
| GET    | `💾 /api/onboarding/v1/questions`                               | 온보딩 질문 목록 조회  | none | questions | 🔐 |
| GET    | `/api/onboarding/v1/profiles/me`                                | 내 온보딩 프로필 조회  | none | profile | 🔐 |
| PUT    | `/api/onboarding/v1/profiles/me`                                | 내 온보딩 프로필 저장/완료 | body | profile | 🔐 |
| GET    | `/api/mission/v1/missions/current`                              | 현재 제안 미션 조회   | query | mission | 🔐 |
| POST   | `/api/mission/v1/missions/today-focus/next`                     | 다음 미션 요청      | body | mission | 🔐 |
| POST   | `/api/mission/v1/missions/{missionId}/rejections`               | 미션 거절 기록 생성   | path + body | rejection | 🔐 |
| POST   | `/api/mission/v1/missions/{missionId}/completion-sessions`      | 완료 질문 세션 시작   | path + body | question | 🔐 |
| POST   | `⚠️ /api/mission/v1/missions/{missionId}/completion-answers`    | 완료 답변 제출 및 보상 지급 | path + body | completion result | 🔐 |
| GET    | `/api/wallet/v1/wallets/me`                                     | 별조각 잔액 조회     | none | wallet | 🔐 |
| GET    | `💾 /api/item/v1/items`                                         | 상점 아이템 목록 조회  | query cursor | items | 🔐 |
| GET    | `/api/item/v1/user-items`                                       | 내 보유 아이템 조회   | query cursor | user items | 🔐 |
| POST   | `⚠️ /api/item/v1/item-purchases`                                | 아이템 구매        | body | purchase result | 🔐 |
| GET    | `/api/share/v1/presigned-url`                                   | 프론트엔드 R2 직접 업로드용 임시 URL 발급 | query | presigned url | 🔐 |
| POST   | `/api/share/v1/share-cards`                                     | 공유 카드 생성 (멘트 포함) | body | share card | 🔐 |
| POST   | `⚠️ /api/share/v1/share-events`                                 | 공유 시도 이벤트 생성 및 보상 처리 | body | share event | 🔐 |
| GET    | `/api/share/v1/share-events/today`                              | 오늘 공유 보상 여부 조회 | none | share status | 🔐 |
| GET    | `💾 /api/share/v1/share-links/{shareId}`                        | 공개 공유 링크 정보 조회 (클릭 로그 내재화) | path | shared card | Public |
| POST   | `⚠️ /api/attendance/v1/attendance-records`                      | 오늘 출석 기록 생성 및 보상 지급 | none | attendance | 🔐 |
| GET    | `/api/attendance/v1/attendance-records`                         | 출석 기록 조회      | query year, month | attendance list | 🔐 |
| GET    | `/api/notification/v1/notifications`                            | 알림 목록 조회      | query cursor | notifications | 🔐 |
| PATCH  | `/api/notification/v1/notifications/{notificationId}`           | 알림 읽음 처리      | path + body | notification | 🔐 |

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
밥 주기, 재우기, 놀아주기 같은 돌봄 액션을 수행한다. 별조각 차감 또는 소모품 수량 차감이 발생할 수 있다.

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
캐릭터에 스킨을 장착한다. 스킨은 한 번에 하나만 적용한다.

**Request**

```json
{
  "itemId": 3
}
```

**Response**

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

### 6.1 GET `/api/mission/v1/missions/current` 🔐

**설명**  
오늘 기준 현재 제안 중인 미션을 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 100,
  "missionDate": "2026-05-15",
  "stackOrder": 2,
  "title": "물 한 컵 마시기",
  "description": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
  "characterMessage": "물 한 컵 마셔볼래? 나도 빛 좀 마셔볼게.",
  "category": "BASIC_ROUTINE",
  "difficulty": "EASY",
  "rewardStarPiece": 5,
  "status": "OFFERED"
}
```

---

### 6.3 POST `⚠️ /api/mission/v1/missions/today-focus/next` 🔐

**설명**  
현재 미션을 처리(완료/거절)한 후, 다음 미션을 활성화해달라고 요청한다.

**Request**

```json
{
  "lastMissionId": 101
}
```

**Response**

```json
{
  "id": 101,
  "missionDate": "2026-05-15",
  "stackOrder": 3,
  "title": "책상 위 물건 하나 치우기",
  "description": "책상 위에서 물건 하나만 제자리로 옮겨보세요.",
  "characterMessage": "작은 정리도 별조각이 될 수 있어.",
  "category": "SPACE_RESET",
  "difficulty": "EASY",
  "rewardStarPiece": 7,
  "status": "OFFERED"
}
```

---

### 6.4 POST `/api/mission/v1/missions/{missionId}/rejections` 🔐

**설명**  
현재 제안된 미션을 거절한다. 거절은 실패가 아니며, 별조각 차감도 없다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionId": 101,
  "status": "REJECTED",
  "rejectedAt": "2026-05-15T18:30:00+09:00",
  "characterMessage": "괜찮아. 그럼 다른 별 찾아볼게.",
  "nextMission": {
    "id": 102,
    "title": "책상 위 물건 치우기",
    "stackOrder": 2
  }
}
```

---

### 6.5 POST `/api/mission/v1/missions/{missionId}/completion-sessions` 🔐

**설명**  
사용자가 완료 버튼을 눌렀을 때 완료 질문 1개를 시작한다. 이 시점에는 아직 보상을 지급하지 않는다.

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
완료 질문에 답변한다. 답변 저장 후 미션을 `COMPLETED`로 전환하고 별조각 보상을 1회 지급한다.

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
    "answeredAt": "2026-05-15T18:35:00+09:00"
  },
  "reward": {
    "starPiece": 7,
    "affection": 5
  },
  "wallet": {
    "starPiece": 127
  },
  "characterMessage": "작은 일이지만, 나한텐 꽤 컸어."
}
```

---

## 7. 별조각 지갑

### 7.1 GET `/api/wallet/v1/wallets/me` 🔐

**설명**  
현재 별조각 잔액을 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "starPiece": 127,
  "updatedAt": "2026-05-15T18:35:00+09:00"
}
```

---

## 8. 아이템 / 상점

### 8.1 GET `💾 /api/item/v1/items` 🔐

**설명**  
판매 중인 아이템 목록을 조회한다. 가격은 현금이 아니라 별조각 가격이다.  
- **모바일 스크롤 UI 대응**: 페이지 누락이나 중복 노출을 방지하기 위해 **커서(Cursor) 기반 페이징**을 적용합니다.
- **탭 필터링**: 화면에 탭(`SKIN`, `CONSUMABLE`) 별로 구분하여 보여주기 위해 쿼리 파라미터 `itemType` 필터링을 제공합니다.

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
      "itemType": "SKIN",
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
내가 보유한 아이템과 수량, 장착 여부를 조회한다.  
- **소모성 아이템**: 보유 개수(`quantity`)가 표현됩니다.
- **장착형 아이템(스킨 등)**: 대표 캐릭터의 장착 여부(`equipped`)가 표현됩니다.

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
      "itemId": 21,
      "name": "별사탕밥",
      "itemType": "CONSUMABLE",
      "effectType": "FOOD",
      "quantity": 2,     
      "equipped": false   
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
프론트엔드에서 렌더링된 캐릭터 이미지를 외부 스토리지(Cloudflare R2/AWS S3)에 직접 업로드하기 위해, 쓰기 권한이 포함된 1회용 임시 URL(Presigned URL)을 발급받는다. 서버 부하 없이 이미지 업로드가 가능하다.

**Request (Query String)**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| extension | string | X | 확장자. 기본값은 "png". "jpg", "jpeg" 등 허용 |

**Response**

```json
{
  "presignedUrl": "https://<bucket>.r2.cloudflarestorage.com/share-cards/UUID.png?X-Amz-Signature=...",
  "imageUrl": "https://cdn.polaris.app/share-cards/UUID.png"
}
```
* **`presignedUrl`**: 프론트엔드가 이미지를 쏠(HTTP PUT) URL. 약 10분 후 만료된다.
* **`imageUrl`**: 업로드 완료 후, 최종적으로 웹상에 노출될 공개 URL. 이후 9.2 API 호출 시 이 값을 사용한다.

---

### 9.2 POST `/api/share/v1/share-cards` 🔐

**설명**  
내 캐릭터 공유 카드를 생성한다. 한 줄 다짐/메시지(`headline`)를 입력받아 카드에 기록한다.

**Request**

```json
{
  "characterId": 10,
  "headline": "오늘도 조금 반짝였음."
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
| `ATTENDANCE` | 출석 보상 |
| `SHARE_REWARD` | 공유 시도 보상 |
| `CARE_ACTION` | 돌봄 액션 비용 |

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
| `MISSION_INVALID_STATUS` | 상태 전이 불가 |
| `MISSION_DAILY_LIMIT_EXCEEDED` | 일일 미션 제안 제한 초과 |
| `MISSION_ALREADY_COMPLETED` | 이미 완료된 미션 |
| `ANSWER_INVALID` | 완료 답변 길이/금칙어 오류 |
| `STAR_PIECE_NOT_ENOUGH` | 별조각 부족 |
| `DUPLICATED_IDEMPOTENCY_KEY` | 중복 요청 |
| `ITEM_NOT_FOUND` | 아이템 없음 |
| `ITEM_ALREADY_OWNED` | 이미 보유한 장착형 아이템 |
| `ITEM_NOT_OWNED` | 보유하지 않은 아이템 |
| `ITEM_QUANTITY_NOT_ENOUGH` | 소모품 수량 부족 |
| `ATTENDANCE_ALREADY_CHECKED` | 오늘 출석 완료 |
| `SHARE_REWARD_ALREADY_PAID` | 오늘 공유 보상 지급 완료 |
| `AI_PROVIDER_TIMEOUT` | AI 응답 지연/타임아웃 |
| `AI_INVALID_OUTPUT` | AI 응답 구조 검증 실패 |

---



