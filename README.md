<div align="center">
  <img src="docs/images/polaris_banner.png" width="100%" alt="Polaris Banner">
</div>

# 🌟 Polaris (별친구)

<div align="center">
  <b>AI 별친구 캐릭터 기반 일상 루틴 메이커 백엔드 서비스</b><br><br>
  <code>MSA</code> <code>gRPC 통신</code> <code>Gemini AI 연동</code> <code>SSE 실시간 대화</code> <code>Outbox Pattern 트랜잭션</code>
</div>

<br/>

<div align="center">
  <img src="https://img.shields.io/badge/Java%2021-ED8B00?style=flat-square&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring%20Boot%203-6DB33F?style=flat-square&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/gRPC-244C5A?style=flat-square&logo=grpc&logoColor=white">
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white">
  <img src="https://img.shields.io/badge/Google%20Gemini-4285F4?style=flat-square&logo=google&logoColor=white">
  <img src="https://img.shields.io/badge/Nx%20Monorepo-143055?style=flat-square&logo=nx&logoColor=white">
</div>

---

## 📌 프로젝트 소개

이 프로젝트는 **AI 캐릭터와 상호작용하며 일상 루틴을 만들어가는 Polaris 앱의 백엔드 서비스**입니다.  
단순한 투두리스트를 넘어, 유저 맞춤형 미션 생성, 캐릭터 돌봄/성장, 실시간 AI 딥톡, 상점(별조각 경제), 알림 시스템 등 다양한 도메인을 포함하고 있습니다.

특히 이번 프로젝트에서는 서비스의 확장성과 안정성을 위해 다음과 같은 실제 운영 관점의 과제들을 해결하는 데 집중했습니다.

- 🏗️ 도메인 간 결합도를 낮추고 독립적인 확장이 가능한 **MSA(Microservices Architecture) 도입**
- ⚡ REST의 오버헤드를 줄이고 서비스 간 고속 통신을 위한 **gRPC 적용**
- 🔒 분산 환경에서 별조각(재화) 및 경험치 지급의 데이터 정합성을 보장하기 위한 **Outbox Pattern 및 멱등키 적용**
- 💬 LLM(Gemini) 응답 지연을 최소화하고 사용자 경험을 극대화하기 위한 **SSE(Server-Sent Events) 기반 실시간 스트리밍**
- 🧠 캐릭터가 유저와의 과거 대화를 기억하도록 돕는 **pgvector 기반 장기 기억(Embedding) 검색**
- 📊 시스템 장애 사전 감지 및 메트릭 수집을 위한 **Prometheus + Grafana 기반 모니터링 체계 구축**

---

## ✅ 요구사항 반영 요약

| 구분 | 구현 내용 | 비고 |
|---|---|---|
| **MSA & 통신** | 8개 마이크로서비스 분리 + Gateway 라우팅 | 내부 통신은 Protocol Buffers 기반 gRPC 사용 |
| **분산 트랜잭션** | Outbox Pattern + 멱등키(Idempotency-Key) 활용 | 미션 보상, 아이템 구매, 공유 보상 중복 방지 |
| **AI 연동** | Google Gemini AI 적용 | 동적 미션 생성, 페르소나 대화, 대화 요약 |
| **실시간 대화** | SSE (Server-Sent Events) 스트리밍 적용 | AI 응답 토큰별 실시간 클라이언트 푸시 |
| **벡터 검색** | PostgreSQL `pgvector` 활용 | 대화 세션 임베딩 저장 및 유사도 검색을 통한 RAG 구현 |
| **인증 / 보안** | JWT (Access/Refresh) + Redis 블랙리스트 | Gateway 글로벌 인터셉터에서 일괄 검증 |
| **모니터링** | Prometheus pull 아키텍처 + Grafana 대시보드 | 애플리케이션, gRPC 통신, 에러 메트릭 수집 |

---

## 👥 팀 소개

| 이름  | 역할      | 담당                                         |
|-----|---------|--------------------------------------------|
| - | 팀장 / 아키텍처 | MSA 인프라 구축, Gateway 라우팅, gRPC 공통 모듈, 모니터링 |
| - | AI / 캐릭터 | Gemini 프롬프트 엔지니어링, SSE 스트리밍, 벡터 DB 구성 |
| - | 미션 / 아이템 | 미션 상태 머신, 상점 경제 시스템, 동시성/멱등성 제어 |
| - | 유저 / 인프라 | 온보딩, 알림 시스템(FCM), DB 설계, CI/CD 자동화 |

### 📎 프로젝트 문서
- 📄 [사용자 흐름도 (User Flow)](polaris_user_flow.md)
- 🗄️ [데이터베이스 모델링 및 ERD 명세](docs/sa-docs/02_ERD_Notion_Templates.md)
- 🔌 [백엔드 API 명세서](docs/sa-docs/03_API_Specification.md)
- 📊 [모니터링 가이드 (Grafana/Prometheus)](docs/sa-docs/application_monitoring_guide.md)

