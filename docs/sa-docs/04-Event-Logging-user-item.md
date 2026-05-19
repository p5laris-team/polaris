# 각 모듈에서 로그 수집이 필요한 API 정리

---

**USER 모듈**

| API | 이벤트 타입 | 수집 목적 | 발생 시점 |
| --- | --- | --- | --- |
| `POST /api/auth/v1/google/sessions` | `USER_SIGNED_UP` | 신규 가입자 수, 가입 전환율, OAuth 유입 분석 | Google 로그인 처리 중 신규 유저 생성 트랜잭션 커밋 후 |
| `POST /api/auth/v1/google/sessions` | `USER_LOGGED_IN` | DAU, D1 Retention, 재방문 분석 | 로그인 성공 후 |
| `PUT /api/onboarding/v1/profiles/me` | `ONBOARDING_PROFILE_SAVED` | 온보딩 중간 저장/이탈 구간 분석 | 온보딩 프로필 저장 커밋 후 |
| `PUT /api/onboarding/v1/profiles/me` | `ONBOARDING_COMPLETED` | 온보딩 완료율, 개인화 준비 완료율 분석 | `completed=true` 저장 커밋 후 |
| `POST /api/attendance/v1/attendance-records` | `ATTENDANCE_CHECKED` | 출석률, 연속 출석, 리텐션 분석 | 출석 기록 생성 커밋 후 |
| `POST /api/attendance/v1/attendance-records` | `STAR_PIECE_EARNED` | 별조각 획득량, 보상 시스템 사용 분석 | 출석 보상 지급 커밋 후 |

---

## USER payload 예시

```json
{
  "eventType": "USER_SIGNED_UP",
  "userId": 1,
  "refType": "USER",
  "refId": 1,
  "metadata": {
    "provider": "GOOGLE",
    "role": "USER",
    "status": "ACTIVE"
  },
  "createdAt": "2026-05-19T15:30:00+09:00"
}
```

```json
{
  "eventType": "USER_LOGGED_IN",
  "userId": 1,
  "refType": "USER",
  "refId": 1,
  "metadata": {
    "provider": "GOOGLE"
  },
  "createdAt": "2026-05-19T15:30:00+09:00"
}
```

```json
{
  "eventType": "ONBOARDING_PROFILE_SAVED",
  "userId": 1,
  "refType": "ONBOARDING_PROFILE",
  "refId": 10,
  "metadata": {
    "completed": false,
    "livingType": "LIVING_ALONE",
    "wakeUpTime": "10:00",
    "sleepTime": "23:00",
    "preferredMissionTime": "EVENING",
    "routineGoal": "SELF_CARE",
    "activityPreference": "INDOOR",
    "missionIntensity": "LIGHT"
  },
  "createdAt": "2026-05-19T15:35:00+09:00"
}
```

```json
{
  "eventType": "ONBOARDING_COMPLETED",
  "userId": 1,
  "refType": "ONBOARDING_PROFILE",
  "refId": 10,
  "metadata": {
    "livingType": "LIVING_ALONE",
    "wakeUpTime": "10:00",
    "sleepTime": "23:00",
    "preferredMissionTime": "EVENING",
    "routineGoal": "SELF_CARE",
    "activityPreference": "INDOOR",
    "missionIntensity": "LIGHT"
  },
  "createdAt": "2026-05-19T15:36:00+09:00"
}
```

```json
{
  "eventType": "ATTENDANCE_CHECKED",
  "userId": 1,
  "refType": "ATTENDANCE_RECORD",
  "refId": 100,
  "metadata": {
    "attendanceDate": "2026-05-19",
    "streakCount": 3,
    "rewardStarPiece": 10
  },
  "createdAt": "2026-05-19T09:00:00+09:00"
}
```

```json
{
  "eventType": "STAR_PIECE_EARNED",
  "userId": 1,
  "refType": "ATTENDANCE_RECORD",
  "refId": 100,
  "metadata": {
    "reason": "ATTENDANCE",
    "amount": 10,
    "balanceAfter": 120,
    "transactionId": 501
  },
  "createdAt": "2026-05-19T09:00:00+09:00"
}
```

---

**ITEM 모듈**

| API | 이벤트 타입 | 수집 목적 | 발생 시점 |
| --- | --- | --- | --- |
| `POST /api/item/v1/item-purchases` | `ITEM_PURCHASED` | 아이템 구매율, 인기 아이템, 소비 행동 분석 | 아이템 구매 트랜잭션 커밋 후 |
| `POST /api/item/v1/item-purchases` | `STAR_PIECE_SPENT` | 별조각 사용률, 경제 시스템 작동 분석 | 별조각 차감 및 구매 커밋 후 |

---

## ITEM payload 예시

```json
{
  "eventType": "ITEM_PURCHASED",
  "userId": 1,
  "refType": "ITEM",
  "refId": 3,
  "metadata": {
    "purchaseId": 700,
    "itemId": 3,
    "itemName": "soft-star-skin",
    "itemType": "SKIN",
    "quantity": 1,
    "totalPrice": 60,
    "transactionId": 901,
    "walletStarPieceAfter": 67
  },
  "createdAt": "2026-05-19T15:42:00+09:00"
}
```

```json
{
  "eventType": "STAR_PIECE_SPENT",
  "userId": 1,
  "refType": "ITEM",
  "refId": 3,
  "metadata": {
    "reason": "ITEM_PURCHASE",
    "amount": 60,
    "balanceAfter": 67,
    "transactionId": 901,
    "idempotencyKey": "purchase-1-3-..."
  },
  "createdAt": "2026-05-19T15:42:00+09:00"
}
```
---

```text
USER_SIGNED_UP
USER_LOGGED_IN
ONBOARDING_PROFILE_SAVED
ONBOARDING_COMPLETED
ATTENDANCE_CHECKED
STAR_PIECE_EARNED
ITEM_PURCHASED
STAR_PIECE_SPENT
```

---