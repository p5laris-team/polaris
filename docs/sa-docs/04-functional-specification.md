# 04. 기능 명세서 (Functional Specification)

## 문서 정보

| 항목 | 내용 |
|------|------|
| 문서명 | Polaris MVP 기능 명세서 |
| 작성일 | 2026-05-14 |
| 버전 | v1.0 |
| 목적 | 각 기능의 상세 동작 로직과 규칙을 정의 |
| 대상 독자 | 백엔드 개발자, QA 엔지니어 |

---

## 목차

1. [사용자 인증 및 관리](#1-사용자-인증-및-관리)
2. [캐릭터 시스템](#2-캐릭터-시스템)
3. [온보딩 설문](#3-온보딩-설문)
4. [AI 미션 시스템](#4-ai-미션-시스템)
5. [미션 완료 및 검증](#5-미션-완료-및-검증)
6. [별조각 경제](#6-별조각-경제)
7. [아이템 및 스킨 시스템](#7-아이템-및-스킨-시스템)
8. [알림 시스템](#8-알림-시스템)
9. [SNS 공유 및 바이럴](#9-sns-공유-및-바이럴)
10. [업적 시스템](#10-업적-시스템)
11. [광고 시스템](#11-광고-시스템)

---

## 1. 사용자 인증 및 관리

### 1.1 Google OAuth2 로그인

#### 기능 개요
- Google OAuth2를 통한 소셜 로그인
- 최초 로그인 시 자동 회원가입
- JWT 기반 세션 관리

#### 상세 로직

**Step 1: 프론트엔드에서 Google OAuth 시작**
```
1. 사용자가 "Google로 시작하기" 버튼 클릭
2. 프론트엔드가 Google OAuth URL로 리다이렉트
3. 사용자가 Google 계정 선택 및 권한 동의
4. Google이 authorization code를 콜백 URL로 전달
```

**Step 2: 백엔드 인증 처리**
```
1. 프론트엔드가 authorization code를 백엔드로 전송
2. 백엔드가 Google API로 access token 요청
3. access token으로 Google UserInfo API 호출
4. email, name, profile_image 획득
5. users 테이블에서 email로 사용자 조회
   - 존재하면: 기존 사용자 로그인
   - 없으면: 신규 사용자 생성
6. JWT access token + refresh token 발급
7. 프론트엔드에 토큰 반환
```

#### 데이터 저장
```sql
INSERT INTO users (
  email,
  display_name,
  profile_image_url,
  auth_provider,
  auth_provider_id,
  created_at
) VALUES (
  'user@gmail.com',
  'John Doe',
  'https://lh3.googleusercontent.com/...',
  'GOOGLE',
  'google_user_id_12345',
  NOW()
);
```

#### 예외 처리
| 상황 | 처리 |
|------|------|
| Google API 타임아웃 | 503 에러, "잠시 후 다시 시도해주세요" |
| 유효하지 않은 authorization code | 401 에러, "인증에 실패했습니다" |
| 이미 탈퇴한 계정 | 403 에러, "탈퇴한 계정입니다" |
| DB 저장 실패 | 500 에러, 운영자 알림 |

#### 보안 정책
- JWT access token 유효기간: 1시간
- JWT refresh token 유효기간: 30일
- refresh token은 Redis에 저장 (key: `refresh_token:{user_id}`)
- 로그아웃 시 Redis에서 refresh token 삭제

---

### 1.2 사용자 프로필 조회

#### 기능 개요
- 내 정보 조회
- 보유 별조각, 캐릭터 정보 포함

#### API 응답 예시
```json
{
  "userId": 123,
  "email": "user@gmail.com",
  "displayName": "John Doe",
  "profileImageUrl": "https://...",
  "starPieces": 150,
  "character": {
    "characterId": 456,
    "characterType": "NOVA",
    "characterName": "노바",
    "nickname": "내 노바",
    "level": 1,
    "states": {
      "fullness": 82,
      "energy": 45,
      "affection": 28
    }
  },
  "createdAt": "2026-05-01T10:00:00Z"
}
```

---

## 2. 캐릭터 시스템

### 2.1 캐릭터 선택 및 생성

#### 기능 개요
- 최초 로그인 후 캐릭터 3종 중 1개 선택
- 캐릭터 닉네임 설정
- 캐릭터 생성 후 온보딩 설문 시작

#### 상세 로직

**Step 1: 캐릭터 종류 조회**
```
GET /api/v1/characters/types
```

응답:
```json
{
  "characterTypes": [
    {
      "id": 1,
      "code": "NOVA",
      "name": "노바",
      "summary": "자기가 별이었다는 걸 까먹은 별알",
      "personality": "다정함, 조심스러움, 기억이 듬성듬성함",
      "thumbnailUrl": "https://cdn.polaris.com/characters/nova_thumb.png",
      "introMessage": "노바는 자기가 한때 길을 비추던 별이었다는 걸 까먹은 별알이에요..."
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
}
```

**Step 2: 캐릭터 생성**
```
POST /api/v1/characters
{
  "characterTypeId": 1,
  "nickname": "내 노바"
}
```

처리 로직:
```
1. 사용자가 이미 캐릭터를 보유하고 있는지 확인
   - 있으면: 400 에러 "이미 캐릭터가 있습니다"
2. character_types에서 characterTypeId 조회
   - 없으면: 404 에러
3. user_characters 테이블에 INSERT
   - user_id
   - character_type_id
   - nickname
   - level = 1
   - fullness = 100
   - energy = 100
   - affection = 50
   - created_at
4. 캐릭터 생성 이벤트 발행 (온보딩 설문 시작 트리거)
5. 생성된 캐릭터 정보 반환
```

#### 제약 조건
- 사용자당 캐릭터 1개만 보유 가능 (MVP)
- 닉네임은 1~20자
- 닉네임 중복 허용 (다른 사용자와)
- 캐릭터 생성 후 삭제 불가 (MVP)

---

### 2.2 캐릭터 상태 관리

#### 기능 개요
- 캐릭터는 3가지 상태를 가짐: fullness(포만감), energy(기운), affection(애정)
- 시간 경과에 따라 자동 감소
- 돌봄 액션으로 회복
- 상태에 따라 캐릭터 이미지 변경

#### 상태 수치 정책
| 상태 | 초기값 | 최소값 | 최대값 | 화면 표시 |
|------|--------|--------|--------|-----------|
| fullness | 100 | 0 | 100 | 포만감 / 배고픔 |
| energy | 100 | 0 | 100 | 기운 / 피곤함 |
| affection | 50 | 0 | 100 | 애정 / 쓸쓸함 |

#### 상태 등급 기준
| 등급 | 수치 범위 | fullness | energy | affection |
|------|-----------|----------|--------|-----------|
| GOOD | 70~100 | 든든함 | 말짱함 | 가까움 |
| NORMAL | 40~69 | 출출함 | 졸림 | 조용함 |
| BAD | 0~39 | 배고픔 | 피곤함 | 쓸쓸함 |


#### 상태 자동 감소 정책

**감소 스케줄러 (매 1시간마다 실행)**
```
1. 모든 활성 캐릭터 조회
2. 마지막 상태 업데이트 시간 확인
3. 경과 시간에 따라 상태 감소 계산
4. 상태 업데이트
5. 상태가 BAD 등급이 되면 알림 생성
```

**감소 규칙**
| 상태 | 감소 조건 | 감소량 | 최소값 |
|------|-----------|--------|--------|
| fullness | 6시간마다 | -10 | 0 |
| energy | 8시간마다 | -10 | 0 |
| affection | 24시간마다 (미션/돌봄/접속 없을 때) | -10 | 0 |

**사용자가 오래 접속하지 않은 경우**
```
1. 상태는 0까지 계속 감소
2. 상태가 BAD 등급(0~39)이 되면 푸시 알림 전송
   - "노바가 배고파하고 있어요 🥺"
   - "무무가 쓸쓸해하는 것 같아요..."
3. 상태가 0이 되면:
   - 캐릭터 이미지를 최악 상태 이미지로 변경
   - 특별 알림 전송: "노바가 많이 기다렸어요. 돌아와주세요!"
   - 캐릭터는 사망하지 않음 (MVP에서는 부정적 경험 최소화)
```

#### 상태 회복 정책

**돌봄 액션별 회복량**
| 액션 | 회복 상태 | 회복량 | 비용 |
|------|-----------|--------|------|
| 밥 주기 | fullness | +30 | 별조각 3개 또는 FOOD 아이템 |
| 재우기 | energy | +30 | 무료 (쿨다운 있음) |
| 놀아주기 | affection | +25 | 별조각 2개 또는 TOY 아이템 |

**미션 완료 시 부가 회복**
| 액션 | 회복 상태 | 회복량 |
|------|-----------|--------|
| 미션 완료 | affection | +5 |
| 일일 출석 | affection | +3 |

**회복 제약**
- 상태 수치는 100을 초과할 수 없음
- 별조각이 부족하면 유료 돌봄 액션 실패
- 소모품 사용 시 user_items.quantity가 1 감소

---

### 2.3 돌봄 액션 상세

#### 2.3.1 밥 주기

**API**
```
POST /api/v1/characters/{characterId}/care/feed
{
  "paymentType": "STAR_PIECE" | "ITEM",
  "itemId": 123  // paymentType이 ITEM일 때만
}
```

**처리 로직**
```
1. 캐릭터 소유권 확인
2. paymentType 검증
   - STAR_PIECE: 사용자 별조각 >= 3 확인
   - ITEM: user_items에서 FOOD 타입 아이템 보유 확인
3. fullness 현재값 조회
4. fullness가 이미 100이면: 400 에러 "이미 배부른 상태입니다"
5. 트랜잭션 시작:
   a. fullness += 30 (최대 100)
   b. 별조각 차감 또는 아이템 수량 -1
   c. character_care_logs 기록
   d. star_piece_transactions 기록 (별조각 사용 시)
6. 트랜잭션 커밋
7. 캐릭터 반응 메시지 생성
8. 상태 변경에 따른 이미지 URL 갱신
```

**쿨다운 정책**
- fullness가 100이 될 때까지 밥 주기 가능
- 쿨다운 없음 (별조각/아이템 소비가 제한 역할)

**캐릭터별 반응 메시지**
| 캐릭터 | 반응 |
|--------|------|
| 노바 | "먹는 중… 빛도 맛이 있구나." |
| 무무 | "무." (해석: 무무가 별빛을 먹은 것 같아요.) |
| 쪼리 | "원정 전 보급 완료." |

---

#### 2.3.2 재우기

**API**
```
POST /api/v1/characters/{characterId}/care/sleep
{
  "paymentType": "FREE" | "ITEM",
  "itemId": 123  // paymentType이 ITEM일 때만
}
```

**처리 로직**
```
1. 캐릭터 소유권 확인
2. energy 현재값 조회
3. energy가 이미 100이면: 400 에러 "이미 말짱한 상태입니다"
4. paymentType이 FREE인 경우:
   - Redis에서 쿨다운 확인: sleep_cooldown:{characterId}
   - 쿨다운 중이면: 400 에러 "아직 재울 수 없습니다 (남은 시간: X분)"
5. 트랜잭션 시작:
   a. energy += 30 (최대 100)
   b. 아이템 사용 시 수량 -1
   c. character_care_logs 기록
6. paymentType이 FREE인 경우:
   - Redis에 쿨다운 설정: sleep_cooldown:{characterId} = true, TTL = energy가 100이 될 때까지의 예상 시간
7. 트랜잭션 커밋
8. 캐릭터 반응 메시지 생성
```

**쿨다운 정책**
- 무료 재우기: energy가 100이 될 때까지 재사용 불가
- 아이템 사용: 쿨다운 없음

**캐릭터별 반응 메시지**
| 캐릭터 | 반응 |
|--------|------|
| 노바 | "나 먼저 잘게. 꿈에서 별 좀 주워올게." |
| 무무 | "무우…" (해석: 무무가 뿌리부터 쉬는 중이에요.) |
| 쪼리 | "철수 아님. 전략적 휴식임." |

---

#### 2.3.3 놀아주기

**API**
```
POST /api/v1/characters/{characterId}/care/play
{
  "paymentType": "STAR_PIECE" | "ITEM",
  "itemId": 123  // paymentType이 ITEM일 때만
}
```

**처리 로직**
```
1. 캐릭터 소유권 확인
2. paymentType 검증
   - STAR_PIECE: 사용자 별조각 >= 2 확인
   - ITEM: user_items에서 TOY 타입 아이템 보유 확인
3. affection 현재값 조회
4. affection이 이미 100이면: 400 에러 "이미 충분히 가까운 상태입니다"
5. 트랜잭션 시작:
   a. affection += 25 (최대 100)
   b. 별조각 차감 또는 아이템 수량 -1
   c. character_care_logs 기록
   d. star_piece_transactions 기록 (별조각 사용 시)
6. 트랜잭션 커밋
7. 캐릭터 반응 메시지 생성
```

**쿨다운 정책**
- affection이 100이 될 때까지 놀아주기 가능
- 쿨다운 없음

**캐릭터별 반응 메시지**
| 캐릭터 | 반응 |
|--------|------|
| 노바 | "나 굴러가도 잡아줄 거야?" |
| 무무 | "무!" (해석: 무무가 생각보다 신났어요. 티는 안 나지만요.) |
| 쪼리 | "훈련임. 놀이 아님. 아무튼 아님." |

---

### 2.4 캐릭터 이미지 변경 로직

#### 이미지 우선순위
캐릭터의 현재 이미지는 다음 우선순위로 결정됩니다:

```
1. 사용자가 방금 실행한 ACTION 이미지 (5초간 표시)
   - FEED, SLEEP, PLAY
2. affection BAD 이미지 (0~39)
3. energy BAD 이미지 (0~39)
4. fullness BAD 이미지 (0~39)
5. IDLE 이미지 (기본 상태)
```

#### 이미지 조회 API
```
GET /api/v1/characters/{characterId}/current-image
```

응답:
```json
{
  "imageUrl": "https://cdn.polaris.com/characters/nova_idle.png",
  "assetType": "IDLE",
  "assetKey": "IDLE",
  "width": 512,
  "height": 512
}
```

#### 이미지 결정 로직
```java
public CharacterAsset getCurrentImage(UserCharacter character) {
    // 1. 최근 5초 이내 액션이 있으면 액션 이미지
    if (hasRecentAction(character, 5)) {
        return getActionImage(character);
    }
    
    // 2. affection BAD
    if (character.getAffection() < 40) {
        return getAsset(character, "AFFECTION_BAD");
    }
    
    // 3. energy BAD
    if (character.getEnergy() < 40) {
        return getAsset(character, "ENERGY_BAD");
    }
    
    // 4. fullness BAD
    if (character.getFullness() < 40) {
        return getAsset(character, "FULLNESS_BAD");
    }
    
    // 5. IDLE
    return getAsset(character, "IDLE");
}
```

---

## 3. 온보딩 설문

### 3.1 설문 개요

#### 목적
- 사용자의 생활 맥락 파악
- AI 미션 개인화 데이터 수집
- 캐릭터와의 첫 대화 경험 제공

#### 설문 정책
- 캐릭터 생성 직후 자동 시작
- 총 9문항 (필수 7문항, 선택 2문항)
- 객관식 선택지 중심
- 설문 완료 후 첫 미션 제안 가능
- 설문 중단 가능하지만 미션 정확도 낮아짐

---

### 3.2 설문 문항 정의

#### Q1. 생활 환경 (필수)
```json
{
  "questionId": "Q1",
  "questionText": {
    "NOVA": "너의 하루는 보통 어디서 시작돼?",
    "MUMU": "무... 집?",
    "JJORY": "기지 형태 확인 필요."
  },
  "questionType": "SINGLE_CHOICE",
  "required": true,
  "options": [
    { "value": "LIVING_ALONE", "label": "혼자 살고 있어요" },
    { "value": "WITH_FAMILY", "label": "가족과 함께 살고 있어요" },
    { "value": "WITH_ROOMMATE", "label": "룸메이트/동거인과 살고 있어요" },
    { "value": "OTHER", "label": "그때그때 달라요" }
  ]
}
```

#### Q2. 기상 시간 (필수)
```json
{
  "questionId": "Q2",
  "questionText": {
    "NOVA": "보통 몇 시쯤 하루가 시작돼?",
    "MUMU": "무... 아침?",
    "JJORY": "기상 시간 보고 바람."
  },
  "questionType": "SINGLE_CHOICE",
  "required": true,
  "options": [
    { "value": "BEFORE_7", "label": "7시 이전" },
    { "value": "BETWEEN_7_9", "label": "7시~9시" },
    { "value": "BETWEEN_9_12", "label": "9시~12시" },
    { "value": "AFTER_12", "label": "12시 이후" },
    { "value": "IRREGULAR", "label": "매일 달라요" }
  ]
}
```

#### Q3. 취침 시간 (필수)
```json
{
  "questionId": "Q3",
  "questionText": {
    "NOVA": "밤은 언제쯤 끝나?",
    "MUMU": "무... 잠?",
    "JJORY": "취침 시간 기록 필요."
  },
  "questionType": "SINGLE_CHOICE",
  "required": true,
  "options": [
    { "value": "BEFORE_23", "label": "23시 이전" },
    { "value": "BETWEEN_23_1", "label": "23시~1시" },
    { "value": "AFTER_1", "label": "1시 이후" },
    { "value": "IRREGULAR", "label": "매일 달라요" }
  ]
}
```

#### Q4. 미션 강도 (필수)
```json
{
  "questionId": "Q4",
  "questionText": {
    "NOVA": "어느 정도 크기의 별을 찾아줄까?",
    "MUMU": "무... 크기?",
    "JJORY": "미션 난이도 설정 바람."
  },
  "questionType": "SINGLE_CHOICE",
  "required": true,
  "options": [
    { "value": "VERY_LIGHT", "label": "진짜 아주 작은 것부터" },
    { "value": "LIGHT", "label": "5분 안에 할 수 있는 것" },
    { "value": "NORMAL", "label": "10분 정도는 괜찮아요" },
    { "value": "CHALLENGE", "label": "조금 도전적인 것도 가능해요" }
  ]
}
```

#### Q5. 가장 부담되는 미션 유형 (필수)
```json
{
  "questionId": "Q5",
  "questionText": {
    "NOVA": "어떤 게 제일 부담스러워?",
    "MUMU": "무... 싫은 거?",
    "JJORY": "회피 대상 확인 필요."
  },
  "questionType": "SINGLE_CHOICE",
  "required": true,
  "options": [
    { "value": "OUTDOOR", "label": "밖에 나가는 것" },
    { "value": "CLEANING", "label": "정리/청소" },
    { "value": "BODY_CARE", "label": "몸을 움직이는 것" },
    { "value": "RECORDING", "label": "글로 기록하는 것" },
    { "value": "SOCIAL", "label": "누군가에게 연락하는 것" },
    { "value": "NONE", "label": "특별히 없어요" }
  ]
}
```

#### Q6. 받고 싶은 도움 (필수)
```json
{
  "questionId": "Q6",
  "questionText": {
    "NOVA": "내가 어떤 도움을 줄 수 있을까?",
    "MUMU": "무... 도움?",
    "JJORY": "지원 항목 선택 바람."
  },
  "questionType": "SINGLE_CHOICE",
  "required": true,
  "options": [
    { "value": "START_DAY", "label": "하루를 시작하는 도움" },
    { "value": "GO_OUTSIDE", "label": "집 밖으로 나가는 계기" },
    { "value": "CLEAN_SPACE", "label": "공간을 정리하는 계기" },
    { "value": "TAKE_CARE", "label": "나를 돌보는 계기" },
    { "value": "RECORD_MOOD", "label": "기분을 기록하는 계기" },
    { "value": "JUST_TALK", "label": "그냥 말 걸어주는 느낌" }
  ]
}
```

#### Q7. 알림 선호 시간 (필수)
```json
{
  "questionId": "Q7",
  "questionText": {
    "NOVA": "언제 말 걸어주면 좋을까?",
    "MUMU": "무... 시간?",
    "JJORY": "알림 시간대 설정 바람."
  },
  "questionType": "SINGLE_CHOICE",
  "required": true,
  "options": [
    { "value": "MORNING", "label": "오전" },
    { "value": "AFTERNOON", "label": "오후" },
    { "value": "EVENING", "label": "저녁" },
    { "value": "NIGHT", "label": "밤" },
    { "value": "NONE", "label": "알림은 받고 싶지 않아요" }
  ]
}
```

#### Q8. 지역/날씨 활용 (선택)
```json
{
  "questionId": "Q8",
  "questionText": {
    "NOVA": "날씨에 맞춰 미션을 골라줘도 될까?",
    "MUMU": "무... 날씨?",
    "JJORY": "날씨 데이터 활용 여부 확인."
  },
  "questionType": "SINGLE_CHOICE",
  "required": false,
  "options": [
    { "value": "REGION_ONLY", "label": "지역만 선택해서 날씨를 반영해줘요" },
    { "value": "NO_WEATHER", "label": "날씨는 반영하지 말아줘요" }
  ]
}
```

#### Q9. 말투 선호 (선택)
```json
{
  "questionId": "Q9",
  "questionText": {
    "NOVA": "어떻게 말하면 좋을까?",
    "MUMU": "무... 말투?",
    "JJORY": "대화 스타일 설정 바람."
  },
  "questionType": "SINGLE_CHOICE",
  "required": false,
  "options": [
    { "value": "GENTLE", "label": "다정하게 말해줘" },
    { "value": "FUNNY", "label": "좀 웃기게 말해줘" },
    { "value": "SHORT", "label": "짧게 말해줘" },
    { "value": "HONEST", "label": "솔직하게 말해줘" }
  ]
}
```

---

### 3.3 설문 API

#### 설문 시작
```
POST /api/v1/onboarding/surveys/start
{
  "characterId": 123
}
```

응답:
```json
{
  "surveySessionId": "uuid-1234",
  "questions": [ /* 9개 문항 */ ],
  "totalQuestions": 9,
  "requiredQuestions": 7
}
```

#### 설문 답변 제출
```
POST /api/v1/onboarding/surveys/{surveySessionId}/answers
{
  "answers": [
    { "questionId": "Q1", "value": "LIVING_ALONE" },
    { "questionId": "Q2", "value": "BETWEEN_7_9" },
    // ...
  ]
}
```

처리 로직:
```
1. surveySessionId 검증
2. 필수 문항 답변 여부 확인
3. user_profiles 테이블에 답변 저장 (JSONB)
4. survey_completed = true 설정
5. 첫 미션 생성 트리거
```

---

## 4. AI 미션 시스템

### 4.1 미션 생성 개요

#### 생성 방식
MVP에서는 **seed 미션 템플릿 + 점수 기반 선정 + AI 문구 생성** 방식을 사용합니다.

```
1. mission_templates에서 활성 미션 조회
2. 사용자 컨텍스트 기반 후보 필터링
3. 점수 계산 (규칙 기반 + 시간 감쇠)
4. 상위 후보 중 weighted random 선정
5. AI가 캐릭터 말투로 문구 생성
6. 생성 실패 시 fallback 문구 사용
7. user_missions에 저장
```

#### 미션 제공 정책
- 미션은 한 번에 1개씩 제공
- 하루 최대 15개 제안
- 권장 완료 수: 3~5개/일
- 자정(00:00 KST)에 당일 미션 만료

---

### 4.2 미션 상태 전이

```
GENERATED → OFFERED → REJECTED
                    → ANSWERING → COMPLETED
                                → EXPIRED
```

| 상태 | 설명 |
|------|------|
| GENERATED | AI가 생성했지만 아직 노출 안 됨 |
| OFFERED | 현재 사용자에게 제안된 상태 |
| REJECTED | 사용자가 거절 |
| ANSWERING | 완료 후 질문에 답변 중 |
| COMPLETED | 답변 완료 및 보상 지급 완료 |
| EXPIRED | 날짜 변경 또는 제한 초과로 만료 |

---

### 4.3 미션 추천 알고리즘

#### Step 1: 후보 필터링
```sql
SELECT * FROM mission_templates
WHERE active = true
  AND id NOT IN (
    -- 오늘 이미 완료한 미션
    SELECT mission_template_id FROM user_missions
    WHERE user_id = ? AND status = 'COMPLETED'
      AND DATE(completed_at) = CURRENT_DATE
  )
  AND id NOT IN (
    -- 오늘 이미 거절한 미션
    SELECT mission_template_id FROM user_missions
    WHERE user_id = ? AND status = 'REJECTED'
      AND DATE(rejected_at) = CURRENT_DATE
  )
  AND id NOT IN (
    -- 최근 3회 연속 제안된 미션
    SELECT mission_template_id FROM user_missions
    WHERE user_id = ? AND status = 'OFFERED'
    ORDER BY offered_at DESC LIMIT 3
  )
```

#### Step 2: 점수 계산
```
score = baseScore
      + goalScore
      + intensityScore
      + timeScore
      + weatherScore
      + stateScore
      + historyScore
      + explorationScore
      - burdenPenalty
      - recentRepeatPenalty
```

**점수 항목 상세**

| 항목 | 점수 | 조건 |
|------|------|------|
| baseScore | +1 | 모든 활성 미션 |
| goalScore | +3 | 온보딩 Q6 답변과 미션 카테고리 일치 |
| intensityScore | +2 | 온보딩 Q4 답변과 미션 난이도 일치 |
| timeScore | +2 | 현재 시간대와 미션 적합성 일치 |
| weatherScore | +2 / -3 | 날씨 좋음/나쁨 |
| stateScore | +1 | 캐릭터 상태와 연결 (예: fullness 낮으면 BODY_CARE 미션) |
| burdenPenalty | -4 | 온보딩 Q5에서 부담스럽다고 답한 유형 |
| recentRepeatPenalty | -5 | 최근 3회 내 동일 카테고리 제안 |
| explorationScore | +0~1 | 가끔 새로운 카테고리 제안 (랜덤) |


#### Step 3: 시간 감쇠 (반감기) 적용

과거 데이터의 영향력을 시간에 따라 감소시킵니다.

**감쇠 공식**
```
decayWeight = 0.5 ^ (daysAgo / halfLifeDays)
```

**반감기 설정**
| 데이터 유형 | halfLifeDays | 이유 |
|-------------|--------------|------|
| 거절 이력 | 7일 | 싫었던 미션도 시간이 지나면 재시도 가능 |
| 완료 이력 | 14일 | 잘 수행한 미션은 오래 긍정 신호 유지 |
| 알림 클릭 | 14일 | 시간대 선호는 2주 정도 유지 |
| 상태 기반 선호 | 3일 | 캐릭터 상태는 빠르게 변함 |

**historyScore 계산**
```java
double historyScore = 0;

// 완료 이력 가산
for (CompletedMission m : completedMissions) {
    int daysAgo = getDaysAgo(m.completedAt);
    double weight = Math.pow(0.5, daysAgo / 14.0);
    if (m.category == candidate.category) {
        historyScore += 1.5 * weight;
    }
}

// 거절 이력 감산
for (RejectedMission m : rejectedMissions) {
    int daysAgo = getDaysAgo(m.rejectedAt);
    double weight = Math.pow(0.5, daysAgo / 7.0);
    if (m.category == candidate.category) {
        historyScore -= 3.0 * weight;
    }
}
```

**예시**
```
사용자가 30일 전에 OUTDOOR_LIGHT를 거절:
penalty = -3 * 0.5^(30/7) ≈ -0.15 (거의 영향 없음)

사용자가 3일 전에 SPACE_RESET을 완료:
bonus = +1.5 * 0.5^(3/14) ≈ +1.29 (여전히 높은 가산점)
```

---

#### Step 4: Weighted Random 선정

```java
// 상위 3개 후보 선정
List<MissionCandidate> topCandidates = candidates.stream()
    .sorted(Comparator.comparingDouble(MissionCandidate::getScore).reversed())
    .limit(3)
    .collect(Collectors.toList());

// 점수 비례 확률로 1개 선택
double totalScore = topCandidates.stream()
    .mapToDouble(MissionCandidate::getScore)
    .sum();

double random = Math.random() * totalScore;
double cumulative = 0;

for (MissionCandidate candidate : topCandidates) {
    cumulative += candidate.getScore();
    if (random <= cumulative) {
        return candidate;
    }
}
```

---

### 4.4 AI 문구 생성

#### 생성 대상
1. `character_message`: 캐릭터가 미션을 제안하는 메시지
2. `completion_question`: 완료 후 질문
3. `completion_response`: 완료 후 캐릭터 반응

#### AI 입력 컨텍스트
```json
{
  "missionTemplate": {
    "title": "물 한 컵 마시기",
    "category": "BASIC_ROUTINE",
    "estimatedMinutes": 1,
    "defaultMessage": "물 한 컵 마셔볼래?"
  },
  "character": {
    "type": "NOVA",
    "personality": "다정함, 조심스러움",
    "speechStyle": "짧고 느림, 문장 끝이 작게 흐려짐"
  },
  "userContext": {
    "surveyAnswers": {
      "preferredGoal": "START_DAY",
      "burdenType": "OUTDOOR"
    },
    "recentCompletions": ["창문 열기", "스트레칭"],
    "recentRejections": ["산책하기"],
    "timeOfDay": "MORNING",
    "weather": "맑음"
  }
}
```

#### AI 프롬프트 (간략)
```
당신은 {character.type} 캐릭터입니다.
성격: {character.personality}
말투: {character.speechStyle}

사용자에게 "{missionTemplate.title}" 미션을 제안하세요.
- character_message: 100자 이하, 캐릭터 말투 반영
- completion_question: 100자 이하, 미션 관련 질문
- completion_response: 80자 이하, 완료 축하 메시지

금지 표현: 비난, 낙인, 강요, 부정적 표현
```

#### AI 응답 검증
```java
public boolean validateAIResponse(AIResponse response) {
    // 길이 검증
    if (response.characterMessage.length() > 100) return false;
    if (response.completionQuestion.length() > 100) return false;
    if (response.completionResponse.length() > 80) return false;
    
    // 금지 표현 검증
    List<String> bannedWords = List.of("게으름", "나태", "실패", "못함");
    for (String word : bannedWords) {
        if (response.characterMessage.contains(word)) return false;
    }
    
    return true;
}
```

---

### 4.5 Fallback 시드 문구

AI 생성 실패 시 사용할 fallback 문구를 카테고리별로 준비합니다.

#### BASIC_ROUTINE (기본 루틴)
| 미션 | 노바 | 무무 | 쪼리 |
|------|------|------|------|
| 물 한 컵 마시기 | "물 한 컵 마셔볼래? 나도 빛 좀 마셔볼게." | "무... 물." | "수분 보급 권장." |
| 양치하기 | "이 닦으면 별이 하나 생겨." | "무... 치카." | "구강 위생 체크 완료." |
| 세수하기 | "얼굴 씻으면 조금 밝아질 거야." | "무... 물." | "세안 미션. 간단함." |

#### SPACE_RESET (공간 정리)
| 미션 | 노바 | 무무 | 쪼리 |
|------|------|------|------|
| 창문 3분 열기 | "창문을 조금 열면... 오늘 공기도 별이 될 수 있어." | "무... 바람." | "환기 작전 개시." |
| 책상 한 칸 정리 | "작은 공간 하나만 치워도 별빛이 들어와." | "무... 정리." | "책상 정복 시작." |

#### OUTDOOR_LIGHT (가벼운 외출)
| 미션 | 노바 | 무무 | 쪼리 |
|------|------|------|------|
| 현관까지 가기 | "멀리 안 가도 돼. 현관까지만." | "무... 밖?" | "현관까지 가면 세계여행임. 반박 안 받음." |
| 하늘 보기 | "오늘 하늘 색깔 한 번만 봐줄래?" | "무... 하늘." | "하늘 정찰 임무." |

#### BODY_CARE (몸 돌보기)
| 미션 | 노바 | 무무 | 쪼리 |
|------|------|------|------|
| 스트레칭 10초 | "몸을 조금만 펴도 별이 움직여." | "무... 쭉." | "근육 점검 필요." |
| 눈 감고 1분 쉬기 | "눈 감고 있으면 별이 보일지도 몰라." | "무..." | "전략적 휴식 권장." |

#### MIND_RECORD (기록/감정)
| 미션 | 노바 | 무무 | 쪼리 |
|------|------|------|------|
| 오늘 기분 한 단어 | "오늘 기분을 별 하나로 남겨볼래?" | "무... 기분?" | "감정 로그 기록 바람." |
| 좋았던 것 하나 적기 | "오늘 작은 빛 하나만 기억해줘." | "무... 좋은 거." | "긍정 요소 1개 보고." |

**Fallback 사용 조건**
- AI API 타임아웃 (5초 초과)
- AI API rate limit 초과
- JSON 파싱 실패
- 검증 실패 (길이 초과, 금지 표현 포함)

**Fallback 사용 시 처리**
```java
if (aiGenerationFailed) {
    mission.setCharacterMessage(getFallbackMessage(template, character));
    mission.setCompletionQuestion(getFallbackQuestion(template));
    mission.setFallbackUsed(true);
    
    // 운영자 알림
    notifyOperator("AI 미션 생성 실패", missionId, errorReason);
    
    // 즉시 재시도 (최대 1회)
    if (retryCount < 1) {
        retryAIGeneration(mission);
    }
}
```


### 4.6 미션 품질 검증

AI가 생성한 미션은 다음 검증을 거칩니다.

#### 검증 항목
| 검증 | 기준 | 실패 시 처리 |
|------|------|--------------|
| 길이 검증 | character_message ≤ 100자 | Fallback 사용 |
| 길이 검증 | completion_question ≤ 100자 | Fallback 사용 |
| 길이 검증 | completion_response ≤ 80자 | Fallback 사용 |
| 금지 표현 | 비난/낙인 단어 없음 | Fallback 사용 |
| 미션 일치성 | 원본 미션 제목/카테고리 변경 안 됨 | Fallback 사용 |
| 보상 변경 | 보상 금액 임의 변경 안 됨 | Fallback 사용 |

#### 금지 표현 목록
```java
private static final List<String> BANNED_WORDS = List.of(
    "게으름", "나태", "실패", "못함", "안 함",
    "포기", "의지박약", "무능", "쓸모없",
    "해야만", "반드시", "꼭", "의무"
);
```

#### 검증 로직
```java
public ValidationResult validateMission(GeneratedMission mission) {
    List<String> errors = new ArrayList<>();
    
    // 길이 검증
    if (mission.getCharacterMessage().length() > 100) {
        errors.add("character_message 길이 초과");
    }
    
    // 금지 표현 검증
    for (String word : BANNED_WORDS) {
        if (mission.getCharacterMessage().contains(word)) {
            errors.add("금지 표현 포함: " + word);
        }
    }
    
    // 미션 일치성 검증
    if (!mission.getTitle().equals(template.getTitle())) {
        errors.add("미션 제목 변경됨");
    }
    
    if (errors.isEmpty()) {
        return ValidationResult.success();
    } else {
        return ValidationResult.failure(errors);
    }
}
```

---

### 4.7 미션 생성 실패 처리

#### 재시도 정책
```
1차 생성 실패 → 즉시 재시도 (1회)
2차 생성 실패 → Fallback 문구 사용
Fallback 사용 → 운영자 알림 전송
```

#### 운영자 알림 내용
```json
{
  "alertType": "AI_MISSION_GENERATION_FAILED",
  "severity": "WARNING",
  "userId": 123,
  "characterId": 456,
  "missionTemplateId": 789,
  "errorReason": "AI API timeout",
  "retryCount": 1,
  "fallbackUsed": true,
  "timestamp": "2026-05-14T10:30:00Z"
}
```

#### 알림 전송 채널
- Slack webhook (개발팀 채널)
- CloudWatch Logs (검색 가능하도록)
- 운영 대시보드 (실시간 모니터링)

---

### 4.8 미션 제안 API

#### 현재 미션 조회
```
GET /api/v1/missions/current
```

응답:
```json
{
  "mission": {
    "missionId": 12345,
    "title": "물 한 컵 마시기",
    "category": "BASIC_ROUTINE",
    "characterMessage": "물 한 컵 마셔볼래? 나도 빛 좀 마셔볼게.",
    "estimatedMinutes": 1,
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
}
```

#### 미션이 없는 경우
```json
{
  "mission": null,
  "reason": "DAILY_LIMIT_REACHED",
  "message": "오늘은 충분히 별을 모았어요. 내일 또 만나요!",
  "todayStats": {
    "offeredCount": 15,
    "completedCount": 5,
    "rejectedCount": 10,
    "remainingOffers": 0
  }
}
```

---

### 4.9 미션 거절

#### API
```
POST /api/v1/missions/{missionId}/reject
{
  "reason": "TOO_LAZY" | "OUTDOOR_BURDEN" | "TOO_HARD" | "ALREADY_DONE" | "NOT_INTERESTED" | "OTHER",
  "comment": "지금은 밖에 나가기 싫어요"  // optional
}
```

#### 처리 로직
```
1. 미션 상태 확인 (OFFERED 상태만 거절 가능)
2. 미션 상태를 REJECTED로 변경
3. rejected_at, rejection_reason 저장
4. 하루 제안 수 차감 (offered_count는 유지)
5. 다음 미션 생성 트리거
6. 캐릭터 반응 메시지 반환
```

#### 거절 사유 코드
| 코드 | 의미 | 가중치 |
|------|------|--------|
| TOO_LAZY | 너무 귀찮아요 | -2 |
| OUTDOOR_BURDEN | 지금은 밖에 나가기 싫어요 | -4 (OUTDOOR 카테고리) |
| TOO_HARD | 너무 어려워요 | -3 |
| ALREADY_DONE | 이미 했어요 | -1 |
| NOT_INTERESTED | 마음에 안 들어요 | -2 |
| OTHER | 다른 이유 | -1 |

#### 캐릭터 반응
```json
{
  "characterResponse": {
    "NOVA": "괜찮아. 그럼 다른 별 찾아볼게.",
    "MUMU": "무. (해석: 무무가 알겠다고 하는 것 같아요.)",
    "JJORY": "후퇴도 전략임. 나 자주 함."
  }
}
```

---

## 5. 미션 완료 및 검증

### 5.1 완료 흐름

```
1. 사용자가 "완료" 버튼 클릭
2. 미션 상태 OFFERED → ANSWERING
3. 캐릭터가 질문 1개 제시
4. 사용자가 텍스트 답변 (1~300자)
5. 답변 저장
6. 미션 상태 ANSWERING → COMPLETED
7. 별조각 지급
8. affection +5
9. 업적 진행도 갱신
10. 캐릭터 반응 메시지 표시
```

---

### 5.2 완료 시작 API

```
POST /api/v1/missions/{missionId}/complete
```

응답:
```json
{
  "missionId": 12345,
  "status": "ANSWERING",
  "completionQuestion": "방금 한 일에서 제일 기억나는 건 뭐였어?",
  "answerConstraints": {
    "minLength": 1,
    "maxLength": 300,
    "inputType": "TEXT"
  }
}
```

---

### 5.3 완료 답변 제출 API

```
POST /api/v1/missions/{missionId}/answer
{
  "answer": "물을 마시니까 목이 시원했어요"
}
```

#### 처리 로직
```
1. 미션 상태 확인 (ANSWERING 상태만 답변 가능)
2. 답변 길이 검증 (1~300자)
3. 욕설/비속어 필터링
4. 트랜잭션 시작:
   a. 답변 저장 (completion_answer)
   b. 미션 상태 COMPLETED
   c. completed_at 기록
   d. 별조각 지급 (star_piece_transactions)
   e. 캐릭터 affection +5
   f. 업적 진행도 갱신
5. 트랜잭션 커밋
6. 캐릭터 반응 메시지 반환
```

#### 응답
```json
{
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
  }
}
```

---

### 5.4 완료 질문 생성

#### 질문 유형별 예시

**외출/산책 미션**
```
"오늘 밖에서 제일 먼저 본 건 뭐였어?"
"나가보니까 어땠어?"
"밖에서 어떤 소리가 들렸어?"
```

**하늘 보기 미션**
```
"오늘 하늘 색깔은 어땠어?"
"구름은 어떤 모양이었어?"
"하늘을 보고 뭐가 떠올랐어?"
```

**물 마시기 미션**
```
"마시고 나서 조금 달라진 게 있어?"
"물 온도는 어땠어?"
"몇 모금 마셨어?"
```

**창문 열기 미션**
```
"창문을 열었을 때 어떤 소리가 들렸어?"
"밖 공기는 어땠어?"
"창문 너머로 뭐가 보였어?"
```

**정리 미션**
```
"어떤 물건을 치웠어?"
"정리하고 나니까 어때?"
"제일 먼저 뭘 치웠어?"
```

**기분 기록 미션**
```
"오늘 기분을 한 단어로 적으면 뭐야?"
"지금 기분은 몇 점이야?"
"오늘 제일 좋았던 순간은 언제였어?"
```

---

### 5.5 답변 검증

#### 욕설/비속어 필터링
```java
private static final List<String> PROFANITY_LIST = List.of(
    // 실제 운영 시 확장
    "욕설1", "욕설2", "비속어1"
);

public boolean containsProfanity(String answer) {
    String normalized = answer.toLowerCase().replaceAll("\\s", "");
    for (String word : PROFANITY_LIST) {
        if (normalized.contains(word)) {
            return true;
        }
    }
    return false;
}
```

#### 검증 실패 응답
```json
{
  "error": "INVALID_ANSWER",
  "message": "적절하지 않은 표현이 포함되어 있어요",
  "code": "PROFANITY_DETECTED"
}
```

---

## 6. 별조각 경제

### 6.1 별조각 정책

#### 기본 규칙
- 별조각은 무료 재화 (MVP에서 구매 불가)
- 최대 보유량 제한 없음
- 음수 불가 (부족 시 거래 실패)
- 모든 증감은 star_piece_transactions에 기록

---

### 6.2 별조각 획득처

| 획득처 | 보상 | 제한 | 비고 |
|--------|------|------|------|
| 미션 완료 | 10 | 미션당 1회 | 난이도 무관 동일 보상 |
| SNS 공유 | 10 | 하루 1회 | 자정(00:00 KST) 기준 리셋 |
| 일일 출석 | 3 | 하루 1회 | 자정 기준 |
| 일일 업적 | 5~20 | 업적당 1회 | 예: 미션 3개 완료 |
| 주간 업적 | 20~50 | 업적당 1회 | 예: 주간 미션 10개 완료 |
| 운영 이벤트 | 가변 | 이벤트별 | 수동 지급 |

---

### 6.3 별조각 사용처

| 사용처 | 비용 | 비고 |
|--------|------|------|
| 스킨 구매 | 50~100 | 1회 구매 후 영구 소유 |
| 배경 구매 | 50~100 | 1회 구매 후 영구 소유 |
| 밥 주기 (FOOD 아이템) | 3 | 소모품 |
| 놀아주기 (TOY 아이템) | 2 | 소모품 |
| 특별 아이템 | 10~30 | 이벤트 아이템 |

---

### 6.4 별조각 거래 API

#### 잔액 조회
```
GET /api/v1/star-pieces/balance
```

응답:
```json
{
  "balance": 150,
  "lastUpdated": "2026-05-14T10:30:00Z"
}
```

#### 거래 내역 조회
```
GET /api/v1/star-pieces/transactions?page=0&size=20
```

응답:
```json
{
  "transactions": [
    {
      "transactionId": 1001,
      "type": "EARN",
      "source": "MISSION_COMPLETION",
      "amount": 10,
      "balance": 150,
      "description": "미션 완료: 물 한 컵 마시기",
      "createdAt": "2026-05-14T10:30:00Z"
    },
    {
      "transactionId": 1000,
      "type": "SPEND",
      "source": "ITEM_PURCHASE",
      "amount": -50,
      "balance": 140,
      "description": "스킨 구매: 노바 봄 스킨",
      "createdAt": "2026-05-14T09:00:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3
  }
}
```

---

### 6.5 별조각 지급 로직

```java
@Transactional
public void earnStarPieces(Long userId, int amount, String source, String description) {
    // 1. 사용자 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException());
    
    // 2. 잔액 증가
    user.setStarPieces(user.getStarPieces() + amount);
    
    // 3. 거래 기록
    StarPieceTransaction transaction = StarPieceTransaction.builder()
        .userId(userId)
        .type(TransactionType.EARN)
        .source(source)
        .amount(amount)
        .balanceAfter(user.getStarPieces())
        .description(description)
        .createdAt(LocalDateTime.now())
        .build();
    
    transactionRepository.save(transaction);
    
    // 4. 사용자 저장
    userRepository.save(user);
}
```

---

### 6.6 별조각 차감 로직

```java
@Transactional
public void spendStarPieces(Long userId, int amount, String source, String description) {
    // 1. 사용자 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException());
    
    // 2. 잔액 확인
    if (user.getStarPieces() < amount) {
        throw new InsufficientStarPiecesException(
            "별조각이 부족합니다. 필요: " + amount + ", 보유: " + user.getStarPieces()
        );
    }
    
    // 3. 잔액 감소
    user.setStarPieces(user.getStarPieces() - amount);
    
    // 4. 거래 기록
    StarPieceTransaction transaction = StarPieceTransaction.builder()
        .userId(userId)
        .type(TransactionType.SPEND)
        .source(source)
        .amount(-amount)  // 음수로 기록
        .balanceAfter(user.getStarPieces())
        .description(description)
        .createdAt(LocalDateTime.now())
        .build();
    
    transactionRepository.save(transaction);
    
    // 5. 사용자 저장
    userRepository.save(user);
}
```

---

### 6.7 중복 지급 방지

#### 미션 완료 보상
```java
@Transactional
public void rewardMissionCompletion(Long missionId) {
    // 1. 미션 조회
    UserMission mission = missionRepository.findById(missionId)
        .orElseThrow();
    
    // 2. 이미 보상 지급되었는지 확인
    if (mission.isRewardPaid()) {
        throw new AlreadyRewardedException("이미 보상이 지급된 미션입니다");
    }
    
    // 3. 별조각 지급
    earnStarPieces(
        mission.getUserId(),
        mission.getRewardStarPieces(),
        "MISSION_COMPLETION",
        "미션 완료: " + mission.getTitle()
    );
    
    // 4. 보상 지급 플래그 설정
    mission.setRewardPaid(true);
    missionRepository.save(mission);
}
```

#### SNS 공유 보상 (하루 1회)
```java
@Transactional
public void rewardShare(Long userId) {
    // 1. 오늘 이미 공유 보상을 받았는지 확인
    LocalDate today = LocalDate.now();
    boolean alreadyRewarded = transactionRepository.existsByUserIdAndSourceAndDate(
        userId,
        "SHARE_REWARD",
        today
    );
    
    if (alreadyRewarded) {
        throw new DailyLimitExceededException("오늘은 이미 공유 보상을 받았습니다");
    }
    
    // 2. 별조각 지급
    earnStarPieces(userId, 10, "SHARE_REWARD", "캐릭터 카드 공유");
}
```


## 7. 아이템 및 스킨 시스템

### 7.1 아이템 유형

| 유형 | 설명 | 특징 | MVP 포함 |
|------|------|------|----------|
| SKIN | 캐릭터 외형 스킨 | 1회 구매 후 영구 소유, 장착/해제 가능 | ✅ |
| BACKGROUND | 홈 화면 배경 | 1회 구매 후 영구 소유, 장착/해제 가능 | ✅ |
| CONSUMABLE_FOOD | 밥 주기 아이템 | 소모품, 사용 시 수량 -1 | ✅ |
| CONSUMABLE_TOY | 놀아주기 아이템 | 소모품, 사용 시 수량 -1 | ✅ |
| CONSUMABLE_REST | 재우기 아이템 | 소모품, 사용 시 수량 -1 | ✅ |
| ACCESSORY | 캐릭터 액세서리 | 슬롯 시스템 필요 | ❌ (향후) |

---

### 7.2 아이템 구매

#### 구매 가능 아이템 조회
```
GET /api/v1/items/shop
```

응답:
```json
{
  "items": [
    {
      "itemId": 1,
      "name": "노바 봄 스킨",
      "type": "SKIN",
      "price": 50,
      "description": "봄날의 노바 스킨",
      "imageUrl": "https://cdn.polaris.com/items/nova_spring_skin.png",
      "characterType": "NOVA",
      "isPurchased": false
    },
    {
      "itemId": 10,
      "name": "별빛 간식",
      "type": "CONSUMABLE_FOOD",
      "price": 3,
      "description": "캐릭터의 포만감을 채워주는 간식",
      "imageUrl": "https://cdn.polaris.com/items/star_snack.png",
      "characterType": null,
      "isPurchased": false
    }
  ]
}
```

#### 아이템 구매 API
```
POST /api/v1/items/purchase
{
  "itemId": 1,
  "quantity": 1
}
```

처리 로직:
```
1. 아이템 조회
2. 아이템 유형 확인
   - SKIN/BACKGROUND: 이미 구매했는지 확인
   - CONSUMABLE: 수량 확인
3. 가격 계산 (price * quantity)
4. 사용자 별조각 확인
5. 트랜잭션 시작:
   a. 별조각 차감
   b. user_items에 INSERT 또는 quantity UPDATE
   c. item_purchase_logs 기록
6. 트랜잭션 커밋
7. 구매 결과 반환
```

응답:
```json
{
  "purchaseId": 5001,
  "itemId": 1,
  "itemName": "노바 봄 스킨",
  "quantity": 1,
  "totalPrice": 50,
  "newBalance": 100,
  "purchasedAt": "2026-05-14T11:00:00Z"
}
```

---

### 7.3 보유 아이템 조회

```
GET /api/v1/items/inventory
```

응답:
```json
{
  "skins": [
    {
      "itemId": 1,
      "name": "노바 봄 스킨",
      "imageUrl": "https://...",
      "isEquipped": true,
      "purchasedAt": "2026-05-14T11:00:00Z"
    }
  ],
  "backgrounds": [
    {
      "itemId": 20,
      "name": "별밤 배경",
      "imageUrl": "https://...",
      "isEquipped": false,
      "purchasedAt": "2026-05-13T10:00:00Z"
    }
  ],
  "consumables": [
    {
      "itemId": 10,
      "name": "별빛 간식",
      "type": "CONSUMABLE_FOOD",
      "quantity": 5,
      "imageUrl": "https://..."
    },
    {
      "itemId": 11,
      "name": "별 장난감",
      "type": "CONSUMABLE_TOY",
      "quantity": 3,
      "imageUrl": "https://..."
    }
  ]
}
```

---

### 7.4 스킨/배경 장착

#### 스킨 장착 API
```
POST /api/v1/characters/{characterId}/equip/skin
{
  "itemId": 1
}
```

처리 로직:
```
1. 캐릭터 소유권 확인
2. 아이템 소유 확인 (user_items)
3. 아이템 타입 확인 (SKIN)
4. 캐릭터 타입 일치 확인
5. 트랜잭션 시작:
   a. 기존 장착 스킨 해제 (is_equipped = false)
   b. 새 스킨 장착 (is_equipped = true)
   c. user_characters.equipped_skin_id 업데이트
6. 트랜잭션 커밋
```

#### 배경 장착 API
```
POST /api/v1/characters/{characterId}/equip/background
{
  "itemId": 20
}
```

처리 로직은 스킨과 동일하며, `equipped_background_id`를 업데이트합니다.

---

### 7.5 소모품 사용

소모품은 돌봄 액션 API에서 사용됩니다.

```
POST /api/v1/characters/{characterId}/care/feed
{
  "paymentType": "ITEM",
  "itemId": 10
}
```

처리 로직:
```
1. 아이템 소유 확인
2. 아이템 타입 확인 (CONSUMABLE_FOOD)
3. 수량 확인 (quantity >= 1)
4. 트랜잭션 시작:
   a. fullness 회복
   b. user_items.quantity -= 1
   c. quantity가 0이 되면 user_items 삭제
   d. character_care_logs 기록
5. 트랜잭션 커밋
```

---

## 8. 알림 시스템

### 8.1 알림 정책

#### 알림 생성 주체
- **캐릭터 모듈**: 알림 생성 및 내용 결정
- **게이트웨이 모듈**: 알림 전송 (FCM, APNs)

#### 알림 유형
| 유형 | 트리거 | 내용 예시 |
|------|--------|-----------|
| MISSION_OFFER | 새 미션 생성 | "노바가 오늘의 작은 미션을 찾았어요 ✨" |
| STATE_BAD | 캐릭터 상태 BAD | "노바가 배고파하고 있어요 🥺" |
| STATE_CRITICAL | 캐릭터 상태 0 | "노바가 많이 기다렸어요. 돌아와주세요!" |
| ACHIEVEMENT | 업적 달성 | "새로운 업적을 달성했어요! 🎉" |
| DAILY_REMINDER | 일일 리마인더 | "오늘도 작은 별 하나 만들어볼까요?" |

---

### 8.2 알림 생성 로직

#### 미션 제안 알림
```java
@Service
public class MissionNotificationService {
    
    public void notifyNewMission(UserMission mission) {
        UserCharacter character = characterRepository.findByUserId(mission.getUserId());
        
        NotificationRequest request = NotificationRequest.builder()
            .userId(mission.getUserId())
            .type(NotificationType.MISSION_OFFER)
            .title(character.getNickname() + "가 말을 걸어요")
            .body(mission.getCharacterMessage())
            .data(Map.of(
                "missionId", mission.getId(),
                "characterId", character.getId()
            ))
            .build();
        
        // 게이트웨이로 알림 전송 요청 (gRPC)
        notificationGrpcClient.sendNotification(request);
    }
}
```

#### 상태 악화 알림
```java
public void notifyBadState(UserCharacter character, String stateType) {
    String message = generateStateMessage(character, stateType);
    
    NotificationRequest request = NotificationRequest.builder()
        .userId(character.getUserId())
        .type(NotificationType.STATE_BAD)
        .title(character.getNickname() + "의 상태")
        .body(message)
        .data(Map.of(
            "characterId", character.getId(),
            "stateType", stateType
        ))
        .build();
    
    notificationGrpcClient.sendNotification(request);
}

private String generateStateMessage(UserCharacter character, String stateType) {
    String characterName = character.getCharacterType().getName();
    
    return switch (stateType) {
        case "fullness" -> characterName + "가 배고파하고 있어요 🥺";
        case "energy" -> characterName + "가 피곤해하고 있어요 😴";
        case "affection" -> characterName + "가 쓸쓸해하는 것 같아요 💭";
        default -> characterName + "가 당신을 기다리고 있어요";
    };
}
```

---

### 8.3 알림 전송 (게이트웨이)

게이트웨이 모듈은 캐릭터 모듈로부터 알림 요청을 받아 실제 푸시 알림을 전송합니다.

```java
@Service
public class NotificationGatewayService {
    
    @Autowired
    private FcmService fcmService;
    
    @Autowired
    private ApnsService apnsService;
    
    public void sendNotification(NotificationRequest request) {
        // 1. 사용자 디바이스 토큰 조회
        List<DeviceToken> tokens = deviceTokenRepository
            .findByUserIdAndActive(request.getUserId(), true);
        
        if (tokens.isEmpty()) {
            log.warn("No active device tokens for user: {}", request.getUserId());
            return;
        }
        
        // 2. 플랫폼별 전송
        for (DeviceToken token : tokens) {
            try {
                if (token.getPlatform() == Platform.ANDROID) {
                    fcmService.send(token.getToken(), request);
                } else if (token.getPlatform() == Platform.IOS) {
                    apnsService.send(token.getToken(), request);
                }
                
                // 3. 전송 로그 기록
                logNotificationSent(request, token);
                
            } catch (Exception e) {
                log.error("Failed to send notification", e);
                // 실패 로그 기록
                logNotificationFailed(request, token, e);
            }
        }
    }
}
```

---

### 8.4 알림 설정

#### 사용자 알림 설정 조회
```
GET /api/v1/notifications/settings
```

응답:
```json
{
  "enabled": true,
  "missionOfferEnabled": true,
  "stateAlertEnabled": true,
  "achievementEnabled": true,
  "dailyReminderEnabled": true,
  "preferredTime": "MORNING",
  "quietHoursStart": "23:00",
  "quietHoursEnd": "07:00"
}
```

#### 알림 설정 변경
```
PUT /api/v1/notifications/settings
{
  "missionOfferEnabled": false,
  "quietHoursStart": "22:00",
  "quietHoursEnd": "08:00"
}
```

---

## 9. SNS 공유 및 바이럴

### 9.1 캐릭터 카드 생성

#### 카드 생성 API
```
POST /api/v1/characters/{characterId}/share-card
```

처리 로직:
```
1. 캐릭터 정보 조회
2. 오늘 완료한 미션 수 조회
3. 보유 별조각 조회
4. 장착 중인 스킨/배경 조회
5. 카드 이미지 생성 (서버 사이드 렌더링 또는 프론트 생성)
6. 공유 링크 생성 (referral code 포함)
7. share_events 테이블에 기록
```

응답:
```json
{
  "shareCardId": "abc123",
  "imageUrl": "https://cdn.polaris.com/share-cards/abc123.png",
  "shareUrl": "https://polaris.app/share/abc123?ref=user_123",
  "shareText": "오늘도 조금 반짝였음.\n\n노바와 별조각 +10\n\"오늘도... 있었네.\"\n\n#Polaris #별조각",
  "expiresAt": "2026-05-21T11:00:00Z"
}
```

---

### 9.2 공유 링크 구조

```
https://polaris.app/share/{shareCardId}?ref={userId}
```

| 파라미터 | 설명 |
|----------|------|
| shareCardId | 공유 카드 고유 ID |
| ref | 공유한 사용자 ID (referral 추적용) |

---

### 9.3 공유 이벤트 기록

```java
@Service
public class ShareService {
    
    @Transactional
    public ShareCard createShareCard(Long characterId) {
        // 1. 카드 생성
        ShareCard card = generateShareCard(characterId);
        
        // 2. 공유 이벤트 기록
        ShareEvent event = ShareEvent.builder()
            .userId(card.getUserId())
            .characterId(characterId)
            .shareCardId(card.getId())
            .shareType(ShareType.CHARACTER_CARD)
            .platform(null)  // 실제 공유 시 업데이트
            .sharedAt(LocalDateTime.now())
            .build();
        
        shareEventRepository.save(event);
        
        return card;
    }
    
    @Transactional
    public void recordShareAction(String shareCardId, String platform) {
        ShareEvent event = shareEventRepository.findByShareCardId(shareCardId)
            .orElseThrow();
        
        event.setPlatform(platform);
        event.setSharedAt(LocalDateTime.now());
        shareEventRepository.save(event);
        
        // 공유 보상 지급 (하루 1회)
        rewardShareIfEligible(event.getUserId());
    }
}
```

---

### 9.4 공유 보상

#### 보상 정책
- 하루 1회 제한
- 자정(00:00 KST) 기준 리셋
- 보상: 별조각 10개

#### 보상 지급 API
```
POST /api/v1/share/reward
{
  "shareCardId": "abc123",
  "platform": "KAKAO" | "INSTAGRAM" | "TWITTER" | "OTHER"
}
```

처리 로직:
```
1. shareCardId 검증
2. 오늘 이미 공유 보상을 받았는지 확인
3. 받지 않았으면:
   a. 별조각 10개 지급
   b. share_events 업데이트
   c. 보상 지급 기록
4. 이미 받았으면: 400 에러
```

---

### 9.5 Referral 추적

#### 공유 링크 클릭 추적
```
GET /share/{shareCardId}?ref={userId}
```

처리 로직:
```
1. shareCardId로 카드 조회
2. ref 파라미터에서 referrer userId 추출
3. 클릭 이벤트 기록:
   - referrer_user_id
   - clicked_at
   - ip_address
   - user_agent
4. 앱 다운로드 페이지로 리다이렉트
```

#### 신규 가입 시 referral 연결
```
POST /api/v1/auth/register
{
  "authCode": "google_auth_code",
  "referralCode": "user_123"  // optional
}
```

처리 로직:
```
1. 일반 회원가입 처리
2. referralCode가 있으면:
   a. referrer 사용자 조회
   b. referrals 테이블에 기록
   c. referrer에게 보상 지급 (향후 기능)
```

---

## 10. 업적 시스템

### 10.1 업적 유형

| 업적 ID | 이름 | 조건 | 보상 |
|---------|------|------|------|
| ACH_001 | 첫 미션 | 미션 1개 완료 | 별조각 5 |
| ACH_002 | 작은 시작 | 미션 3개 완료 | 별조각 10 |
| ACH_003 | 별 모으기 | 미션 10개 완료 | 별조각 20 |
| ACH_004 | 별 수집가 | 미션 50개 완료 | 별조각 50 |
| ACH_005 | 첫 공유 | 캐릭터 카드 공유 1회 | 별조각 10 |
| ACH_006 | 7일 출석 | 7일 연속 출석 | 별조각 30 |
| ACH_007 | 30일 출석 | 30일 연속 출석 | 별조각 100 |
| ACH_008 | 스킨 수집가 | 스킨 3개 구매 | 별조각 20 |

---

### 10.2 업적 진행도 갱신

```java
@Service
public class AchievementService {
    
    @Transactional
    public void updateMissionAchievements(Long userId) {
        // 1. 완료한 미션 수 조회
        int completedCount = missionRepository.countByUserIdAndStatus(
            userId, MissionStatus.COMPLETED
        );
        
        // 2. 업적 진행도 갱신
        List<Achievement> achievements = List.of(
            new Achievement("ACH_001", 1),
            new Achievement("ACH_002", 3),
            new Achievement("ACH_003", 10),
            new Achievement("ACH_004", 50)
        );
        
        for (Achievement ach : achievements) {
            UserAchievement userAch = userAchievementRepository
                .findByUserIdAndAchievementId(userId, ach.getId())
                .orElseGet(() -> createNewUserAchievement(userId, ach.getId()));
            
            // 3. 진행도 업데이트
            userAch.setProgress(completedCount);
            
            // 4. 달성 확인
            if (!userAch.isCompleted() && completedCount >= ach.getRequirement()) {
                completeAchievement(userAch, ach);
            }
            
            userAchievementRepository.save(userAch);
        }
    }
    
    private void completeAchievement(UserAchievement userAch, Achievement ach) {
        userAch.setCompleted(true);
        userAch.setCompletedAt(LocalDateTime.now());
        
        // 보상 지급
        starPieceService.earnStarPieces(
            userAch.getUserId(),
            ach.getReward(),
            "ACHIEVEMENT",
            "업적 달성: " + ach.getName()
        );
        
        // 알림 전송
        notificationService.notifyAchievement(userAch.getUserId(), ach);
    }
}
```

---

### 10.3 업적 조회 API

```
GET /api/v1/achievements
```

응답:
```json
{
  "achievements": [
    {
      "achievementId": "ACH_001",
      "name": "첫 미션",
      "description": "첫 번째 미션을 완료하세요",
      "requirement": 1,
      "progress": 1,
      "completed": true,
      "completedAt": "2026-05-14T10:00:00Z",
      "reward": 5
    },
    {
      "achievementId": "ACH_002",
      "name": "작은 시작",
      "description": "미션 3개를 완료하세요",
      "requirement": 3,
      "progress": 2,
      "completed": false,
      "completedAt": null,
      "reward": 10
    }
  ]
}
```

---

## 11. 광고 시스템

### 11.1 광고 정책

#### MVP 광고 방식
- 모바일 하단 배너 광고
- Google AdMob 사용
- 실제 수익화는 MVP 이후

#### 광고 노출 위치
- 홈 화면 하단
- 미션 완료 후 화면 하단
- 아이템 상점 하단

---

### 11.2 광고 이벤트 기록

#### 광고 노출 기록
```
POST /api/v1/ads/impression
{
  "adUnitId": "ca-app-pub-xxx",
  "adType": "BANNER",
  "placement": "HOME_BOTTOM"
}
```

#### 광고 클릭 기록
```
POST /api/v1/ads/click
{
  "adUnitId": "ca-app-pub-xxx",
  "adType": "BANNER",
  "placement": "HOME_BOTTOM"
}
```

---

### 11.3 광고 데이터 분석

```sql
-- 일별 광고 노출/클릭 통계
SELECT 
  DATE(created_at) as date,
  placement,
  COUNT(*) FILTER (WHERE event_type = 'IMPRESSION') as impressions,
  COUNT(*) FILTER (WHERE event_type = 'CLICK') as clicks,
  ROUND(
    COUNT(*) FILTER (WHERE event_type = 'CLICK')::numeric / 
    NULLIF(COUNT(*) FILTER (WHERE event_type = 'IMPRESSION'), 0) * 100,
    2
  ) as ctr
FROM ad_events
WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY DATE(created_at), placement
ORDER BY date DESC, placement;
```

---

## 12. 요약

이 기능 명세서는 Polaris MVP의 모든 핵심 기능의 상세 동작 로직을 정의합니다.

### 핵심 정책 요약

| 기능 | 핵심 정책 |
|------|-----------|
| 인증 | Google OAuth2, JWT 기반 |
| 캐릭터 상태 | 3가지 상태, 시간 경과 감소, 0까지 감소 가능 |
| 돌봄 액션 | 밥(유료), 재우기(무료+쿨다운), 놀아주기(유료) |
| 미션 생성 | 규칙 기반 점수 + 시간 감쇠 + AI 문구 생성 |
| 미션 제공 | 하루 최대 15개, 1개씩 제안 |
| 미션 완료 | 질문 1개 + 텍스트 답변 |
| 별조각 | 무료 재화, 제한 없음, 난이도 무관 동일 보상 |
| 공유 보상 | 하루 1회, 자정 기준 리셋 |
| 알림 | 캐릭터 모듈 생성, 게이트웨이 전송 |
| 광고 | 하단 배너, AdMob |

---

**다음 문서**: [05-ERD.md](./05-erd.md)