---

## 🛠 기술 스택

### 📦 Backend
<p>
  <img src="https://img.shields.io/badge/Java%2021-ED8B00?style=flat-square&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring%20Boot%203-6DB33F?style=flat-square&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=flat-square&logo=spring&logoColor=white">
  <img src="https://img.shields.io/badge/gRPC-244C5A?style=flat-square&logo=grpc&logoColor=white">
  <img src="https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white">
</p>

### 🗄️ Database / Cache
<p>
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/pgvector-336791?style=flat-square&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white">
</p>

### 🤖 AI / External API
<p>
  <img src="https://img.shields.io/badge/Google%20Gemini-4285F4?style=flat-square&logo=google&logoColor=white">
  <img src="https://img.shields.io/badge/FCM-FFCA28?style=flat-square&logo=firebase&logoColor=white">
  <img src="https://img.shields.io/badge/AWS%20S3-569A31?style=flat-square&logo=amazons3&logoColor=white">
</p>

### 📊 Build / DevOps / Monitoring
<p>
  <img src="https://img.shields.io/badge/Nx%20Monorepo-143055?style=flat-square&logo=nx&logoColor=white">
  <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white">
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white">
  <img src="https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white">
</p>

---

## 🧩 아키텍처

### 🌐 서비스 아키텍처
<p align="center">
  <img src="" width="80%" alt="(서비스 아키텍처 다이어그램 들어갈 자리)">
</p>

- 클라이언트 요청은 **Gateway**에서 JWT 인증을 거쳐 각 서비스로 라우팅됩니다.
- 모듈 간 결합도를 낮추기 위해 **gRPC**를 이용해 통신하며, Proto 스키마로 인터페이스를 엄격하게 관리합니다.
- 복잡한 AI 로직은 `ai` 서비스가 전담하며, 타 서비스는 결과를 비동기 또는 스트리밍 방식으로 수신합니다.

---

## 🗄 ERD

<p align="center">
  <img src="" width="80%" alt="(ERD 이미지 들어갈 자리)">
</p>

### 핵심 도메인 관계
- **회원(`users`)**: 온보딩 정보, 지갑(별조각), 출석 기록과 1:N으로 연결됩니다.
- **캐릭터(`user_characters`)**: 사용자는 자신만의 캐릭터를 가지며, 돌봄 내역(`character_care_logs`)과 장착 스킨 정보가 기록됩니다.
- **미션(`user_missions`)**: AI가 동적으로 생성한 미션으로 관리되며, 수락/거절/완료 상태를 가집니다.
- **기억(`user_memory_embeddings`)**: 완료된 미션 및 캐릭터와의 대화 내역은 요약되어 벡터 임베딩으로 저장됩니다.

---

## 🚀 주요 기능

### 👤 인증 / 회원 (User)
- 구글 OAuth 기반 가입 및 로그인 (JWT Auth)
- 맞춤형 미션을 위한 온보딩 (라이프스타일, 기상/취침 시간 등 수집)
- 출석 체크 및 지갑(별조각) 관리

### 👾 캐릭터 (Character)
- 파트너 캐릭터 생성 (NOVA, MUMU, JJORY)
- 캐릭터 돌봄 (먹이주기, 재우기, 놀아주기) 및 경험치/레벨업 시스템
- 획득한 스킨 장착/해제 및 상태(포만감/에너지/애정도) 관리

### 🎯 AI 미션 (Mission)
- 유저 컨텍스트(시간/장소) 기반 AI 동적 미션 생성
- 미션 수락 / 거절 / 완료 질문 세션
- 미션 완료 시 보상(별조각/경험치) 자동 지급 분산 트랜잭션 처리

### 🛍️ 아이템 상점 (Item)
- 소모성 아이템 및 스킨 상점 목록 조회
- 멱등키를 활용한 안전한 아이템 구매 (재화 차감 및 인벤토리 추가)

### 💬 실시간 대화 (AI Chat)
- 캐릭터의 페르소나와 유저의 과거 기억(Vector Search)을 반영한 AI 딥톡
- SSE(Server-Sent Events)를 통한 응답 지연 없는 실시간 토큰 스트리밍

### 📣 공유 및 알림 (Share & Notification)
- 커스텀 완료 카드 렌더링 후 S3 업로드 및 SNS 공유
- FCM 기반 푸시 알림 및 유저 맞춤형 방해금지 시간 설정

---

## 🧠 기술 선택 근거 및 트러블슈팅

### 1. MSA 및 gRPC 통신

#### 💡 왜 MSA와 gRPC를 적용했는가
초기 모놀리식 구조에서는 AI 텍스트 생성 작업이나 임베딩 연산이 전체 서버의 스레드를 점유하여, 단순 정보 조회 API까지 응답이 지연되는 병목이 발생할 우려가 있었습니다. AI 모듈, 캐릭터, 미션 등 도메인 성격이 명확히 다르고 확장 요구사항이 달라 **MSA로 분리**했습니다.

