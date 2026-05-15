# 12. 에러 코드 설계서

## 문서 정보

| 항목 | 내용 |
|------|------|
| 문서명 | Polaris MVP 에러 코드 설계서 |
| 작성일 | 2026-05-14 |
| 버전 | v1.0 |
| 목적 | 체계적인 에러 코드 정의 및 관리 |
| 대상 독자 | 백엔드 개발자, 프론트엔드 개발자 |

---

## 📋 목차

1. [에러 코드 체계](#에러-코드-체계)
2. [HTTP 상태 코드 매핑](#http-상태-코드-매핑)
3. [공통 에러 코드](#공통-에러-코드)
4. [모듈별 에러 코드](#모듈별-에러-코드)
5. [에러 응답 형식](#에러-응답-형식)
6. [에러 처리 가이드](#에러-처리-가이드)
7. [다국어 지원](#다국어-지원)

---

## 에러 코드 체계

### 에러 코드 구조

```
{CATEGORY}{MODULE}{NUMBER}
```

#### 구성 요소

| 요소 | 설명 | 예시 |
|------|------|------|
| **CATEGORY** | 에러 카테고리 (1자리) | C, A, R, B, S |
| **MODULE** | 모듈 코드 (2자리) | 00, 01, 02, 03 등 |
| **NUMBER** | 일련번호 (3자리) | 001, 002, 003 등 |

#### 카테고리 코드

| 코드 | 카테고리 | HTTP 상태 | 설명 |
|------|----------|-----------|------|
| **C** | Client Error | 400 | 클라이언트 요청 오류 |
| **A** | Authentication | 401 | 인증 오류 |
| **P** | Permission | 403 | 권한 오류 |
| **R** | Resource | 404 | 리소스 없음 |
| **B** | Business Logic | 400, 409 | 비즈니스 로직 오류 |
| **S** | Server Error | 500 | 서버 내부 오류 |
| **E** | External | 502, 503 | 외부 시스템 오류 |

#### 모듈 코드

| 코드 | 모듈 | 설명 |
|------|------|------|
| **00** | Common | 공통 |
| **01** | User | 사용자 |
| **02** | Character | 캐릭터 |
| **03** | Item | 아이템 |
| **04** | Mission | 미션 |
| **05** | AI | AI |
| **06** | Notification | 알림 |
| **07** | Share | 공유 |
| **08** | Achievement | 업적 |
| **09** | Operation | 운영 |

### 에러 코드 예시

```
C00001: 잘못된 요청 형식 (Client Error - Common - 001)
A01001: 인증 토큰 만료 (Authentication - User - 001)
R02001: 캐릭터를 찾을 수 없음 (Resource - Character - 001)
B04001: 미션 일일 제한 초과 (Business - Mission - 001)
S00001: 서버 내부 오류 (Server - Common - 001)
E05001: AI API 호출 실패 (External - AI - 001)
```

---

## HTTP 상태 코드 매핑

### 상태 코드 사용 원칙

| HTTP 상태 | 사용 시점 | 에러 카테고리 |
|-----------|----------|--------------|
| **200 OK** | 성공 | - |
| **201 Created** | 리소스 생성 성공 | - |
| **400 Bad Request** | 잘못된 요청 | C (Client), B (Business) |
| **401 Unauthorized** | 인증 실패 | A (Authentication) |
| **403 Forbidden** | 권한 없음 | P (Permission) |
| **404 Not Found** | 리소스 없음 | R (Resource) |
| **409 Conflict** | 리소스 충돌 | B (Business) |
| **429 Too Many Requests** | Rate Limit 초과 | C (Client) |
| **500 Internal Server Error** | 서버 오류 | S (Server) |
| **502 Bad Gateway** | 외부 시스템 오류 | E (External) |
| **503 Service Unavailable** | 서비스 이용 불가 | E (External) |

---

## 공통 에러 코드

### C00xxx: 클라이언트 요청 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **C00001** | 400 | Invalid request format | 잘못된 요청 형식 |
| **C00002** | 400 | Missing required field: {field} | 필수 필드 누락 |
| **C00003** | 400 | Invalid field value: {field} | 잘못된 필드 값 |
| **C00004** | 400 | Field length exceeded: {field} | 필드 길이 초과 |
| **C00005** | 400 | Invalid date format: {field} | 잘못된 날짜 형식 |
| **C00006** | 400 | Invalid email format | 잘못된 이메일 형식 |
| **C00007** | 400 | Invalid phone number format | 잘못된 전화번호 형식 |
| **C00008** | 400 | Invalid enum value: {field} | 잘못된 Enum 값 |
| **C00009** | 400 | Request body is empty | 요청 본문이 비어있음 |
| **C00010** | 400 | Invalid JSON format | 잘못된 JSON 형식 |
| **C00011** | 429 | Rate limit exceeded | Rate Limit 초과 |
| **C00012** | 400 | Invalid cursor format | 잘못된 Cursor 형식 |
| **C00013** | 400 | Page size exceeded maximum | 페이지 크기 초과 |
| **C00014** | 400 | Invalid sort parameter | 잘못된 정렬 파라미터 |
| **C00015** | 400 | Profanity detected | 부적절한 표현 감지 |

---

### A00xxx: 인증 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **A00001** | 401 | Authentication required | 인증 필요 |
| **A00002** | 401 | Invalid access token | 유효하지 않은 Access Token |
| **A00003** | 401 | Access token expired | Access Token 만료 |
| **A00004** | 401 | Invalid refresh token | 유효하지 않은 Refresh Token |
| **A00005** | 401 | Refresh token expired | Refresh Token 만료 |
| **A00006** | 401 | Token signature verification failed | 토큰 서명 검증 실패 |
| **A00007** | 401 | Token malformed | 토큰 형식 오류 |
| **A00008** | 401 | Authorization header missing | Authorization 헤더 누락 |
| **A00009** | 401 | Invalid authorization header format | 잘못된 Authorization 헤더 형식 |
| **A00010** | 401 | Session expired | 세션 만료 |

---

### P00xxx: 권한 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **P00001** | 403 | Access denied | 접근 거부 |
| **P00002** | 403 | Insufficient permissions | 권한 부족 |
| **P00003** | 403 | Resource access forbidden | 리소스 접근 금지 |
| **P00004** | 403 | Operation not allowed | 작업 허용되지 않음 |
| **P00005** | 403 | Account suspended | 계정 정지됨 |
| **P00006** | 403 | Account deleted | 계정 삭제됨 |
| **P00007** | 403 | IP address blocked | IP 주소 차단됨 |
| **P00008** | 403 | Region restricted | 지역 제한 |

---

### R00xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R00001** | 404 | Resource not found | 리소스를 찾을 수 없음 |
| **R00002** | 404 | Endpoint not found | 엔드포인트를 찾을 수 없음 |
| **R00003** | 404 | Page not found | 페이지를 찾을 수 없음 |

---

### S00xxx: 서버 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **S00001** | 500 | Internal server error | 서버 내부 오류 |
| **S00002** | 500 | Database connection failed | 데이터베이스 연결 실패 |
| **S00003** | 500 | Database query failed | 데이터베이스 쿼리 실패 |
| **S00004** | 500 | Transaction failed | 트랜잭션 실패 |
| **S00005** | 500 | Cache connection failed | 캐시 연결 실패 |
| **S00006** | 500 | File upload failed | 파일 업로드 실패 |
| **S00007** | 500 | File download failed | 파일 다운로드 실패 |
| **S00008** | 500 | Unexpected error occurred | 예상치 못한 오류 발생 |
| **S00009** | 503 | Service temporarily unavailable | 서비스 일시적으로 이용 불가 |
| **S00010** | 503 | Service under maintenance | 서비스 점검 중 |

---

## 모듈별 에러 코드

### User Module (01)

#### A01xxx: 인증 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **A01001** | 401 | Invalid authorization code | 유효하지 않은 인증 코드 |
| **A01002** | 401 | OAuth provider error | OAuth 제공자 오류 |
| **A01003** | 401 | Google API unavailable | Google API 이용 불가 |
| **A01004** | 401 | Apple API unavailable | Apple API 이용 불가 |
| **A01005** | 401 | Invalid OAuth state | 유효하지 않은 OAuth state |

#### R01xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R01001** | 404 | User not found | 사용자를 찾을 수 없음 |
| **R01002** | 404 | User profile not found | 사용자 프로필을 찾을 수 없음 |
| **R01003** | 404 | User session not found | 사용자 세션을 찾을 수 없음 |

#### B01xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B01001** | 409 | Email already exists | 이메일이 이미 존재함 |
| **B01002** | 400 | Onboarding already completed | 온보딩이 이미 완료됨 |
| **B01003** | 400 | Onboarding not completed | 온보딩이 완료되지 않음 |
| **B01004** | 400 | Invalid display name | 유효하지 않은 표시 이름 |
| **B01005** | 400 | Display name too short | 표시 이름이 너무 짧음 |
| **B01006** | 400 | Display name too long | 표시 이름이 너무 김 |
| **B01007** | 400 | Insufficient star pieces | 별조각 부족 |
| **B01008** | 400 | Invalid star piece amount | 유효하지 않은 별조각 수량 |
| **B01009** | 400 | Star piece transaction failed | 별조각 거래 실패 |

---

### Character Module (02)

#### R02xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R02001** | 404 | Character not found | 캐릭터를 찾을 수 없음 |
| **R02002** | 404 | Character type not found | 캐릭터 종류를 찾을 수 없음 |
| **R02003** | 404 | Character asset not found | 캐릭터 에셋을 찾을 수 없음 |
| **R02004** | 404 | Share card not found | 공유 카드를 찾을 수 없음 |

#### B02xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B02001** | 409 | Character already exists | 캐릭터가 이미 존재함 |
| **B02002** | 400 | Invalid character nickname | 유효하지 않은 캐릭터 닉네임 |
| **B02003** | 400 | Character state already max | 캐릭터 상태가 이미 최대치 |
| **B02004** | 400 | Character state already min | 캐릭터 상태가 이미 최소치 |
| **B02005** | 400 | Invalid care type | 유효하지 않은 돌봄 유형 |
| **B02006** | 400 | Care action on cooldown | 돌봄 액션 쿨다운 중 |
| **B02007** | 400 | Cooldown time remaining: {seconds}s | 쿨다운 남은 시간 |
| **B02008** | 400 | Invalid payment type | 유효하지 않은 결제 유형 |
| **B02009** | 400 | Share card expired | 공유 카드 만료됨 |
| **B02010** | 400 | Share reward already paid | 공유 보상 이미 지급됨 |
| **B02011** | 400 | Daily share limit exceeded | 일일 공유 제한 초과 |
| **B02012** | 400 | Referral code invalid | 추천 코드 유효하지 않음 |
| **B02013** | 400 | Self referral not allowed | 자기 자신 추천 불가 |
| **B02014** | 409 | Already referred by another user | 이미 다른 사용자에게 추천받음 |

---

### Item Module (03)

#### R03xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R03001** | 404 | Item not found | 아이템을 찾을 수 없음 |
| **R03002** | 404 | User item not found | 사용자 아이템을 찾을 수 없음 |

#### B03xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B03001** | 409 | Item already purchased | 아이템이 이미 구매됨 |
| **B03002** | 400 | Item not available | 아이템 구매 불가 |
| **B03003** | 400 | Item out of stock | 아이템 재고 없음 |
| **B03004** | 400 | Invalid purchase quantity | 유효하지 않은 구매 수량 |
| **B03005** | 400 | Purchase quantity exceeded | 구매 수량 초과 |
| **B03006** | 400 | Item not owned | 아이템을 보유하지 않음 |
| **B03007** | 400 | Item not equippable | 장착 불가능한 아이템 |
| **B03008** | 400 | Item already equipped | 아이템이 이미 장착됨 |
| **B03009** | 400 | Item not consumable | 소모품이 아님 |
| **B03010** | 400 | Insufficient item quantity | 아이템 수량 부족 |
| **B03011** | 400 | Item character type mismatch | 아이템 캐릭터 타입 불일치 |

---

### Mission Module (04)

#### R04xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R04001** | 404 | Mission not found | 미션을 찾을 수 없음 |
| **R04002** | 404 | Mission template not found | 미션 템플릿을 찾을 수 없음 |
| **R04003** | 404 | Current mission not found | 현재 미션을 찾을 수 없음 |

#### B04xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B04001** | 400 | Daily mission limit exceeded | 일일 미션 제한 초과 |
| **B04002** | 400 | Mission already completed | 미션이 이미 완료됨 |
| **B04003** | 400 | Mission already rejected | 미션이 이미 거절됨 |
| **B04004** | 400 | Mission expired | 미션 만료됨 |
| **B04005** | 400 | Mission not offered | 미션이 제안되지 않음 |
| **B04006** | 400 | Mission not in answering state | 미션이 답변 대기 상태가 아님 |
| **B04007** | 400 | Invalid rejection reason | 유효하지 않은 거절 사유 |
| **B04008** | 400 | Answer too short | 답변이 너무 짧음 |
| **B04009** | 400 | Answer too long | 답변이 너무 김 |
| **B04010** | 400 | Mission completion failed | 미션 완료 실패 |
| **B04011** | 400 | No available missions | 이용 가능한 미션 없음 |
| **B04012** | 400 | Mission generation failed | 미션 생성 실패 |

---

### AI Module (05)

#### E05xxx: 외부 시스템 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **E05001** | 502 | AI API call failed | AI API 호출 실패 |
| **E05002** | 503 | AI API unavailable | AI API 이용 불가 |
| **E05003** | 504 | AI API timeout | AI API 타임아웃 |
| **E05004** | 502 | AI API rate limit exceeded | AI API Rate Limit 초과 |
| **E05005** | 400 | AI response validation failed | AI 응답 검증 실패 |
| **E05006** | 400 | AI response contains forbidden content | AI 응답에 금지된 내용 포함 |
| **E05007** | 500 | Fallback message not found | Fallback 메시지를 찾을 수 없음 |

---

### Notification Module (06)

#### R06xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R06001** | 404 | Device token not found | 디바이스 토큰을 찾을 수 없음 |
| **R06002** | 404 | Notification setting not found | 알림 설정을 찾을 수 없음 |

#### B06xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B06001** | 409 | Device token already registered | 디바이스 토큰이 이미 등록됨 |
| **B06002** | 400 | Invalid device token | 유효하지 않은 디바이스 토큰 |
| **B06003** | 400 | Invalid platform | 유효하지 않은 플랫폼 |
| **B06004** | 400 | Notification disabled | 알림이 비활성화됨 |
| **B06005** | 400 | Quiet hours active | 방해 금지 시간 활성화 |

#### E06xxx: 외부 시스템 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **E06001** | 502 | FCM send failed | FCM 전송 실패 |
| **E06002** | 502 | APNS send failed | APNS 전송 실패 |
| **E06003** | 503 | FCM unavailable | FCM 이용 불가 |
| **E06004** | 503 | APNS unavailable | APNS 이용 불가 |

---

### Share Module (07)

#### R07xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R07001** | 404 | Share card not found | 공유 카드를 찾을 수 없음 |

#### B07xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B07001** | 400 | Share card generation failed | 공유 카드 생성 실패 |
| **B07002** | 400 | Share card expired | 공유 카드 만료됨 |
| **B07003** | 400 | Invalid share platform | 유효하지 않은 공유 플랫폼 |

---

### Achievement Module (08)

#### R08xxx: 리소스 없음

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **R08001** | 404 | Achievement not found | 업적을 찾을 수 없음 |
| **R08002** | 404 | User achievement not found | 사용자 업적을 찾을 수 없음 |

#### B08xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B08001** | 400 | Achievement already completed | 업적이 이미 완료됨 |
| **B08002** | 400 | Achievement not completed | 업적이 완료되지 않음 |
| **B08003** | 400 | Achievement reward already claimed | 업적 보상이 이미 수령됨 |

---

### Operation Module (09)

#### B09xxx: 비즈니스 로직 오류

| 에러 코드 | HTTP | 에러 메시지 | 설명 |
|----------|------|------------|------|
| **B09001** | 400 | Invalid alert type | 유효하지 않은 알림 유형 |
| **B09002** | 400 | Alert already resolved | 알림이 이미 해결됨 |

---

## 에러 응답 형식

### 기본 에러 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "B01007",
    "message": "별조각이 부족합니다",
    "details": {
      "required": 50,
      "current": 30,
      "shortage": 20
    }
  },
  "timestamp": "2026-05-14T10:00:00Z"
}
```

### 필드 검증 오류 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C00002",
    "message": "필수 필드가 누락되었습니다",
    "details": {
      "missingFields": ["email", "displayName"],
      "validationErrors": [
        {
          "field": "email",
          "message": "이메일은 필수입니다",
          "rejectedValue": null
        },
        {
          "field": "displayName",
          "message": "표시 이름은 필수입니다",
          "rejectedValue": null
        }
      ]
    }
  },
  "timestamp": "2026-05-14T10:00:00Z"
}
```

### 다중 검증 오류 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C00003",
    "message": "입력 값이 유효하지 않습니다",
    "details": {
      "validationErrors": [
        {
          "field": "email",
          "message": "이메일 형식이 올바르지 않습니다",
          "rejectedValue": "invalid-email"
        },
        {
          "field": "displayName",
          "message": "표시 이름은 2자 이상이어야 합니다",
          "rejectedValue": "a"
        },
        {
          "field": "age",
          "message": "나이는 0보다 커야 합니다",
          "rejectedValue": -5
        }
      ]
    }
  },
  "timestamp": "2026-05-14T10:00:00Z"
}
```

### 비즈니스 로직 오류 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "B04001",
    "message": "일일 미션 제한을 초과했습니다",
    "details": {
      "dailyLimit": 15,
      "currentCount": 15,
      "resetAt": "2026-05-15T00:00:00Z"
    }
  },
  "timestamp": "2026-05-14T10:00:00Z"
}
```

### 외부 시스템 오류 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "E05001",
    "message": "AI API 호출에 실패했습니다",
    "details": {
      "provider": "OpenAI",
      "statusCode": 503,
      "retryAfter": 60,
      "fallbackUsed": true
    }
  },
  "timestamp": "2026-05-14T10:00:00Z"
}
```

---

## 에러 처리 가이드

### 백엔드 구현

#### ErrorCode Enum

```java
package p5laris.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    // Common - Client Error (C00xxx)
    INVALID_REQUEST_FORMAT(HttpStatus.BAD_REQUEST, "C00001", "잘못된 요청 형식입니다"),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "C00002", "필수 필드가 누락되었습니다: {field}"),
    INVALID_FIELD_VALUE(HttpStatus.BAD_REQUEST, "C00003", "잘못된 필드 값입니다: {field}"),
    FIELD_LENGTH_EXCEEDED(HttpStatus.BAD_REQUEST, "C00004", "필드 길이를 초과했습니다: {field}"),
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "C00005", "잘못된 날짜 형식입니다: {field}"),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "C00006", "잘못된 이메일 형식입니다"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "C00011", "Rate Limit을 초과했습니다"),
    PROFANITY_DETECTED(HttpStatus.BAD_REQUEST, "C00015", "부적절한 표현이 감지되었습니다"),
    
    // Common - Authentication (A00xxx)
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "A00001", "인증이 필요합니다"),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "A00002", "유효하지 않은 Access Token입니다"),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A00003", "Access Token이 만료되었습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A00004", "유효하지 않은 Refresh Token입니다"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A00005", "Refresh Token이 만료되었습니다"),
    
    // Common - Permission (P00xxx)
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "P00001", "접근이 거부되었습니다"),
    INSUFFICIENT_PERMISSIONS(HttpStatus.FORBIDDEN, "P00002", "권한이 부족합니다"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "P00005", "계정이 정지되었습니다"),
    
    // Common - Resource (R00xxx)
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "R00001", "리소스를 찾을 수 없습니다"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "R00002", "엔드포인트를 찾을 수 없습니다"),
    
    // Common - Server (S00xxx)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S00001", "서버 내부 오류가 발생했습니다"),
    DATABASE_CONNECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S00002", "데이터베이스 연결에 실패했습니다"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S00009", "서비스를 일시적으로 이용할 수 없습니다"),
    
    // User Module (01)
    INVALID_AUTHORIZATION_CODE(HttpStatus.UNAUTHORIZED, "A01001", "유효하지 않은 인증 코드입니다"),
    GOOGLE_API_UNAVAILABLE(HttpStatus.UNAUTHORIZED, "A01003", "Google API를 이용할 수 없습니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "R01001", "사용자를 찾을 수 없습니다"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "B01001", "이메일이 이미 존재합니다"),
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "B01002", "온보딩이 이미 완료되었습니다"),
    INSUFFICIENT_STAR_PIECES(HttpStatus.BAD_REQUEST, "B01007", "별조각이 부족합니다"),
    
    // Character Module (02)
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "R02001", "캐릭터를 찾을 수 없습니다"),
    CHARACTER_ALREADY_EXISTS(HttpStatus.CONFLICT, "B02001", "캐릭터가 이미 존재합니다"),
    CHARACTER_STATE_ALREADY_MAX(HttpStatus.BAD_REQUEST, "B02003", "캐릭터 상태가 이미 최대치입니다"),
    CARE_ACTION_ON_COOLDOWN(HttpStatus.BAD_REQUEST, "B02006", "돌봄 액션이 쿨다운 중입니다"),
    SHARE_CARD_EXPIRED(HttpStatus.BAD_REQUEST, "B02009", "공유 카드가 만료되었습니다"),
    
    // Item Module (03)
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "R03001", "아이템을 찾을 수 없습니다"),
    ITEM_ALREADY_PURCHASED(HttpStatus.CONFLICT, "B03001", "아이템이 이미 구매되었습니다"),
    ITEM_NOT_OWNED(HttpStatus.BAD_REQUEST, "B03006", "아이템을 보유하지 않았습니다"),
    ITEM_NOT_EQUIPPABLE(HttpStatus.BAD_REQUEST, "B03007", "장착할 수 없는 아이템입니다"),
    
    // Mission Module (04)
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "R04001", "미션을 찾을 수 없습니다"),
    DAILY_MISSION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "B04001", "일일 미션 제한을 초과했습니다"),
    MISSION_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "B04002", "미션이 이미 완료되었습니다"),
    MISSION_EXPIRED(HttpStatus.BAD_REQUEST, "B04004", "미션이 만료되었습니다"),
    ANSWER_TOO_SHORT(HttpStatus.BAD_REQUEST, "B04008", "답변이 너무 짧습니다"),
    ANSWER_TOO_LONG(HttpStatus.BAD_REQUEST, "B04009", "답변이 너무 깁니다"),
    
    // AI Module (05)
    AI_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "E05001", "AI API 호출에 실패했습니다"),
    AI_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "E05002", "AI API를 이용할 수 없습니다"),
    AI_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "E05003", "AI API 타임아웃이 발생했습니다"),
    
    // Notification Module (06)
    DEVICE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "R06001", "디바이스 토큰을 찾을 수 없습니다"),
    DEVICE_TOKEN_ALREADY_REGISTERED(HttpStatus.CONFLICT, "B06001", "디바이스 토큰이 이미 등록되었습니다"),
    NOTIFICATION_DISABLED(HttpStatus.BAD_REQUEST, "B06004", "알림이 비활성화되었습니다"),
    ;
    
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
    
    public String getMessage(Object... args) {
        return String.format(message, args);
    }
}
```

---

#### BusinessException

```java
package p5laris.common.exception;

