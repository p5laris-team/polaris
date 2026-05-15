# 06. REST API 명세서

## 문서 정보

| 항목 | 내용 |
|------|------|
| 문서명 | Polaris MVP REST API 명세서 |
| 작성일 | 2026-05-14 |
| 버전 | v1.0 |
| 목적 | REST API 엔드포인트 및 요청/응답 정의 |
| 대상 독자 | 백엔드 개발자, 프론트엔드 개발자 |

---

## 📋 목차

1. [API 개요](#api-개요)
2. [인증 API](#인증-api)
3. [사용자 API](#사용자-api)
4. [캐릭터 API](#캐릭터-api)
5. [미션 API](#미션-api)
6. [아이템 API](#아이템-api)
7. [별조각 API](#별조각-api)
8. [알림 API](#알림-api)
9. [공유 API](#공유-api)
10. [업적 API](#업적-api)

---

## API 개요

### Base URL
```
Production: https://api.polaris.app/v1
Staging: https://api-staging.polaris.app/v1
Development: http://localhost:8080/v1
```

### 공통 헤더
```
Authorization: Bearer {access_token}
Content-Type: application/json
Accept: application/json
X-Client-Version: 1.0.0
X-Platform: WEB | IOS | ANDROID
```

### 공통 응답 형식
```json
{
  "success": true,
  "data": {},
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

### 에러 응답 형식
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지",
    "details": {}
  },
  "timestamp": "2026-05-14T10:00:00Z"
}
```

### 표기 규칙
- 🔐 : 인증 필요
- 💾 : 캐싱 대상
- ⚠️ : 여러 모듈 쓰기 발생

---

## 인증 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| POST | `/auth/google` | Google OAuth2 로그인 | - | UC-001 |
| POST | `/auth/refresh` | Access Token 갱신 | - | UC-001 |
| POST | `/auth/logout` | 로그아웃 | 🔐 | - |

---

### POST /auth/google

Google OAuth2 인증 코드로 로그인

**Request Body:**
```json
{
  "authorizationCode": "4/0AX4XfWh...",
  "redirectUri": "https://polaris.app/auth/callback"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600,
    "tokenType": "Bearer",
    "user": {
      "userId": 123,
      "email": "user@gmail.com",
      "displayName": "John Doe",
      "profileImageUrl": "https://lh3.googleusercontent.com/...",
      "isNewUser": true
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `401 INVALID_AUTHORIZATION_CODE`: 유효하지 않은 인증 코드
- `503 GOOGLE_API_UNAVAILABLE`: Google API 장애

---

### POST /auth/refresh

Refresh Token으로 Access Token 갱신

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600,
    "tokenType": "Bearer"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /auth/logout

로그아웃 (Refresh Token 무효화)

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "message": "로그아웃되었습니다"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

## 사용자 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| GET | `/users/me` | 내 정보 조회 | 🔐 | - |
| GET | `/users/me/profile` | 프로필 조회 | 🔐 💾 | - |
| PUT | `/users/me/profile` | 프로필 수정 | 🔐 | UC-004 |
| POST | `/users/me/onboarding` | 온보딩 설문 제출 | 🔐 | UC-004 |

---

### GET /users/me

현재 로그인한 사용자 정보 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": 123,
    "email": "user@gmail.com",
    "displayName": "John Doe",
    "profileImageUrl": "https://lh3.googleusercontent.com/...",
    "starPieces": 150,
    "createdAt": "2026-05-01T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /users/me/profile

사용자 프로필 및 온보딩 설문 답변 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": 123,
    "surveyCompleted": true,
    "surveyCompletedAt": "2026-05-01T10:30:00Z",
    "preferences": {
      "livingEnvironment": "LIVING_ALONE",
      "wakeTime": "BETWEEN_7_9",
      "sleepTime": "BETWEEN_23_1",
      "missionIntensity": "LIGHT",
      "burdenType": "OUTDOOR",
      "preferredGoal": "START_DAY",
      "notificationPreference": "MORNING",
      "weatherPreference": "REGION_ONLY",
      "speechPreference": "GENTLE",
      "region": "서울특별시"
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /users/me/onboarding

온보딩 설문 답변 제출

**Request Body:**
```json
{
  "answers": {
    "livingEnvironment": "LIVING_ALONE",
    "wakeTime": "BETWEEN_7_9",
    "sleepTime": "BETWEEN_23_1",
    "missionIntensity": "LIGHT",
    "burdenType": "OUTDOOR",
    "preferredGoal": "START_DAY",
    "notificationPreference": "MORNING",
    "weatherPreference": "REGION_ONLY",
    "speechPreference": "GENTLE",
    "region": "서울특별시"
  }
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "surveyCompleted": true,
    "surveyCompletedAt": "2026-05-14T10:00:00Z",
    "message": "온보딩이 완료되었습니다"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---


## 캐릭터 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| GET | `/characters/types` | 캐릭터 종류 목록 조회 | - 💾 | UC-002 |
| POST | `/characters` | 캐릭터 생성 | 🔐 | UC-003 |
| GET | `/characters/me` | 내 캐릭터 조회 | 🔐 💾 | UC-010 |
| GET | `/characters/me/image` | 현재 캐릭터 이미지 조회 | 🔐 💾 | UC-010 |
| POST | `/characters/me/care/feed` | 밥 주기 | 🔐 ⚠️ | UC-011 |
| POST | `/characters/me/care/sleep` | 재우기 | 🔐 | UC-012 |
| POST | `/characters/me/care/play` | 놀아주기 | 🔐 ⚠️ | UC-013 |
| GET | `/characters/me/care-logs` | 돌봄 이력 조회 | 🔐 | - |

---

### GET /characters/types

선택 가능한 캐릭터 종류 목록 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "characterTypes": [
      {
        "id": 1,
        "code": "NOVA",
        "name": "노바",
        "summary": "자기가 별이었다는 걸 까먹은 별알",
        "personality": "다정함, 조심스러움, 기억이 듬성듬성함",
        "speechStyle": "짧고 느림, 문장 끝이 작게 흐려짐",
        "introMessage": "노바는 자기가 한때 길을 비추던 별이었다는 걸 까먹은 별알이에요...",
        "sampleLine": "나 굴러가도 잡아줄 거야?",
        "thumbnailUrl": "https://cdn.polaris.com/characters/nova_thumb.png"
      },
      {
        "id": 2,
        "code": "MUMU",
        "name": "무무",
        "summary": "무...밖에 못 하지만 다 알고 있는 작은 별나무",
        "thumbnailUrl": "https://cdn.polaris.com/characters/mumu_thumb.png"
      },
      {
        "id": 3,
        "code": "JJORY",
        "name": "쪼리",
        "summary": "현관까지 가면 세계여행이라고 믿는 별쥐",
        "thumbnailUrl": "https://cdn.polaris.com/characters/jjory_thumb.png"
      }
    ]
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /characters

캐릭터 생성 (온보딩 시 1회)

**Request Body:**
```json
{
  "characterTypeId": 1,
  "nickname": "내 노바"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "characterId": 456,
    "characterTypeId": 1,
    "characterType": "NOVA",
    "characterName": "노바",
    "nickname": "내 노바",
    "level": 1,
    "states": {
      "fullness": 100,
      "energy": 100,
      "affection": 50
    },
    "createdAt": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 ALREADY_HAS_CHARACTER`: 이미 캐릭터를 보유함
- `400 INVALID_NICKNAME`: 닉네임 유효성 검증 실패

---

### GET /characters/me

내 캐릭터 상태 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "characterId": 456,
    "characterTypeId": 1,
    "characterType": "NOVA",
    "characterName": "노바",
    "nickname": "내 노바",
    "level": 1,
    "states": {
      "fullness": {
        "value": 82,
        "max": 100,
        "grade": "GOOD",
        "label": "든든함"
      },
      "energy": {
        "value": 45,
        "max": 100,
        "grade": "NORMAL",
        "label": "졸림"
      },
      "affection": {
        "value": 28,
        "max": 100,
        "grade": "BAD",
        "label": "쓸쓸함"
      }
    },
    "equippedItems": {
      "skinId": null,
      "backgroundId": null
    },
    "lastStateUpdatedAt": "2026-05-14T08:00:00Z",
    "createdAt": "2026-05-01T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /characters/me/image

현재 캐릭터 이미지 조회 (상태에 따라 동적 변경)

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "imageUrl": "https://cdn.polaris.com/characters/nova_affection_bad.png",
    "assetType": "STATE",
    "assetKey": "AFFECTION_BAD",
    "width": 512,
    "height": 512,
    "reason": "affection이 BAD 상태입니다"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /characters/me/care/feed

밥 주기 (fullness +30)

**Request Body:**
```json
{
  "paymentType": "STAR_PIECE",
  "itemId": null
}
```

또는

```json
{
  "paymentType": "ITEM",
  "itemId": 123
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "characterId": 456,
    "careType": "FEED",
    "stateType": "fullness",
    "stateBefore": 70,
    "stateAfter": 100,
    "cost": 3,
    "paymentType": "STAR_PIECE",
    "characterResponse": "먹는 중... 빛도 맛이 있구나.",
    "newBalance": {
      "starPieces": 147,
      "fullness": 100
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 INSUFFICIENT_STAR_PIECES`: 별조각 부족
- `400 ITEM_NOT_FOUND`: 아이템 없음
- `400 STATE_ALREADY_MAX`: 이미 최대치

---

### POST /characters/me/care/sleep

재우기 (energy +30)

**Request Body:**
```json
{
  "paymentType": "FREE"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "characterId": 456,
    "careType": "SLEEP",
    "stateType": "energy",
    "stateBefore": 45,
    "stateAfter": 75,
    "cost": 0,
    "paymentType": "FREE",
    "characterResponse": "나 먼저 잘게. 꿈에서 별 좀 주워올게.",
    "cooldownUntil": "2026-05-14T18:00:00Z",
    "newBalance": {
      "energy": 75
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 COOLDOWN_ACTIVE`: 쿨다운 중 (남은 시간 포함)

---

### POST /characters/me/care/play

놀아주기 (affection +25)

**Request Body:**
```json
{
  "paymentType": "STAR_PIECE",
  "itemId": null
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "characterId": 456,
    "careType": "PLAY",
    "stateType": "affection",
    "stateBefore": 28,
    "stateAfter": 53,
    "cost": 2,
    "paymentType": "STAR_PIECE",
    "characterResponse": "나 굴러가도 잡아줄 거야?",
    "newBalance": {
      "starPieces": 148,
      "affection": 53
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /characters/me/care-logs

돌봄 이력 조회 (Cursor 기반 페이징)

**Query Parameters:**
- `cursor` (optional): 다음 페이지 커서
- `limit` (optional, default: 20): 페이지 크기

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "careLogs": [
      {
        "id": 1001,
        "careType": "FEED",
        "paymentType": "STAR_PIECE",
        "cost": 3,
        "stateType": "fullness",
        "stateBefore": 70,
        "stateAfter": 100,
        "createdAt": "2026-05-14T10:00:00Z"
      },
      {
        "id": 1000,
        "careType": "PLAY",
        "paymentType": "ITEM",
        "itemId": 123,
        "cost": 0,
        "stateType": "affection",
        "stateBefore": 28,
        "stateAfter": 53,
        "createdAt": "2026-05-14T09:00:00Z"
      }
    ],
    "pagination": {
      "nextCursor": "eyJpZCI6MTAwMH0=",
      "hasMore": true
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---


## 미션 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| GET | `/missions/current` | 현재 미션 조회 | 🔐 | UC-006 |
| POST | `/missions/{missionId}/reject` | 미션 거절 | 🔐 | UC-007 |
| POST | `/missions/{missionId}/complete` | 미션 완료 시작 | 🔐 | UC-008 |
| POST | `/missions/{missionId}/answer` | 완료 질문 답변 | 🔐 ⚠️ | UC-009 |
| GET | `/missions/history` | 미션 이력 조회 | 🔐 | - |
| GET | `/missions/stats` | 미션 통계 조회 | 🔐 💾 | - |

---

### GET /missions/current

현재 제안된 미션 조회

**Response (200 OK - 미션 있음):**
```json
{
  "success": true,
  "data": {
    "mission": {
      "missionId": 12345,
      "title": "물 한 컵 마시기",
      "category": "BASIC_ROUTINE",
      "characterMessage": "물 한 컵 마셔볼래? 나도 빛 좀 마셔볼게.",
      "estimatedMinutes": 1,
      "difficulty": "VERY_LIGHT",
      "reward": {
        "starPieces": 10
      },
      "status": "OFFERED",
      "offeredAt": "2026-05-14T10:00:00Z"
    },
    "todayStats": {
      "offeredCount": 3,
      "completedCount": 2,
      "rejectedCount": 1,
      "remainingOffers": 12
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Response (200 OK - 미션 없음):**
```json
{
  "success": true,
  "data": {
    "mission": null,
    "reason": "DAILY_LIMIT_REACHED",
    "message": "오늘은 충분히 별을 모았어요. 내일 또 만나요!",
    "todayStats": {
      "offeredCount": 15,
      "completedCount": 5,
      "rejectedCount": 10,
      "remainingOffers": 0
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /missions/{missionId}/reject

미션 거절

**Request Body:**
```json
{
  "reason": "TOO_LAZY",
  "comment": "지금은 너무 피곤해요"
}
```

**Rejection Reasons:**
- `TOO_LAZY`: 너무 귀찮아요
- `OUTDOOR_BURDEN`: 지금은 밖에 나가기 싫어요
- `TOO_HARD`: 너무 어려워요
- `ALREADY_DONE`: 이미 했어요
- `NOT_INTERESTED`: 마음에 안 들어요
- `OTHER`: 다른 이유

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "missionId": 12345,
    "status": "REJECTED",
    "characterResponse": "괜찮아. 그럼 다른 별 찾아볼게.",
    "rejectedAt": "2026-05-14T10:00:00Z",
    "nextMission": {
      "missionId": 12346,
      "title": "창문 3분 열기",
      "characterMessage": "창문을 조금 열면... 오늘 공기도 별이 될 수 있어."
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 ALREADY_REJECTED`: 이미 거절한 미션
- `400 DAILY_LIMIT_REACHED`: 오늘 제안 횟수 초과

---

### POST /missions/{missionId}/complete

미션 완료 시작 (질문 받기)

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "missionId": 12345,
    "status": "ANSWERING",
    "completionQuestion": "방금 한 일에서 제일 기억나는 건 뭐였어?",
    "answerConstraints": {
      "minLength": 1,
      "maxLength": 300,
      "inputType": "TEXT"
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 ALREADY_COMPLETED`: 이미 완료한 미션
- `400 MISSION_REJECTED`: 거절한 미션은 완료 불가

---

### POST /missions/{missionId}/answer

완료 질문 답변 제출

**Request Body:**
```json
{
  "answer": "물을 마시니까 목이 시원했어요"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "missionId": 12345,
    "status": "COMPLETED",
    "characterResponse": "그걸 기억해둘게. 오늘 별조각이 됐어.",
    "rewards": {
      "starPieces": 10,
      "affection": 5
    },
    "newBalance": {
      "starPieces": 160,
      "affection": 33
    },
    "completedAt": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 ANSWER_TOO_SHORT`: 답변이 1자 미만
- `400 ANSWER_TOO_LONG`: 답변이 300자 초과
- `400 PROFANITY_DETECTED`: 부적절한 표현 포함

---

### GET /missions/history

미션 이력 조회 (Cursor 기반 페이징)

**Query Parameters:**
- `status` (optional): COMPLETED | REJECTED | EXPIRED
- `cursor` (optional): 다음 페이지 커서
- `limit` (optional, default: 20): 페이지 크기

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "missions": [
      {
        "missionId": 12345,
        "title": "물 한 컵 마시기",
        "category": "BASIC_ROUTINE",
        "status": "COMPLETED",
        "characterMessage": "물 한 컵 마셔볼래?",
        "completionAnswer": "물을 마시니까 목이 시원했어요",
        "reward": 10,
        "offeredAt": "2026-05-14T09:00:00Z",
        "completedAt": "2026-05-14T09:05:00Z"
      },
      {
        "missionId": 12344,
        "title": "산책하기",
        "category": "OUTDOOR_LIGHT",
        "status": "REJECTED",
        "rejectionReason": "OUTDOOR_BURDEN",
        "offeredAt": "2026-05-14T08:00:00Z",
        "rejectedAt": "2026-05-14T08:01:00Z"
      }
    ],
    "pagination": {
      "nextCursor": "eyJpZCI6MTIzNDR9",
      "hasMore": true
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /missions/stats

미션 통계 조회

**Query Parameters:**
- `period` (optional): TODAY | WEEK | MONTH | ALL (default: TODAY)

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "period": "TODAY",
    "stats": {
      "totalOffered": 3,
      "totalCompleted": 2,
      "totalRejected": 1,
      "completionRate": 66.67,
      "totalStarPiecesEarned": 20,
      "categoryBreakdown": [
        {
          "category": "BASIC_ROUTINE",
          "count": 2,
          "completionRate": 100
        },
        {
          "category": "OUTDOOR_LIGHT",
          "count": 1,
          "completionRate": 0
        }
      ]
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---


## 아이템 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| GET | `/items` | 아이템 목록 조회 | 🔐 💾 | UC-015 |
| POST | `/items/{itemId}/purchase` | 아이템 구매 | 🔐 ⚠️ | UC-016, UC-017 |
| GET | `/items/inventory` | 보유 아이템 조회 | 🔐 | UC-018 |
| POST | `/items/{itemId}/equip` | 아이템 장착 | 🔐 | UC-018 |
| POST | `/items/{itemId}/use` | 소모품 사용 | 🔐 ⚠️ | UC-019 |
| GET | `/items/purchase-history` | 구매 이력 조회 | 🔐 | - |

---

### GET /items

상점 아이템 목록 조회

**Query Parameters:**
- `type` (optional): SKIN | BACKGROUND | CONSUMABLE_FOOD | CONSUMABLE_TOY | CONSUMABLE_REST
- `characterTypeId` (optional): 특정 캐릭터 전용 아이템 필터

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "itemId": 101,
        "name": "노바 봄 스킨",
        "type": "SKIN",
        "price": 50,
        "description": "봄날의 노바 스킨",
        "imageUrl": "https://cdn.polaris.com/items/nova_spring_skin.png",
        "characterTypeId": 1,
        "characterTypeName": "노바",
        "isPurchased": false,
        "canPurchase": true
      },
      {
        "itemId": 201,
        "name": "별빛 배경",
        "type": "BACKGROUND",
        "price": 80,
        "description": "반짝이는 별빛 배경",
        "imageUrl": "https://cdn.polaris.com/items/starlight_bg.png",
        "characterTypeId": null,
        "isPurchased": true,
        "canPurchase": false
      },
      {
        "itemId": 301,
        "name": "프리미엄 간식",
        "type": "CONSUMABLE_FOOD",
        "price": 5,
        "description": "fullness +50 (일반 밥 주기보다 효과 좋음)",
        "imageUrl": "https://cdn.polaris.com/items/premium_food.png",
        "characterTypeId": null,
        "canPurchase": true
      }
    ],
    "categories": [
      {
        "type": "SKIN",
        "label": "스킨",
        "count": 12
      },
      {
        "type": "BACKGROUND",
        "label": "배경",
        "count": 8
      },
      {
        "type": "CONSUMABLE",
        "label": "소모품",
        "count": 6
      }
    ]
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /items/{itemId}/purchase

아이템 구매

**Request Body:**
```json
{
  "quantity": 1
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "itemId": 101,
    "itemName": "노바 봄 스킨",
    "itemType": "SKIN",
    "quantity": 1,
    "totalPrice": 50,
    "newBalance": {
      "starPieces": 100
    },
    "purchasedAt": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 INSUFFICIENT_STAR_PIECES`: 별조각 부족
- `400 ALREADY_PURCHASED`: 이미 구매한 아이템 (스킨/배경)
- `400 INVALID_QUANTITY`: 수량이 1~10 범위 밖

---

### GET /items/inventory

보유 아이템 조회

**Query Parameters:**
- `type` (optional): SKIN | BACKGROUND | CONSUMABLE

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "userItemId": 1001,
        "itemId": 101,
        "itemName": "노바 봄 스킨",
        "itemType": "SKIN",
        "imageUrl": "https://cdn.polaris.com/items/nova_spring_skin.png",
        "quantity": 1,
        "isEquipped": true,
        "purchasedAt": "2026-05-10T10:00:00Z",
        "equippedAt": "2026-05-10T10:05:00Z"
      },
      {
        "userItemId": 1002,
        "itemId": 301,
        "itemName": "프리미엄 간식",
        "itemType": "CONSUMABLE_FOOD",
        "imageUrl": "https://cdn.polaris.com/items/premium_food.png",
        "quantity": 5,
        "isEquipped": false,
        "purchasedAt": "2026-05-14T09:00:00Z"
      }
    ],
    "summary": {
      "totalSkins": 3,
      "totalBackgrounds": 2,
      "totalConsumables": 8
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /items/{itemId}/equip

스킨/배경 장착

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "itemId": 101,
    "itemName": "노바 봄 스킨",
    "itemType": "SKIN",
    "isEquipped": true,
    "equippedAt": "2026-05-14T10:00:00Z",
    "message": "스킨이 장착되었습니다"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `404 ITEM_NOT_OWNED`: 보유하지 않은 아이템
- `400 NOT_EQUIPPABLE`: 장착 불가능한 아이템 타입

---

### POST /items/{itemId}/use

소모품 사용

**Request Body:**
```json
{
  "quantity": 1
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "itemId": 301,
    "itemName": "프리미엄 간식",
    "itemType": "CONSUMABLE_FOOD",
    "quantityUsed": 1,
    "remainingQuantity": 4,
    "effect": {
      "stateType": "fullness",
      "stateBefore": 50,
      "stateAfter": 100,
      "increase": 50
    },
    "characterResponse": "먹는 중... 이건 특별한 맛이야!",
    "usedAt": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 INSUFFICIENT_QUANTITY`: 보유 수량 부족
- `400 NOT_CONSUMABLE`: 소모품이 아님

---

### GET /items/purchase-history

아이템 구매 이력 조회 (Cursor 기반 페이징)

**Query Parameters:**
- `cursor` (optional): 다음 페이지 커서
- `limit` (optional, default: 20): 페이지 크기

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "purchases": [
      {
        "purchaseId": 5001,
        "itemId": 101,
        "itemName": "노바 봄 스킨",
        "itemType": "SKIN",
        "quantity": 1,
        "totalPrice": 50,
        "purchasedAt": "2026-05-10T10:00:00Z"
      },
      {
        "purchaseId": 5000,
        "itemId": 301,
        "itemName": "프리미엄 간식",
        "itemType": "CONSUMABLE_FOOD",
        "quantity": 5,
        "totalPrice": 25,
        "purchasedAt": "2026-05-09T15:00:00Z"
      }
    ],
    "pagination": {
      "nextCursor": "eyJpZCI6NTAwMH0=",
      "hasMore": true
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---


## 별조각 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| GET | `/star-pieces/balance` | 별조각 잔액 조회 | 🔐 💾 | UC-020 |
| GET | `/star-pieces/transactions` | 거래 내역 조회 | 🔐 | - |

---

### GET /star-pieces/balance

별조각 잔액 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "balance": 150,
    "lastUpdated": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /star-pieces/transactions

별조각 거래 내역 조회 (Cursor 기반 페이징)

**Query Parameters:**
- `type` (optional): EARN | SPEND
- `source` (optional): MISSION_COMPLETION | SHARE_REWARD | ITEM_PURCHASE | CARE_ACTION | ATTENDANCE
- `cursor` (optional): 다음 페이지 커서
- `limit` (optional, default: 20): 페이지 크기

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "transactions": [
      {
        "transactionId": 1001,
        "type": "EARN",
        "source": "MISSION_COMPLETION",
        "amount": 10,
        "balanceAfter": 150,
        "description": "미션 완료: 물 한 컵 마시기",
        "relatedEntity": {
          "type": "MISSION",
          "id": 12345
        },
        "createdAt": "2026-05-14T10:00:00Z"
      },
      {
        "transactionId": 1000,
        "type": "SPEND",
        "source": "ITEM_PURCHASE",
        "amount": -50,
        "balanceAfter": 140,
        "description": "아이템 구매: 노바 봄 스킨",
        "relatedEntity": {
          "type": "ITEM",
          "id": 101
        },
        "createdAt": "2026-05-14T09:00:00Z"
      },
      {
        "transactionId": 999,
        "type": "SPEND",
        "source": "CARE_ACTION",
        "amount": -3,
        "balanceAfter": 190,
        "description": "돌봄 액션: 밥 주기",
        "relatedEntity": {
          "type": "CHARACTER_CARE",
          "id": 456
        },
        "createdAt": "2026-05-14T08:00:00Z"
      }
    ],
    "pagination": {
      "nextCursor": "eyJpZCI6OTk5fQ==",
      "hasMore": true
    },
    "summary": {
      "totalEarned": 120,
      "totalSpent": 103,
      "netChange": 17
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

## 알림 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| POST | `/notifications/device-tokens` | 디바이스 토큰 등록 | 🔐 | - |
| DELETE | `/notifications/device-tokens/{token}` | 디바이스 토큰 삭제 | 🔐 | - |
| GET | `/notifications/settings` | 알림 설정 조회 | 🔐 💾 | UC-031 |
| PUT | `/notifications/settings` | 알림 설정 변경 | 🔐 | UC-031 |
| GET | `/notifications/logs` | 알림 이력 조회 | 🔐 | - |

---

### POST /notifications/device-tokens

푸시 알림용 디바이스 토큰 등록

**Request Body:**
```json
{
  "token": "fcm_token_or_apns_token_here",
  "platform": "IOS"
}
```

**Platforms:**
- `IOS`: Apple Push Notification Service
- `ANDROID`: Firebase Cloud Messaging

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "tokenId": 7001,
    "token": "fcm_token_or_apns_token_here",
    "platform": "IOS",
    "active": true,
    "createdAt": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### DELETE /notifications/device-tokens/{token}

디바이스 토큰 삭제 (로그아웃 시)

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "message": "디바이스 토큰이 삭제되었습니다"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /notifications/settings

알림 설정 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "missionOfferEnabled": true,
    "stateAlertEnabled": true,
    "achievementEnabled": true,
    "dailyReminderEnabled": true,
    "preferredTime": "MORNING",
    "quietHours": {
      "start": "23:00",
      "end": "07:00"
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### PUT /notifications/settings

알림 설정 변경

**Request Body:**
```json
{
  "enabled": true,
  "missionOfferEnabled": true,
  "stateAlertEnabled": false,
  "achievementEnabled": true,
  "dailyReminderEnabled": true,
  "preferredTime": "AFTERNOON",
  "quietHours": {
    "start": "23:00",
    "end": "07:00"
  }
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "missionOfferEnabled": true,
    "stateAlertEnabled": false,
    "achievementEnabled": true,
    "dailyReminderEnabled": true,
    "preferredTime": "AFTERNOON",
    "quietHours": {
      "start": "23:00",
      "end": "07:00"
    },
    "updatedAt": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /notifications/logs

알림 전송 이력 조회 (Cursor 기반 페이징)

**Query Parameters:**
- `type` (optional): MISSION_OFFER | STATE_BAD | STATE_CRITICAL | ACHIEVEMENT | DAILY_REMINDER
- `cursor` (optional): 다음 페이지 커서
- `limit` (optional, default: 20): 페이지 크기

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "notificationId": 9001,
        "type": "MISSION_OFFER",
        "title": "오늘의 작은 별을 찾았어요",
        "body": "노바가 새로운 미션을 준비했어요",
        "sentAt": "2026-05-14T09:00:00Z",
        "deliveryStatus": "SENT"
      },
      {
        "notificationId": 9000,
        "type": "STATE_BAD",
        "title": "캐릭터가 당신을 기다려요",
        "body": "노바가 쓸쓸해하는 것 같아요",
        "sentAt": "2026-05-13T20:00:00Z",
        "deliveryStatus": "SENT"
      }
    ],
    "pagination": {
      "nextCursor": "eyJpZCI6OTAwMH0=",
      "hasMore": true
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---


## 공유 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| POST | `/share/cards` | 캐릭터 카드 생성 | 🔐 | UC-025 |
| GET | `/share/cards/{shareCardId}` | 공유 카드 조회 (공개) | - 💾 | UC-027 |
| POST | `/share/cards/{shareCardId}/share` | SNS 공유 완료 기록 | 🔐 ⚠️ | UC-026 |
| GET | `/share/events` | 공유 이력 조회 | 🔐 | - |

---

### POST /share/cards

캐릭터 카드 생성

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "shareCardId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "imageUrl": "https://cdn.polaris.com/share-cards/a1b2c3d4.png",
    "shareUrl": "https://polaris.app/share/a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "cardData": {
      "characterName": "노바",
      "characterNickname": "내 노바",
      "characterImageUrl": "https://cdn.polaris.com/characters/nova_idle.png",
      "todayStarPieces": 30,
      "characterMessage": "오늘도 작은 별을 모았어."
    },
    "expiresAt": "2026-05-21T10:00:00Z",
    "createdAt": "2026-05-14T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /share/cards/{shareCardId}

공유 카드 조회 (공개 API, 인증 불필요)

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "shareCardId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "imageUrl": "https://cdn.polaris.com/share-cards/a1b2c3d4.png",
    "cardData": {
      "characterName": "노바",
      "characterNickname": "내 노바",
      "characterImageUrl": "https://cdn.polaris.com/characters/nova_idle.png",
      "todayStarPieces": 30,
      "characterMessage": "오늘도 작은 별을 모았어."
    },
    "isExpired": false,
    "expiresAt": "2026-05-21T10:00:00Z"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `404 SHARE_CARD_NOT_FOUND`: 존재하지 않는 공유 카드
- `410 SHARE_CARD_EXPIRED`: 만료된 공유 카드

---

### POST /share/cards/{shareCardId}/share

SNS 공유 완료 기록

**Request Body:**
```json
{
  "platform": "INSTAGRAM",
  "shareType": "CHARACTER_CARD"
}
```

**Platforms:**
- `KAKAO`: 카카오톡
- `INSTAGRAM`: 인스타그램
- `TWITTER`: 트위터
- `FACEBOOK`: 페이스북
- `OTHER`: 기타

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "shareEventId": 8001,
    "shareCardId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "platform": "INSTAGRAM",
    "sharedAt": "2026-05-14T10:00:00Z",
    "reward": {
      "starPieces": 10,
      "message": "공유 보상으로 별조각 10개를 받았어요!"
    },
    "newBalance": {
      "starPieces": 160
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Response (200 OK - 오늘 이미 보상 받음):**
```json
{
  "success": true,
  "data": {
    "shareEventId": 8002,
    "shareCardId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "platform": "TWITTER",
    "sharedAt": "2026-05-14T10:00:00Z",
    "reward": null,
    "message": "오늘은 이미 공유 보상을 받았어요. 내일 다시 받을 수 있어요!"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /share/events

공유 이력 조회 (Cursor 기반 페이징)

**Query Parameters:**
- `cursor` (optional): 다음 페이지 커서
- `limit` (optional, default: 20): 페이지 크기

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "shareEvents": [
      {
        "shareEventId": 8001,
        "shareCardId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "shareType": "CHARACTER_CARD",
        "platform": "INSTAGRAM",
        "sharedAt": "2026-05-14T10:00:00Z",
        "rewardPaid": true
      },
      {
        "shareEventId": 8000,
        "shareCardId": "b2c3d4e5-f6g7-8901-bcde-fg2345678901",
        "shareType": "CHARACTER_CARD",
        "platform": "KAKAO",
        "sharedAt": "2026-05-13T15:00:00Z",
        "rewardPaid": true
      }
    ],
    "pagination": {
      "nextCursor": "eyJpZCI6ODAwMH0=",
      "hasMore": true
    },
    "summary": {
      "totalShares": 15,
      "totalRewards": 150
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

## 업적 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| GET | `/achievements` | 업적 목록 조회 | 🔐 💾 | UC-029 |
| GET | `/achievements/me` | 내 업적 진행도 조회 | 🔐 | UC-029 |
| POST | `/achievements/{achievementId}/claim` | 업적 보상 수령 | 🔐 ⚠️ | UC-030 |

---

### GET /achievements

전체 업적 목록 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "achievements": [
      {
        "achievementId": "ACH_001",
        "name": "첫 걸음",
        "description": "첫 미션을 완료하세요",
        "achievementType": "MISSION_COUNT",
        "requirement": 1,
        "rewardStarPieces": 5,
        "iconUrl": "https://cdn.polaris.com/achievements/first_step.png"
      },
      {
        "achievementId": "ACH_002",
        "name": "별 수집가",
        "description": "미션 10개를 완료하세요",
        "achievementType": "MISSION_COUNT",
        "requirement": 10,
        "rewardStarPieces": 20,
        "iconUrl": "https://cdn.polaris.com/achievements/star_collector.png"
      },
      {
        "achievementId": "ACH_003",
        "name": "공유의 기쁨",
        "description": "캐릭터 카드를 처음 공유하세요",
        "achievementType": "SHARE",
        "requirement": 1,
        "rewardStarPieces": 10,
        "iconUrl": "https://cdn.polaris.com/achievements/first_share.png"
      }
    ]
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### GET /achievements/me

내 업적 진행도 조회

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "userAchievements": [
      {
        "achievementId": "ACH_001",
        "name": "첫 걸음",
        "description": "첫 미션을 완료하세요",
        "requirement": 1,
        "progress": 1,
        "progressRate": 100,
        "completed": true,
        "completedAt": "2026-05-01T11:00:00Z",
        "rewardStarPieces": 5,
        "rewardClaimed": true
      },
      {
        "achievementId": "ACH_002",
        "name": "별 수집가",
        "description": "미션 10개를 완료하세요",
        "requirement": 10,
        "progress": 7,
        "progressRate": 70,
        "completed": false,
        "rewardStarPieces": 20
      },
      {
        "achievementId": "ACH_003",
        "name": "공유의 기쁨",
        "description": "캐릭터 카드를 처음 공유하세요",
        "requirement": 1,
        "progress": 0,
        "progressRate": 0,
        "completed": false,
        "rewardStarPieces": 10
      }
    ],
    "summary": {
      "totalAchievements": 15,
      "completedAchievements": 3,
      "completionRate": 20,
      "totalRewardsClaimed": 35
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### POST /achievements/{achievementId}/claim

업적 보상 수령

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "achievementId": "ACH_001",
    "achievementName": "첫 걸음",
    "rewardStarPieces": 5,
    "newBalance": {
      "starPieces": 155
    },
    "claimedAt": "2026-05-14T10:00:00Z",
    "message": "축하합니다! 업적을 달성했어요!"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 ACHIEVEMENT_NOT_COMPLETED`: 업적 미완료
- `400 REWARD_ALREADY_CLAIMED`: 이미 보상 수령함

---


## 부가 기능 API

### API 목록

| Method | Endpoint | 설명 | 인증 | 관련 UC |
|--------|----------|------|------|---------|
| POST | `/attendance/check` | 출석 체크 | 🔐 ⚠️ | UC-028 |
| GET | `/attendance/calendar` | 출석 캘린더 조회 | 🔐 | UC-028 |

---

### POST /attendance/check

출석 체크

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "attendanceId": 6001,
    "checkedAt": "2026-05-14T10:00:00Z",
    "consecutiveDays": 5,
    "reward": {
      "starPieces": 3,
      "affection": 3
    },
    "newBalance": {
      "starPieces": 153,
      "affection": 56
    },
    "message": "5일 연속 출석! 별조각 3개를 받았어요!"
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Error Responses:**
- `400 ALREADY_CHECKED_TODAY`: 오늘 이미 출석함

---

### GET /attendance/calendar

출석 캘린더 조회

**Query Parameters:**
- `year` (required): 연도 (예: 2026)
- `month` (required): 월 (1-12)

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "year": 2026,
    "month": 5,
    "attendances": [
      {
        "date": "2026-05-01",
        "checked": true,
        "consecutiveDays": 1
      },
      {
        "date": "2026-05-02",
        "checked": true,
        "consecutiveDays": 2
      },
      {
        "date": "2026-05-03",
        "checked": false,
        "consecutiveDays": 0
      },
      {
        "date": "2026-05-04",
        "checked": true,
        "consecutiveDays": 1
      }
    ],
    "summary": {
      "totalDays": 31,
      "checkedDays": 12,
      "checkRate": 38.71,
      "currentStreak": 5,
      "longestStreak": 7
    }
  },
  "error": null,
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

## 에러 코드 정의

### 인증 관련 (401)

| 코드 | 메시지 | 설명 |
|------|--------|------|
| `UNAUTHORIZED` | 인증이 필요합니다 | Access Token 없음 |
| `INVALID_TOKEN` | 유효하지 않은 토큰입니다 | 토큰 검증 실패 |
| `TOKEN_EXPIRED` | 토큰이 만료되었습니다 | Access Token 만료 |
| `INVALID_AUTHORIZATION_CODE` | 유효하지 않은 인증 코드입니다 | Google OAuth 코드 오류 |

### 권한 관련 (403)

| 코드 | 메시지 | 설명 |
|------|--------|------|
| `FORBIDDEN` | 접근 권한이 없습니다 | 리소스 접근 권한 없음 |
| `ACCOUNT_DELETED` | 탈퇴한 계정입니다 | 소프트 삭제된 계정 |

### 리소스 관련 (404)

| 코드 | 메시지 | 설명 |
|------|--------|------|
| `USER_NOT_FOUND` | 사용자를 찾을 수 없습니다 | 존재하지 않는 사용자 |
| `CHARACTER_NOT_FOUND` | 캐릭터를 찾을 수 없습니다 | 존재하지 않는 캐릭터 |
| `MISSION_NOT_FOUND` | 미션을 찾을 수 없습니다 | 존재하지 않는 미션 |
| `ITEM_NOT_FOUND` | 아이템을 찾을 수 없습니다 | 존재하지 않는 아이템 |
| `SHARE_CARD_NOT_FOUND` | 공유 카드를 찾을 수 없습니다 | 존재하지 않는 공유 카드 |

### 비즈니스 로직 관련 (400)

| 코드 | 메시지 | 설명 |
|------|--------|------|
| `ALREADY_HAS_CHARACTER` | 이미 캐릭터가 있습니다 | 캐릭터 중복 생성 시도 |
| `INSUFFICIENT_STAR_PIECES` | 별조각이 부족합니다 | 잔액 부족 |
| `ALREADY_PURCHASED` | 이미 구매한 아이템입니다 | 스킨/배경 중복 구매 |
| `ITEM_NOT_OWNED` | 보유하지 않은 아이템입니다 | 미보유 아이템 사용 시도 |
| `INSUFFICIENT_QUANTITY` | 보유 수량이 부족합니다 | 소모품 수량 부족 |
| `STATE_ALREADY_MAX` | 이미 최대치입니다 | 상태값이 이미 100 |
| `COOLDOWN_ACTIVE` | 쿨다운 중입니다 | 재우기 쿨다운 |
| `ALREADY_REJECTED` | 이미 거절한 미션입니다 | 미션 중복 거절 |
| `ALREADY_COMPLETED` | 이미 완료한 미션입니다 | 미션 중복 완료 |
| `MISSION_REJECTED` | 거절한 미션은 완료할 수 없습니다 | 거절된 미션 완료 시도 |
| `DAILY_LIMIT_REACHED` | 오늘 제안 횟수를 초과했습니다 | 하루 15개 제한 |
| `ANSWER_TOO_SHORT` | 답변을 입력해주세요 | 답변 1자 미만 |
| `ANSWER_TOO_LONG` | 300자 이하로 입력해주세요 | 답변 300자 초과 |
| `PROFANITY_DETECTED` | 적절하지 않은 내용입니다 | 욕설 필터 감지 |
| `INVALID_NICKNAME` | 사용할 수 없는 이름입니다 | 닉네임 검증 실패 |
| `INVALID_QUANTITY` | 수량이 올바르지 않습니다 | 수량 범위 오류 |
| `NOT_EQUIPPABLE` | 장착할 수 없는 아이템입니다 | 소모품 장착 시도 |
| `NOT_CONSUMABLE` | 소모품이 아닙니다 | 스킨/배경 사용 시도 |
| `ACHIEVEMENT_NOT_COMPLETED` | 업적을 완료하지 않았습니다 | 미완료 업적 보상 수령 |
| `REWARD_ALREADY_CLAIMED` | 이미 보상을 받았습니다 | 중복 보상 수령 |
| `ALREADY_CHECKED_TODAY` | 오늘 이미 출석했습니다 | 중복 출석 |

### 서버 관련 (500, 503)

| 코드 | 메시지 | 설명 |
|------|--------|------|
| `INTERNAL_SERVER_ERROR` | 서버 오류가 발생했습니다 | 예상치 못한 서버 오류 |
| `DATABASE_ERROR` | 데이터베이스 오류가 발생했습니다 | DB 연결/쿼리 오류 |
| `GOOGLE_API_UNAVAILABLE` | Google 서비스를 사용할 수 없습니다 | Google API 장애 |
| `AI_SERVICE_UNAVAILABLE` | AI 서비스를 사용할 수 없습니다 | AI API 장애 |

---

## API 설계 원칙

### 1. REST 원칙 준수

✅ **자원 중심 설계**
```
GET /users/me              (O)
GET /getUserInfo           (X)
```

✅ **HTTP 메서드 의미 준수**
- `GET`: 조회
- `POST`: 생성, 액션
- `PUT`: 전체 수정
- `PATCH`: 부분 수정
- `DELETE`: 삭제

✅ **복수형 명사 사용**
```
GET /items                 (O)
GET /item                  (X)
```

---

### 2. 페이징 전략

**Cursor 기반 페이징 (권장)**
```json
{
  "data": [...],
  "pagination": {
    "nextCursor": "eyJpZCI6MTAwMH0=",
    "hasMore": true
  }
}
```

**장점:**
- 실시간 데이터 변경에 안전
- 대용량 데이터 처리 효율적
- 중복/누락 없음

**사용 API:**
- 미션 이력
- 돌봄 이력
- 거래 내역
- 알림 이력
- 공유 이력
- 구매 이력

---

### 3. 캐싱 전략

**캐싱 대상 API (💾 표시)**

| API | 캐시 TTL | 무효화 조건 |
|-----|----------|------------|
| `GET /characters/types` | 1시간 | 캐릭터 타입 변경 시 |
| `GET /items` | 10분 | 아이템 추가/수정 시 |
| `GET /users/me/profile` | 5분 | 프로필 수정 시 |
| `GET /characters/me` | 1분 | 상태 변경 시 |
| `GET /characters/me/image` | 1분 | 상태/장착 변경 시 |
| `GET /star-pieces/balance` | 30초 | 거래 발생 시 |
| `GET /notifications/settings` | 5분 | 설정 변경 시 |
| `GET /achievements` | 1시간 | 업적 추가 시 |
| `GET /share/cards/{id}` | 1시간 | 만료 시 |

**캐시 키 전략:**
```
user:{userId}:profile
user:{userId}:character
user:{userId}:balance
items:list
characters:types
```

---

### 4. 여러 모듈 쓰기 발생 API (⚠️ 표시)

**트랜잭션 관리 필요**

| API | 관련 모듈 | 처리 방식 |
|-----|----------|----------|
| `POST /missions/{id}/answer` | Mission, User, Character | Saga Pattern |
| `POST /characters/me/care/feed` | Character, User | 2PC 또는 Saga |
| `POST /characters/me/care/play` | Character, User | 2PC 또는 Saga |
| `POST /items/{id}/purchase` | Item, User | 2PC 또는 Saga |
| `POST /items/{id}/use` | Item, Character | 2PC 또는 Saga |
| `POST /share/cards/{id}/share` | Character, User | Saga Pattern |
| `POST /achievements/{id}/claim` | Achievement, User | Saga Pattern |
| `POST /attendance/check` | User, Character | Saga Pattern |

**Saga Pattern 예시 (미션 완료):**
```
1. Mission 모듈: 미션 상태 COMPLETED 변경
2. Mission 모듈: MissionCompletedEvent 발행
3. User 모듈: 이벤트 구독 → 별조각 지급
4. Character 모듈: 이벤트 구독 → affection 증가
```

---

### 5. 인증 전략

**JWT 기반 인증**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**토큰 구조:**
```json
{
  "sub": "123",
  "email": "user@gmail.com",
  "iat": 1715684400,
  "exp": 1715688000
}
```

**토큰 갱신 흐름:**
```
1. Access Token 만료 (401 TOKEN_EXPIRED)
2. 클라이언트가 Refresh Token으로 갱신 요청
3. 새로운 Access Token 발급
4. 원래 요청 재시도
```

---

### 6. Rate Limiting

**사용자별 제한**

| 엔드포인트 | 제한 | 기간 |
|-----------|------|------|
| `POST /auth/google` | 10회 | 1시간 |
| `POST /missions/{id}/reject` | 15회 | 1일 |
| `POST /share/cards` | 20회 | 1일 |
| `POST /attendance/check` | 1회 | 1일 |
| 기타 API | 1000회 | 1시간 |

**응답 헤더:**
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1715688000
```

**제한 초과 시:**
```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "요청 횟수를 초과했습니다",
    "details": {
      "retryAfter": 3600
    }
  },
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

### 7. API 버저닝

**URL 기반 버저닝 (현재 방식)**
```
https://api.polaris.app/v1/users/me
https://api.polaris.app/v2/users/me
```

**하위 호환성 유지 원칙:**
- 필드 추가: OK
- 필드 삭제: 새 버전 필요
- 필드 타입 변경: 새 버전 필요
- 엔드포인트 삭제: Deprecated 후 6개월 뒤 제거

---

## API 테스트 가이드

### Postman Collection

```json
{
  "info": {
    "name": "Polaris API v1",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "auth": {
    "type": "bearer",
    "bearer": [
      {
        "key": "token",
        "value": "{{accessToken}}",
        "type": "string"
      }
    ]
  },
  "variable": [
    {
      "key": "baseUrl",
      "value": "https://api-staging.polaris.app/v1"
    },
    {
      "key": "accessToken",
      "value": ""
    }
  ]
}
```

### 테스트 시나리오

**1. 온보딩 플로우**
```
1. POST /auth/google (로그인)
2. GET /characters/types (캐릭터 목록 조회)
3. POST /characters (캐릭터 생성)
4. POST /users/me/onboarding (설문 제출)
5. GET /missions/current (첫 미션 조회)
```

**2. 미션 완료 플로우**
```
1. GET /missions/current (미션 조회)
2. POST /missions/{id}/complete (완료 시작)
3. POST /missions/{id}/answer (답변 제출)
4. GET /star-pieces/balance (잔액 확인)
5. GET /characters/me (캐릭터 상태 확인)
```

**3. 아이템 구매 플로우**
```
1. GET /items (아이템 목록 조회)
2. POST /items/{id}/purchase (구매)
3. GET /items/inventory (인벤토리 확인)
4. POST /items/{id}/equip (장착)
5. GET /characters/me/image (이미지 변경 확인)
```

---

## 다음 문서

---

**문서 작성 완료일**: 2026-05-14