내부 서비스 간 통신은 REST API 대신 **gRPC**를 채택했습니다. HTTP/2 기반의 양방향 스트리밍과 Protocol Buffers의 바이너리 직렬화를 통해 페이로드 크기를 줄이고 통신 속도를 극대화하여 마이크로서비스 환경에서의 지연을 최소화했습니다.

---

### 2. 분산 환경에서의 정합성 보장 (Outbox Pattern)

#### 🧨 문제 상황
'미션 완료' 시 미션 상태 변경(Mission), 별조각 지급(User), 경험치 증가(Character)가 동시에 일어나야 합니다. 이 중 하나의 네트워크 호출이 실패하면 데이터 불일치가 발생합니다. 2PC나 분산 락은 MSA 환경에서 성능 저하가 커서 도입을 피했습니다.

#### 🎯 해결 방식 (Outbox Pattern + Idempotency)
- 미션 완료 트랜잭션 내에 `mission_outbox_events` 테이블에 보상 지급 이벤트를 `PENDING` 상태로 함께 저장합니다.
- 메시지 릴레이(스케줄러)가 해당 이벤트를 읽어 User/Character 서비스로 gRPC 호출을 비동기로 시도합니다.
- 수신 측에서는 `Idempotency-Key` (예: `reward_mission_1234`)를 검증하여 일시적인 네트워크 오류로 인한 **중복 지급을 원천 차단**합니다. 성공 시 outbox 상태를 `SUCCEEDED`로 변경합니다.

---

### 3. 실시간 AI 대화 (SSE 스트리밍)

#### 🧨 문제 상황
Gemini AI API 호출 후 전체 텍스트가 완성될 때까지 3~5초가 소요되어, 사용자가 답답함을 느낄 수 있었습니다. 

#### 🎯 선택 기술: SSE (Server-Sent Events)
- 웹소켓(WebSocket)도 고려했으나, 클라이언트에서 서버로 메시지를 한 번 보내고 서버가 응답을 길게 내려주는 단방향 성격이 강해, 오버헤드가 적고 HTTP 기본 포트를 사용하는 **SSE가 적합하다고 판단**했습니다.
- AI 모듈에서 토큰이 생성될 때마다 gRPC 스트리밍으로 Character 서비스에 전달하고, Character 서비스는 이를 다시 SSE 이벤트로 묶어 클라이언트에 즉시 푸시하여 체감 지연 속도를 0.5초 이내로 단축했습니다.

---

### 4. 대화 기억 장기화 (pgvector)

#### 🧨 문제 상황
프롬프트에 모든 과거 대화 내역을 넣으면 LLM의 토큰 제한에 걸리고 비용이 기하급수적으로 증가합니다.

#### 🎯 해결 방식 (RAG)
- 대화 세션이 종료되면 AI가 대화의 핵심을 요약하고 이를 임베딩 모델을 통해 벡터(768차원 배열)로 변환하여 PostgreSQL의 `user_memory_embeddings` (`pgvector` 익스텐션) 테이블에 저장합니다.
- 다음 대화가 시작될 때 현재 컨텍스트와 가장 코사인 유사도(Cosine Similarity)가 높은 과거 기억 3개를 추출해 프롬프트에 주입(RAG)함으로써, 토큰 낭비 없이 **캐릭터가 유저의 과거 상황을 자연스럽게 기억**하도록 구현했습니다.

---

## 🗺 사용자 흐름도 (User Flow)

앱 진입부터 미션 수행, 보상 획득까지의 전체 사용자 여정입니다.

<p align="center">
  <img src="docs/images/polaris_user_flow.png" width="80%" alt="Polaris User Flow">
</p>

---

## 🖼 API 명세서 & 테스트 케이스

<p align="center">
  <img src="" width="80%" alt="(API 명세서 이미지 들어갈 자리)">
</p>

<p align="center">
  <img src="" width="80%" alt="(테스트 케이스 이미지 들어갈 자리)">
</p>

---

## 📁 프로젝트 파일 구조

```text
Polaris/
├── ai/                # AI 연동 및 프롬프트 관리
├── character/         # 캐릭터 상태 및 상호작용
├── common/            # 공통 유틸리티 및 예외 처리
├── docs/              # 문서 및 에셋 이미지
│   ├── images/
│   └── sa-docs/       # API 명세, 설계 문서 등
├── event-log/         # 분석용 데이터 로그 단일 저장소
├── gateway/           # REST API 진입점, 라우팅, 인증
├── item/              # 상점 및 아이템
├── mission/           # 미션 상태 머신
├── notification/      # 푸시 알림
├── proto/             # gRPC Protobuf 스키마
├── user/              # 회원, 온보딩, 지갑
├── nx.json            # Nx 모노레포 설정
├── build.gradle
└── settings.gradle
```

---
*Created with ❤️ by the Polaris Team*