import lombok.Getter;
import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = Map.of();
    }
    
    public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Map.of();
    }
    
    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
```

---

#### GlobalExceptionHandler

```java
package p5laris.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import p5laris.common.dto.ApiResponse;
import p5laris.common.dto.ErrorResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * BusinessException 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception occurred: {}", e.getMessage(), e);
        
        ErrorResponse error = ErrorResponse.of(
            e.getErrorCode().getCode(),
            e.getMessage(),
            e.getDetails()
        );
        
        return ResponseEntity
            .status(e.getErrorCode().getHttpStatus())
            .body(ApiResponse.error(error));
    }
    
    /**
     * Validation 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
        MethodArgumentNotValidException e
    ) {
        log.warn("Validation exception occurred: {}", e.getMessage());
        
        List<Map<String, Object>> validationErrors = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::createValidationError)
            .collect(Collectors.toList());
        
        Map<String, Object> details = Map.of("validationErrors", validationErrors);
        
        ErrorResponse error = ErrorResponse.of(
            ErrorCode.INVALID_FIELD_VALUE.getCode(),
            "입력 값이 유효하지 않습니다",
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(error));
    }
    
    /**
     * 예상치 못한 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected exception occurred", e);
        
        ErrorResponse error = ErrorResponse.of(
            ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
            "서버 내부 오류가 발생했습니다",
            Map.of()
        );
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(error));
    }
    
    private Map<String, Object> createValidationError(FieldError fieldError) {
        Map<String, Object> error = new HashMap<>();
        error.put("field", fieldError.getField());
        error.put("message", fieldError.getDefaultMessage());
        error.put("rejectedValue", fieldError.getRejectedValue());
        return error;
    }
}
```

---

#### ErrorResponse DTO

```java
package p5laris.common.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {
    
    private String code;
    private String message;
    private Map<String, Object> details;
    
    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(code, message, details);
    }
    
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of());
    }
}
```

---

### 프론트엔드 처리

#### TypeScript 타입 정의

```typescript
// types/error.ts

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, any>;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  timestamp: string;
}

export interface ValidationError {
  field: string;
  message: string;
  rejectedValue: any;
}
```

---

#### 에러 처리 유틸리티

```typescript
// utils/errorHandler.ts

import { ApiError } from '@/types/error';

export class ErrorHandler {
  
  /**
   * 에러 메시지 추출
   */
  static getMessage(error: ApiError): string {
    return error.message || '알 수 없는 오류가 발생했습니다';
  }
  
  /**
   * 에러 코드별 처리
   */
  static handleError(error: ApiError): void {
    const { code } = error;
    
    // 인증 오류
    if (code.startsWith('A')) {
      this.handleAuthError(error);
      return;
    }
    
    // 권한 오류
    if (code.startsWith('P')) {
      this.handlePermissionError(error);
      return;
    }
    
    // 리소스 없음
    if (code.startsWith('R')) {
      this.handleResourceError(error);
      return;
    }
    
    // 비즈니스 로직 오류
    if (code.startsWith('B')) {
      this.handleBusinessError(error);
      return;
    }
    
    // 서버 오류
    if (code.startsWith('S') || code.startsWith('E')) {
      this.handleServerError(error);
      return;
    }
    
    // 기타 오류
    this.showErrorToast(this.getMessage(error));
  }
  
  /**
   * 인증 오류 처리
   */
  private static handleAuthError(error: ApiError): void {
    const { code } = error;
    
    // Access Token 만료
    if (code === 'A00003') {
      // Refresh Token으로 재발급 시도
      this.refreshAccessToken();
      return;
    }
    
    // Refresh Token 만료
    if (code === 'A00005') {
      // 로그인 페이지로 이동
      this.redirectToLogin();
      return;
    }
    
    // 기타 인증 오류
    this.showErrorToast(this.getMessage(error));
    this.redirectToLogin();
  }
  
  /**
   * 권한 오류 처리
   */
  private static handlePermissionError(error: ApiError): void {
    this.showErrorToast(this.getMessage(error));
    // 이전 페이지로 이동
    window.history.back();
  }
  
  /**
   * 리소스 없음 처리
   */
  private static handleResourceError(error: ApiError): void {
    this.showErrorToast(this.getMessage(error));
  }
  
  /**
   * 비즈니스 로직 오류 처리
   */
  private static handleBusinessError(error: ApiError): void {
    const { code, details } = error;
    
    // 별조각 부족
    if (code === 'B01007') {
      this.showStarPieceShortageDialog(details);
      return;
    }
    
    // 일일 미션 제한 초과
    if (code === 'B04001') {
      this.showDailyLimitDialog(details);
      return;
    }
    
    // 기타 비즈니스 오류
    this.showErrorToast(this.getMessage(error));
  }
  
  /**
   * 서버 오류 처리
   */
  private static handleServerError(error: ApiError): void {
    this.showErrorToast('서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
  }
  
  // Helper methods
  private static showErrorToast(message: string): void {
    // Toast 표시 로직
    console.error(message);
  }
  
  private static showStarPieceShortageDialog(details: any): void {
    // 별조각 부족 다이얼로그 표시
    console.log('별조각 부족:', details);
  }
  
  private static showDailyLimitDialog(details: any): void {
    // 일일 제한 다이얼로그 표시
    console.log('일일 제한 초과:', details);
  }
  
  private static refreshAccessToken(): void {
    // Access Token 재발급 로직
    console.log('Access Token 재발급');
  }
  
  private static redirectToLogin(): void {
    // 로그인 페이지로 이동
    window.location.href = '/login';
  }
}
```

---

#### API 클라이언트 예시

```typescript
// api/client.ts

import axios, { AxiosError } from 'axios';
import { ApiResponse, ApiError } from '@/types/error';
import { ErrorHandler } from '@/utils/errorHandler';

const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 응답 인터셉터
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<any>>) => {
    if (error.response?.data?.error) {
      const apiError = error.response.data.error;
      ErrorHandler.handleError(apiError);
    } else {
      ErrorHandler.handleError({
        code: 'S00001',
        message: '네트워크 오류가 발생했습니다',
      });
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

---

## 다국어 지원

### 메시지 파일 구조

```
src/main/resources/messages/
├── messages.properties           # 기본 (한국어)
├── messages_en.properties        # 영어
└── messages_ja.properties        # 일본어
```

---

### messages.properties (한국어)

```properties
# Common - Client Error (C00xxx)
C00001=잘못된 요청 형식입니다
C00002=필수 필드가 누락되었습니다: {0}
C00003=잘못된 필드 값입니다: {0}
C00004=필드 길이를 초과했습니다: {0}
C00005=잘못된 날짜 형식입니다: {0}
C00006=잘못된 이메일 형식입니다
C00011=Rate Limit을 초과했습니다
C00015=부적절한 표현이 감지되었습니다

# Common - Authentication (A00xxx)
A00001=인증이 필요합니다
A00002=유효하지 않은 Access Token입니다
A00003=Access Token이 만료되었습니다
A00004=유효하지 않은 Refresh Token입니다
A00005=Refresh Token이 만료되었습니다

# Common - Permission (P00xxx)
P00001=접근이 거부되었습니다
P00002=권한이 부족합니다
P00005=계정이 정지되었습니다

# Common - Resource (R00xxx)
R00001=리소스를 찾을 수 없습니다
R00002=엔드포인트를 찾을 수 없습니다

# Common - Server (S00xxx)
S00001=서버 내부 오류가 발생했습니다
S00002=데이터베이스 연결에 실패했습니다
S00009=서비스를 일시적으로 이용할 수 없습니다

# User Module (01)
A01001=유효하지 않은 인증 코드입니다
A01003=Google API를 이용할 수 없습니다
R01001=사용자를 찾을 수 없습니다
B01001=이메일이 이미 존재합니다
B01002=온보딩이 이미 완료되었습니다
B01007=별조각이 부족합니다

# Character Module (02)
R02001=캐릭터를 찾을 수 없습니다
B02001=캐릭터가 이미 존재합니다
B02003=캐릭터 상태가 이미 최대치입니다
B02006=돌봄 액션이 쿨다운 중입니다
B02009=공유 카드가 만료되었습니다

# Item Module (03)
R03001=아이템을 찾을 수 없습니다
B03001=아이템이 이미 구매되었습니다
B03006=아이템을 보유하지 않았습니다
B03007=장착할 수 없는 아이템입니다

# Mission Module (04)
R04001=미션을 찾을 수 없습니다
B04001=일일 미션 제한을 초과했습니다
B04002=미션이 이미 완료되었습니다
B04004=미션이 만료되었습니다
B04008=답변이 너무 짧습니다
B04009=답변이 너무 깁니다

# AI Module (05)
E05001=AI API 호출에 실패했습니다
E05002=AI API를 이용할 수 없습니다
E05003=AI API 타임아웃이 발생했습니다

# Notification Module (06)
R06001=디바이스 토큰을 찾을 수 없습니다
B06001=디바이스 토큰이 이미 등록되었습니다
B06004=알림이 비활성화되었습니다
```

---

### messages_en.properties (영어)

```properties
# Common - Client Error (C00xxx)
C00001=Invalid request format
C00002=Missing required field: {0}
C00003=Invalid field value: {0}
C00004=Field length exceeded: {0}
C00005=Invalid date format: {0}
C00006=Invalid email format
C00011=Rate limit exceeded
C00015=Profanity detected

# Common - Authentication (A00xxx)
A00001=Authentication required
A00002=Invalid access token
A00003=Access token expired
A00004=Invalid refresh token
A00005=Refresh token expired

# Common - Permission (P00xxx)
P00001=Access denied
P00002=Insufficient permissions
P00005=Account suspended

# Common - Resource (R00xxx)
R00001=Resource not found
R00002=Endpoint not found

# Common - Server (S00xxx)
S00001=Internal server error
S00002=Database connection failed
S00009=Service temporarily unavailable

# User Module (01)
A01001=Invalid authorization code
A01003=Google API unavailable
R01001=User not found
B01001=Email already exists
B01002=Onboarding already completed
B01007=Insufficient star pieces

# Character Module (02)
R02001=Character not found
B02001=Character already exists
B02003=Character state already at maximum
B02006=Care action on cooldown
B02009=Share card expired

# Item Module (03)
R03001=Item not found
B03001=Item already purchased
B03006=Item not owned
B03007=Item not equippable

# Mission Module (04)
R04001=Mission not found
B04001=Daily mission limit exceeded
B04002=Mission already completed
B04004=Mission expired
B04008=Answer too short
B04009=Answer too long

# AI Module (05)
E05001=AI API call failed
E05002=AI API unavailable
E05003=AI API timeout

# Notification Module (06)
R06001=Device token not found
B06001=Device token already registered
B06004=Notification disabled
```

---

### MessageSource 설정

```java
package p5laris.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class MessageConfig {
    
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }
}
```

---

### ErrorCode에서 다국어 메시지 사용

```java
package p5laris.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    INVALID_REQUEST_FORMAT(HttpStatus.BAD_REQUEST, "C00001"),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "C00002"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "R01001"),
    INSUFFICIENT_STAR_PIECES(HttpStatus.BAD_REQUEST, "B01007"),
    // ... 기타 에러 코드
    ;
    
    private final HttpStatus httpStatus;
    private final String code;
    
    public String getMessage(MessageSource messageSource, Locale locale, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
```

---

## 에러 코드 관리 가이드

### 에러 코드 추가 절차

1. **에러 코드 설계서 업데이트**
   - 새로운 에러 코드 정의
   - HTTP 상태 코드 매핑
   - 에러 메시지 작성

2. **ErrorCode Enum 추가**
   ```java
   NEW_ERROR_CODE(HttpStatus.BAD_REQUEST, "B01010", "새로운 에러입니다")
   ```

3. **메시지 파일 추가**
   ```properties
   # messages.properties
   B01010=새로운 에러입니다
   
   # messages_en.properties
   B01010=New error occurred
   ```

4. **API 명세서 업데이트**
   - 해당 API의 Error Responses 섹션에 추가

5. **프론트엔드 에러 처리 추가**
   - ErrorHandler에 특수 처리 로직 추가 (필요 시)

---

### 에러 코드 네이밍 규칙

1. **일관성 유지**
   - 같은 카테고리는 같은 접두사 사용
   - 모듈별로 번호 범위 할당

2. **명확한 의미**
   - 에러 코드만 보고도 대략적인 내용 파악 가능
   - 에러 메시지는 구체적이고 명확하게

3. **확장성 고려**
   - 모듈별로 충분한 번호 범위 확보 (001~999)
   - 향후 추가될 에러를 위한 여유 공간

---

### 에러 로깅 가이드

```java
@Slf4j
@Service
public class UserService {
    
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> {
                log.warn("User not found: userId={}", userId);
                return new BusinessException(
                    ErrorCode.USER_NOT_FOUND,
                    Map.of("userId", userId)
                );
            });
    }
    
    public void earnStarPieces(Long userId, int amount) {
        User user = getUserById(userId);
        
        try {
            user.earnStarPieces(amount, "MISSION_COMPLETION");
            userRepository.save(user);
            log.info("Star pieces earned: userId={}, amount={}", userId, amount);
        } catch (Exception e) {
            log.error("Failed to earn star pieces: userId={}, amount={}", userId, amount, e);
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                Map.of("userId", userId, "amount", amount)
            );
        }
    }
}
```

---

## 에러 코드 요약표

### 카테고리별 에러 코드 수

| 카테고리 | 코드 범위 | 에러 수 | 설명 |
|---------|----------|---------|------|
| **C** (Client) | C00001~C00015 | 15 | 클라이언트 요청 오류 |
| **A** (Auth) | A00001~A01005 | 15 | 인증 오류 |
| **P** (Permission) | P00001~P00008 | 8 | 권한 오류 |
| **R** (Resource) | R00001~R08002 | 20+ | 리소스 없음 |
| **B** (Business) | B01001~B09002 | 60+ | 비즈니스 로직 오류 |
| **S** (Server) | S00001~S00010 | 10 | 서버 오류 |
| **E** (External) | E05001~E06004 | 11 | 외부 시스템 오류 |

---

### 모듈별 에러 코드 수

| 모듈 | 코드 | 에러 수 | 주요 에러 |
|------|------|---------|----------|
| **Common** | 00 | 40+ | 공통 에러 |
| **User** | 01 | 14 | 인증, 온보딩, 별조각 |
| **Character** | 02 | 18 | 캐릭터 관리, 돌봄, 공유 |
| **Item** | 03 | 13 | 아이템 구매, 장착 |
| **Mission** | 04 | 15 | 미션 생성, 완료 |
| **AI** | 05 | 7 | AI API 연동 |
| **Notification** | 06 | 9 | 알림 발송 |
| **Share** | 07 | 3 | 공유 카드 |
| **Achievement** | 08 | 3 | 업적 |
| **Operation** | 09 | 2 | 운영 |

---

## 📚 참고 문서

- [06. REST API 명세서](./06-API-spec.md)
- [11. 백엔드 프로젝트 구조 설계서](./11-Backend-Project-Structure.md)

---

## 📝 문서 히스토리

| 버전 | 날짜 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v1.0 | 2026-05-14 | Backend Team | 초기 작성 |

---

**문서 끝**
