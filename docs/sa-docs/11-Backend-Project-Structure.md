# 11. 백엔드 프로젝트 구조 설계서

## 문서 정보

| 항목 | 내용 |
|------|------|
| 문서명 | Polaris MVP 백엔드 프로젝트 구조 설계서 |
| 작성일 | 2026-05-14 |
| 버전 | v1.0 |
| 목적 | 백엔드 프로젝트의 디렉토리 구조 및 패키지 설계 정의 |
| 대상 독자 | 백엔드 개발자, 아키텍트 |

---

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [전체 프로젝트 구조](#전체-프로젝트-구조)
3. [모듈별 상세 구조](#모듈별-상세-구조)
4. [공통 모듈 설계](#공통-모듈-설계)
5. [패키지 네이밍 규칙](#패키지-네이밍-규칙)
6. [레이어 아키텍처](#레이어-아키텍처)
7. [의존성 관리](#의존성-관리)
8. [설정 파일 관리](#설정-파일-관리)

---

## 프로젝트 개요

### 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Java 17+ |
| 프레임워크 | Spring Boot 3.x |
| 빌드 도구 | Gradle 8.x |
| 데이터베이스 | PostgreSQL 15+ |
| ORM | Spring Data JPA (Hibernate) |
| 통신 | REST API, gRPC (모듈 간) |
| 인증 | JWT, OAuth2 (Google, Apple) |
| 캐싱 | Redis |
| 메시징 | (향후) Kafka |

### 아키텍처 패턴

- **모듈러 모놀리스**: 논리적으로 분리된 모듈, 물리적으로는 단일 애플리케이션
- **레이어드 아키텍처**: Presentation → Application → Domain → Infrastructure
- **DDD (Domain-Driven Design)**: 도메인 중심 설계
- **Clean Architecture**: 의존성 역전 원칙

---

## 전체 프로젝트 구조

```
polaris/
├── proto/                          # gRPC 프로토콜 정의
│   ├── src/main/proto/
│   │   ├── user.proto
│   │   ├── character.proto
│   │   └── common.proto
│   └── build.gradle
│
├── common/                         # 공통 모듈 (신규 생성 필요)
│   ├── src/main/java/p5laris/common/
│   │   ├── config/                # 공통 설정
│   │   ├── exception/             # 공통 예외
│   │   ├── util/                  # 유틸리티
│   │   ├── dto/                   # 공통 DTO
│   │   └── security/              # 보안 공통
│   └── build.gradle
│
├── gateway/                        # API Gateway 모듈
│   ├── src/main/java/p5laris/gateway/
│   │   ├── GatewayApplication.java
│   │   ├── config/
│   │   ├── filter/
│   │   ├── route/
│   │   └── security/
│   ├── src/main/resources/
│   │   └── application.yaml
│   └── build.gradle
│
├── user/                           # User 모듈
│   ├── src/main/java/p5laris/user/
│   │   ├── UserApplication.java
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── presentation/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── db/migration/
│   ├── src/test/java/p5laris/user/
│   └── build.gradle
│
├── character/                      # Character 모듈
│   ├── src/main/java/p5laris/character/
│   │   ├── CharacterApplication.java
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── presentation/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── db/migration/
│   ├── src/test/java/p5laris/character/
│   └── build.gradle
│
├── item/                           # Item 모듈
│   ├── src/main/java/p5laris/item/
│   │   ├── ItemApplication.java
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── presentation/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── db/migration/
│   ├── src/test/java/p5laris/item/
│   └── build.gradle
│
├── mission/                        # Mission 모듈
│   ├── src/main/java/p5laris/mission/
│   │   ├── MissionApplication.java
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── presentation/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── db/migration/
│   ├── src/test/java/p5laris/mission/
│   └── build.gradle
│
├── ai/                             # AI 모듈
│   ├── src/main/java/p5laris/ai/
│   │   ├── AiApplication.java
│   │   ├── service/
│   │   ├── client/
│   │   ├── prompt/
│   │   └── fallback/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── prompts/
│   ├── src/test/java/p5laris/ai/
│   └── build.gradle
│
├── notification/                   # Notification 모듈 (신규 생성 필요)
│   ├── src/main/java/p5laris/notification/
│   │   ├── NotificationApplication.java
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── presentation/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── db/migration/
│   ├── src/test/java/p5laris/notification/
│   └── build.gradle
│
├── operation/                      # Operation 모듈 (신규 생성 필요)
│   ├── src/main/java/p5laris/operation/
│   │   ├── OperationApplication.java
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── presentation/
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   └── db/migration/
│   ├── src/test/java/p5laris/operation/
│   └── build.gradle
│
├── docs/                           # 문서
│   ├── sa-docs/                   # 시스템 아키텍처 문서
│   ├── api-docs/                  # API 문서 (Swagger)
│   └── README.md
│
├── scripts/                        # 스크립트 (신규 생성 필요)
│   ├── deploy/
│   ├── db/
│   └── monitoring/
│
├── build.gradle                    # 루트 빌드 설정
├── settings.gradle                 # 프로젝트 설정
├── gradlew                         # Gradle Wrapper
├── gradlew.bat
└── README.md
```

---

## 모듈별 상세 구조

### User Module 상세 구조

```
user/
├── src/main/java/p5laris/user/
│   ├── UserApplication.java                    # Spring Boot 메인 클래스
│   │
│   ├── domain/                                  # 도메인 레이어
│   │   ├── model/                              # 도메인 모델 (엔티티)
│   │   │   ├── User.java
│   │   │   ├── UserProfile.java
│   │   │   ├── UserSession.java
│   │   │   └── StarPieceTransaction.java
│   │   │
│   │   ├── vo/                                 # Value Object
│   │   │   ├── Email.java
│   │   │   ├── DisplayName.java
│   │   │   └── StarPieces.java
│   │   │
│   │   ├── repository/                         # 리포지토리 인터페이스
│   │   │   ├── UserRepository.java
│   │   │   ├── UserProfileRepository.java
│   │   │   ├── UserSessionRepository.java
│   │   │   └── StarPieceTransactionRepository.java
│   │   │
│   │   ├── service/                            # 도메인 서비스
│   │   │   ├── UserDomainService.java
│   │   │   └── StarPieceDomainService.java
│   │   │
│   │   └── event/                              # 도메인 이벤트 (향후)
│   │       ├── UserRegisteredEvent.java
│   │       └── StarPiecesEarnedEvent.java
│   │
│   ├── application/                             # 애플리케이션 레이어
│   │   ├── service/                            # 애플리케이션 서비스
│   │   │   ├── UserService.java
│   │   │   ├── AuthService.java
│   │   │   ├── UserProfileService.java
│   │   │   └── StarPieceService.java
│   │   │
│   │   ├── dto/                                # DTO (요청/응답)
│   │   │   ├── request/
│   │   │   │   ├── LoginRequest.java
│   │   │   │   ├── OnboardingRequest.java
│   │   │   │   └── ProfileUpdateRequest.java
│   │   │   └── response/
│   │   │       ├── LoginResponse.java
│   │   │       ├── UserResponse.java
│   │   │       └── StarPieceBalanceResponse.java
│   │   │
│   │   ├── mapper/                             # DTO ↔ Entity 매퍼
│   │   │   ├── UserMapper.java
│   │   │   └── StarPieceMapper.java
│   │   │
│   │   └── usecase/                            # Use Case (선택적)
│   │       ├── RegisterUserUseCase.java
│   │       └── CompleteOnboardingUseCase.java
│   │
│   ├── infrastructure/                          # 인프라 레이어
│   │   ├── persistence/                        # 영속성 구현
│   │   │   ├── entity/                         # JPA 엔티티
│   │   │   │   ├── UserEntity.java
│   │   │   │   ├── UserProfileEntity.java
│   │   │   │   └── StarPieceTransactionEntity.java
│   │   │   │
│   │   │   ├── repository/                     # JPA 리포지토리 구현
│   │   │   │   ├── UserJpaRepository.java
│   │   │   │   └── UserRepositoryImpl.java
│   │   │   │
│   │   │   └── mapper/                         # Entity ↔ Domain 매퍼
│   │   │       └── UserEntityMapper.java
│   │   │
│   │   ├── external/                           # 외부 시스템 연동
│   │   │   ├── oauth/
│   │   │   │   ├── GoogleOAuthClient.java
│   │   │   │   └── AppleOAuthClient.java
│   │   │   │
│   │   │   └── grpc/                           # gRPC 클라이언트
│   │   │       └── CharacterGrpcClient.java
│   │   │
│   │   ├── cache/                              # 캐시 구현
│   │   │   └── UserCacheRepository.java
│   │   │
│   │   └── config/                             # 인프라 설정
│   │       ├── JpaConfig.java
│   │       └── RedisConfig.java
│   │
│   └── presentation/                            # 프레젠테이션 레이어
│       ├── rest/                               # REST API 컨트롤러
│       │   ├── AuthController.java
│       │   ├── UserController.java
│       │   └── StarPieceController.java
│       │
│       ├── grpc/                               # gRPC 서버
│       │   └── UserGrpcService.java
│       │
│       ├── filter/                             # 필터
│       │   └── JwtAuthenticationFilter.java
│       │
│       └── exception/                          # 예외 핸들러
│           └── UserExceptionHandler.java
│
├── src/main/resources/
│   ├── application.yaml                        # 메인 설정
│   ├── application-dev.yaml                    # 개발 환경 설정
│   ├── application-prod.yaml                   # 운영 환경 설정
│   │
│   └── db/migration/                           # Flyway 마이그레이션
│       ├── V1__create_users_table.sql
│       ├── V2__create_user_profiles_table.sql
│       └── V3__create_star_piece_transactions_table.sql
│
└── src/test/java/p5laris/user/
    ├── domain/
    │   └── service/
    │       └── UserDomainServiceTest.java
    │
    ├── application/
    │   └── service/
    │       └── UserServiceTest.java
    │
    └── presentation/
        └── rest/
            └── UserControllerTest.java
```

---

### Character Module 상세 구조

```
character/
├── src/main/java/p5laris/character/
│   ├── CharacterApplication.java
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── CharacterType.java
│   │   │   ├── CharacterAsset.java
│   │   │   ├── UserCharacter.java
│   │   │   ├── CharacterCareLog.java
│   │   │   ├── ShareCard.java
│   │   │   ├── ShareEvent.java
│   │   │   └── Referral.java
│   │   │
│   │   ├── vo/
│   │   │   ├── CharacterState.java          # fullness, energy, affection
│   │   │   ├── CharacterCode.java           # NOVA, MUMU, JJORY
│   │   │   └── CareType.java                # FEED, SLEEP, PLAY
│   │   │
│   │   ├── repository/
│   │   │   ├── CharacterTypeRepository.java
│   │   │   ├── UserCharacterRepository.java
│   │   │   ├── CharacterCareLogRepository.java
│   │   │   ├── ShareCardRepository.java
│   │   │   └── ShareEventRepository.java
│   │   │
│   │   └── service/
│   │       ├── CharacterStateDomainService.java
│   │       └── ShareDomainService.java
│   │
│   ├── application/
│   │   ├── service/
│   │   │   ├── CharacterService.java
│   │   │   ├── CharacterCareService.java
│   │   │   ├── CharacterStateService.java
│   │   │   ├── ShareCardService.java
│   │   │   └── ReferralService.java
│   │   │
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── CreateCharacterRequest.java
│   │   │   │   ├── CareActionRequest.java
│   │   │   │   └── ShareCardRequest.java
│   │   │   └── response/
│   │   │       ├── CharacterResponse.java
│   │   │       ├── CharacterStateResponse.java
│   │   │       ├── CareActionResponse.java
│   │   │       └── ShareCardResponse.java
│   │   │
│   │   ├── scheduler/
│   │   │   └── CharacterStateScheduler.java  # 1시간마다 상태 감소
│   │   │
│   │   └── mapper/
│   │       └── CharacterMapper.java
│   │
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── entity/
│   │   │   │   ├── CharacterTypeEntity.java
│   │   │   │   ├── UserCharacterEntity.java
│   │   │   │   └── CharacterCareLogEntity.java
│   │   │   │
│   │   │   └── repository/
│   │   │       ├── CharacterTypeJpaRepository.java
│   │   │       └── UserCharacterJpaRepository.java
│   │   │
│   │   ├── external/
│   │   │   ├── grpc/
│   │   │   │   └── UserGrpcClient.java       # User 모듈 호출
│   │   │   │
│   │   │   └── storage/
│   │   │       └── S3ImageStorageClient.java  # 공유 카드 이미지 저장
│   │   │
│   │   └── cache/
│   │       └── CharacterTypeCacheRepository.java
│   │
│   └── presentation/
│       ├── rest/
│       │   ├── CharacterController.java
│       │   ├── CharacterCareController.java
│       │   └── ShareController.java
│       │
│       └── grpc/
│           └── CharacterGrpcService.java
│
└── src/main/resources/
    ├── application.yaml
    └── db/migration/
        ├── V1__create_character_types_table.sql
        ├── V2__create_character_assets_table.sql
        ├── V3__create_user_characters_table.sql
        ├── V4__create_character_care_logs_table.sql
        ├── V5__create_share_cards_table.sql
        └── V6__create_share_events_table.sql
```

---

### Mission Module 상세 구조

```
mission/
├── src/main/java/p5laris/mission/
│   ├── MissionApplication.java
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── MissionTemplate.java
│   │   │   ├── UserMission.java
│   │   │   ├── MissionRejectionLog.java
│   │   │   └── MissionRecommendationLog.java
│   │   │
│   │   ├── vo/
│   │   │   ├── MissionCategory.java         # BASIC_ROUTINE, SPACE_RESET 등
│   │   │   ├── MissionDifficulty.java       # VERY_LIGHT, LIGHT, NORMAL 등
│   │   │   ├── MissionStatus.java           # OFFERED, COMPLETED, REJECTED
│   │   │   └── RejectionReason.java
│   │   │
│   │   ├── repository/
│   │   │   ├── MissionTemplateRepository.java
│   │   │   ├── UserMissionRepository.java
│   │   │   └── MissionRecommendationLogRepository.java
│   │   │
│   │   └── service/
│   │       └── MissionRecommendationDomainService.java
│   │
│   ├── application/
│   │   ├── service/
│   │   │   ├── MissionService.java
│   │   │   ├── MissionGenerationService.java
│   │   │   ├── MissionRecommendationService.java
│   │   │   └── MissionCompletionService.java
│   │   │
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── RejectMissionRequest.java
│   │   │   │   ├── CompleteMissionRequest.java
│   │   │   │   └── AnswerMissionRequest.java
│   │   │   └── response/
│   │   │       ├── CurrentMissionResponse.java
│   │   │       ├── MissionCompletionResponse.java
│   │   │       └── MissionStatsResponse.java
│   │   │
│   │   ├── algorithm/
│   │   │   ├── MissionScoreCalculator.java   # 점수 계산 알고리즘
│   │   │   └── WeightedRandomSelector.java   # Weighted Random 선정
│   │   │
│   │   ├── scheduler/
│   │   │   └── MissionOfferScheduler.java    # 미션 제안 스케줄러
│   │   │
│   │   └── mapper/
│   │       └── MissionMapper.java
│   │
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── entity/
│   │   │   │   ├── MissionTemplateEntity.java
│   │   │   │   ├── UserMissionEntity.java
│   │   │   │   └── MissionRejectionLogEntity.java
│   │   │   │
│   │   │   └── repository/
│   │   │       ├── MissionTemplateJpaRepository.java
│   │   │       └── UserMissionJpaRepository.java
│   │   │
│   │   ├── external/
│   │   │   ├── grpc/
│   │   │   │   ├── UserGrpcClient.java
│   │   │   │   ├── CharacterGrpcClient.java
│   │   │   │   └── AiGrpcClient.java
│   │   │   │
│   │   │   └── weather/
│   │   │       └── WeatherApiClient.java      # 날씨 API 연동
│   │   │
│   │   └── cache/
│   │       └── MissionTemplateCacheRepository.java
│   │
│   └── presentation/
│       ├── rest/
│       │   └── MissionController.java
│       │
│       └── grpc/
│           └── MissionGrpcService.java
│
└── src/main/resources/
    ├── application.yaml
    └── db/migration/
        ├── V1__create_mission_templates_table.sql
        ├── V2__create_user_missions_table.sql
        ├── V3__create_mission_rejection_logs_table.sql
        └── V4__create_mission_recommendation_logs_table.sql
```

---

### AI Module 상세 구조

```
ai/
├── src/main/java/p5laris/ai/
│   ├── AiApplication.java
│   │
│   ├── service/
│   │   ├── AiService.java                    # AI 서비스 인터페이스
│   │   ├── OpenAiService.java                # OpenAI 구현
│   │   ├── ClaudeService.java                # Claude 구현 (향후)
│   │   └── FallbackService.java              # Fallback 문구 관리
│   │
│   ├── client/
│   │   ├── OpenAiClient.java                 # OpenAI API 클라이언트
│   │   └── ClaudeClient.java                 # Claude API 클라이언트
│   │
│   ├── prompt/
│   │   ├── PromptBuilder.java                # 프롬프트 생성
│   │   ├── MissionOfferPrompt.java
│   │   ├── CompletionQuestionPrompt.java
│   │   ├── CompletionResponsePrompt.java
│   │   └── RejectionResponsePrompt.java
│   │
│   ├── fallback/
│   │   ├── FallbackRepository.java           # Fallback 문구 저장소
│   │   ├── FallbackSelector.java             # Fallback 선택 로직
│   │   └── fallback-messages.json            # Fallback 문구 DB
│   │
│   ├── validation/
│   │   ├── AiResponseValidator.java          # AI 응답 검증
│   │   └── SafetyGuardian.java               # 안전 가이드라인 체크
│   │
│   ├── dto/
│   │   ├── request/
│   │   │   ├── GenerateMissionMessageRequest.java
│   │   │   └── GenerateCompletionQuestionRequest.java
│   │   └── response/
│   │       ├── AiGenerationResponse.java
│   │       └── FallbackResponse.java
│   │
│   ├── config/
│   │   ├── OpenAiConfig.java
│   │   └── AiRetryConfig.java                # Retry 정책
│   │
│   └── exception/
│       ├── AiApiException.java
│       └── AiTimeoutException.java
│
└── src/main/resources/
    ├── application.yaml
    │
    └── prompts/                               # 프롬프트 템플릿
        ├── mission-offer-system.txt
        ├── completion-question-system.txt
        └── completion-response-system.txt
```

---

### Item Module 상세 구조

```
item/
├── src/main/java/p5laris/item/
│   ├── ItemApplication.java
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Item.java
│   │   │   ├── UserItem.java
│   │   │   └── ItemPurchaseLog.java
│   │   │
│   │   ├── vo/
│   │   │   ├── ItemType.java                # SKIN, BACKGROUND, CONSUMABLE
│   │   │   └── ItemPrice.java
│   │   │
│   │   ├── repository/
│   │   │   ├── ItemRepository.java
│   │   │   ├── UserItemRepository.java
│   │   │   └── ItemPurchaseLogRepository.java
│   │   │
│   │   └── service/
│   │       └── ItemPurchaseDomainService.java
│   │
│   ├── application/
│   │   ├── service/
│   │   │   ├── ItemService.java
│   │   │   ├── ItemPurchaseService.java
│   │   │   ├── ItemEquipService.java
│   │   │   └── ConsumableItemService.java
│   │   │
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── PurchaseItemRequest.java
│   │   │   │   ├── EquipItemRequest.java
│   │   │   │   └── UseConsumableRequest.java
│   │   │   └── response/
│   │   │       ├── ItemListResponse.java
│   │   │       ├── PurchaseResponse.java
│   │   │       └── InventoryResponse.java
│   │   │
│   │   └── mapper/
│   │       └── ItemMapper.java
│   │
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── entity/
│   │   │   │   ├── ItemEntity.java
│   │   │   │   ├── UserItemEntity.java
│   │   │   │   └── ItemPurchaseLogEntity.java
│   │   │   │
│   │   │   └── repository/
│   │   │       ├── ItemJpaRepository.java
│   │   │       └── UserItemJpaRepository.java
│   │   │
│   │   ├── external/
│   │   │   └── grpc/
│   │   │       ├── UserGrpcClient.java       # 별조각 차감
│   │   │       └── CharacterGrpcClient.java  # 아이템 장착
│   │   │
│   │   └── cache/
│   │       └── ItemCacheRepository.java
│   │
│   └── presentation/
│       ├── rest/
│       │   └── ItemController.java
│       │
│       └── grpc/
│           └── ItemGrpcService.java
│
└── src/main/resources/
    ├── application.yaml
    └── db/migration/
        ├── V1__create_items_table.sql
        ├── V2__create_user_items_table.sql
        └── V3__create_item_purchase_logs_table.sql
```

---

### Notification Module 상세 구조

```
notification/
├── src/main/java/p5laris/notification/
│   ├── NotificationApplication.java
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── DeviceToken.java
│   │   │   ├── NotificationSetting.java
│   │   │   └── NotificationLog.java
│   │   │
│   │   ├── vo/
│   │   │   ├── NotificationType.java        # MISSION_OFFER, STATE_BAD 등
│   │   │   ├── Platform.java                # IOS, ANDROID
│   │   │   └── DeliveryStatus.java
│   │   │
│   │   ├── repository/
│   │   │   ├── DeviceTokenRepository.java
│   │   │   ├── NotificationSettingRepository.java
│   │   │   └── NotificationLogRepository.java
│   │   │
│   │   └── service/
│   │       └── NotificationDomainService.java
│   │
│   ├── application/
│   │   ├── service/
│   │   │   ├── NotificationService.java
│   │   │   ├── PushNotificationService.java
│   │   │   └── NotificationSettingService.java
│   │   │
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── RegisterDeviceTokenRequest.java
│   │   │   │   └── UpdateNotificationSettingRequest.java
│   │   │   └── response/
│   │   │       ├── NotificationSettingResponse.java
│   │   │       └── NotificationLogResponse.java
│   │   │
│   │   ├── scheduler/
│   │   │   └── DailyReminderScheduler.java   # 일일 알림 스케줄러
│   │   │
│   │   └── mapper/
│   │       └── NotificationMapper.java
│   │
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── entity/
│   │   │   │   ├── DeviceTokenEntity.java
│   │   │   │   ├── NotificationSettingEntity.java
│   │   │   │   └── NotificationLogEntity.java
│   │   │   │
│   │   │   └── repository/
│   │   │       ├── DeviceTokenJpaRepository.java
│   │   │       └── NotificationSettingJpaRepository.java
│   │   │
│   │   ├── external/
│   │   │   ├── fcm/
│   │   │   │   └── FcmClient.java            # Firebase Cloud Messaging
│   │   │   │
│   │   │   └── apns/
│   │   │       └── ApnsClient.java           # Apple Push Notification
│   │   │
│   │   └── config/
│   │       ├── FcmConfig.java
│   │       └── ApnsConfig.java
│   │
│   └── presentation/
│       ├── rest/
│       │   └── NotificationController.java
│       │
│       └── grpc/
│           └── NotificationGrpcService.java
│
└── src/main/resources/
    ├── application.yaml
    ├── firebase-service-account.json          # FCM 인증 파일
    └── db/migration/
        ├── V1__create_device_tokens_table.sql
        ├── V2__create_notification_settings_table.sql
        └── V3__create_notification_logs_table.sql
```

---

## 공통 모듈 설계

### Common Module 구조

```
common/
├── src/main/java/p5laris/common/
│   │
│   ├── config/                                # 공통 설정
│   │   ├── JpaAuditingConfig.java            # JPA Auditing 설정
│   │   ├── RedisConfig.java                  # Redis 공통 설정
│   │   ├── WebMvcConfig.java                 # Web MVC 설정
│   │   └── AsyncConfig.java                  # 비동기 처리 설정
│   │
│   ├── exception/                             # 공통 예외
│   │   ├── BusinessException.java            # 비즈니스 예외 기본 클래스
│   │   ├── ErrorCode.java                    # 에러 코드 Enum
│   │   ├── GlobalExceptionHandler.java       # 전역 예외 핸들러
│   │   │
│   │   └── custom/                           # 커스텀 예외
│   │       ├── ResourceNotFoundException.java
│   │       ├── UnauthorizedException.java
│   │       ├── ForbiddenException.java
│   │       └── InvalidRequestException.java
│   │
│   ├── dto/                                   # 공통 DTO
│   │   ├── ApiResponse.java                  # 공통 응답 형식
│   │   ├── ErrorResponse.java                # 에러 응답 형식
│   │   ├── PageResponse.java                 # 페이징 응답
│   │   └── CursorPageResponse.java           # Cursor 페이징 응답
│   │
│   ├── security/                              # 보안 공통
│   │   ├── jwt/
│   │   │   ├── JwtTokenProvider.java         # JWT 토큰 생성/검증
│   │   │   ├── JwtAuthenticationFilter.java  # JWT 인증 필터
│   │   │   └── JwtProperties.java            # JWT 설정
│   │   │
│   │   ├── oauth/
│   │   │   ├── OAuth2UserInfo.java           # OAuth2 사용자 정보
│   │   │   └── OAuth2Provider.java           # OAuth2 제공자 Enum
│   │   │
│   │   └── SecurityConfig.java               # Spring Security 설정
│   │
│   ├── util/                                  # 유틸리티
│   │   ├── DateTimeUtil.java                 # 날짜/시간 유틸
│   │   ├── StringUtil.java                   # 문자열 유틸
│   │   ├── JsonUtil.java                     # JSON 유틸
│   │   ├── CursorUtil.java                   # Cursor 인코딩/디코딩
│   │   └── ValidationUtil.java               # 검증 유틸
│   │
│   ├── annotation/                            # 커스텀 어노테이션
│   │   ├── CurrentUser.java                  # 현재 사용자 주입
│   │   ├── RateLimit.java                    # Rate Limiting
│   │   └── Cacheable.java                    # 캐싱
│   │
│   ├── aspect/                                # AOP
│   │   ├── LoggingAspect.java                # 로깅 AOP
│   │   ├── PerformanceAspect.java            # 성능 측정 AOP
│   │   └── RateLimitAspect.java              # Rate Limiting AOP
│   │
│   ├── interceptor/                           # 인터셉터
│   │   └── RequestLoggingInterceptor.java    # 요청 로깅
│   │
│   ├── validator/                             # 커스텀 Validator
│   │   ├── EmailValidator.java
│   │   └── PhoneNumberValidator.java
│   │
│   └── constant/                              # 상수
│       ├── CacheKey.java                     # 캐시 키
│       ├── HeaderConstant.java               # HTTP 헤더
│       └── TimeConstant.java                 # 시간 상수
│
└── src/main/resources/
    └── application-common.yaml                # 공통 설정
```

---

## 패키지 네이밍 규칙

### 기본 규칙

```
p5laris.{module}.{layer}.{feature}
```

### 예시

| 패키지 | 설명 |
|--------|------|
| `p5laris.user.domain.model` | User 모듈의 도메인 모델 |
| `p5laris.user.application.service` | User 모듈의 애플리케이션 서비스 |
| `p5laris.user.infrastructure.persistence` | User 모듈의 영속성 구현 |
| `p5laris.user.presentation.rest` | User 모듈의 REST API |
| `p5laris.common.exception` | 공통 예외 |

### 클래스 네이밍 규칙

| 타입 | 네이밍 규칙 | 예시 |
|------|------------|------|
| Entity (Domain) | `{Name}` | `User`, `Character` |
| Entity (JPA) | `{Name}Entity` | `UserEntity`, `CharacterEntity` |
| Repository Interface | `{Name}Repository` | `UserRepository` |
| Repository Impl | `{Name}RepositoryImpl` | `UserRepositoryImpl` |
| JPA Repository | `{Name}JpaRepository` | `UserJpaRepository` |
| Service | `{Name}Service` | `UserService` |
| Domain Service | `{Name}DomainService` | `UserDomainService` |
| Controller | `{Name}Controller` | `UserController` |
| DTO Request | `{Action}{Name}Request` | `CreateUserRequest` |
| DTO Response | `{Name}Response` | `UserResponse` |
| Mapper | `{Name}Mapper` | `UserMapper` |
| Exception | `{Name}Exception` | `UserNotFoundException` |
| Config | `{Name}Config` | `JpaConfig` |

---

## 레이어 아키텍처

### 레이어 구조

```
┌─────────────────────────────────────────┐
│      Presentation Layer                 │  ← REST API, gRPC
│  (Controller, Filter, Exception)        │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      Application Layer                  │  ← Use Case, Service
│  (Service, DTO, Mapper, Scheduler)      │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      Domain Layer                       │  ← Business Logic
│  (Model, VO, Repository, Service)       │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      Infrastructure Layer               │  ← 기술 구현
│  (Persistence, External, Cache)         │
└─────────────────────────────────────────┘
```

### 레이어별 책임

#### 1. Presentation Layer (프레젠테이션 레이어)

**책임:**
- HTTP 요청/응답 처리
- 입력 검증 (Validation)
- 인증/인가 (Authentication/Authorization)
- 예외 처리 (Exception Handling)

**포함 요소:**
- Controller
- Filter
- Interceptor
- Exception Handler

**규칙:**
- Application Layer에만 의존
- 비즈니스 로직 포함 금지
- DTO 변환은 Application Layer에 위임

**예시:**
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(
        @CurrentUser Long userId
    ) {
        UserResponse response = userService.getUserById(userId);
        return ApiResponse.success(response);
    }
    
    @PostMapping("/onboarding")
    public ApiResponse<Void> completeOnboarding(
        @CurrentUser Long userId,
        @Valid @RequestBody OnboardingRequest request
    ) {
        userService.completeOnboarding(userId, request);
        return ApiResponse.success();
    }
}
```

---

#### 2. Application Layer (애플리케이션 레이어)

**책임:**
- Use Case 구현
- 트랜잭션 관리
- 도메인 객체 조합
- DTO ↔ Domain 변환
- 외부 시스템 호출 조율

**포함 요소:**
- Service
- DTO (Request/Response)
- Mapper
- Use Case (선택적)
- Scheduler

**규칙:**
- Domain Layer와 Infrastructure Layer에 의존
- 비즈니스 로직은 Domain Layer에 위임
- 트랜잭션 경계 설정 (`@Transactional`)

**예시:**
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        return userMapper.toResponse(user);
    }
    
    @Transactional
    public void completeOnboarding(Long userId, OnboardingRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        UserProfile profile = userMapper.toProfile(request);
        user.completeOnboarding(profile);
        
        userRepository.save(user);
    }
}
```

---

#### 3. Domain Layer (도메인 레이어)

**책임:**
- 핵심 비즈니스 로직
- 도메인 규칙 검증
- 도메인 이벤트 발행
- 엔티티 상태 관리

**포함 요소:**
- Model (Entity)
- Value Object
- Repository Interface
- Domain Service
- Domain Event

**규칙:**
- 다른 레이어에 의존하지 않음 (순수 Java)
- 비즈니스 로직은 도메인 객체 내부에 캡슐화
- 인프라 기술 의존 금지 (JPA 어노테이션 금지)

**예시:**
```java
public class User {
    
    private Long id;
    private Email email;
    private DisplayName displayName;
    private StarPieces starPieces;
    private UserProfile profile;
    
    // 비즈니스 로직
    public void completeOnboarding(UserProfile profile) {
        if (this.profile != null && this.profile.isSurveyCompleted()) {
            throw new OnboardingAlreadyCompletedException();
        }
        
        this.profile = profile;
        this.profile.completeSurvey();
    }
    
    public void earnStarPieces(int amount, String source) {
        if (amount <= 0) {
            throw new InvalidStarPieceAmountException(amount);
        }
        
        this.starPieces = this.starPieces.add(amount);
        
        // 도메인 이벤트 발행 (향후)
        // DomainEvents.raise(new StarPiecesEarnedEvent(this.id, amount, source));
    }
    
    public void spendStarPieces(int amount, String purpose) {
        if (amount <= 0) {
            throw new InvalidStarPieceAmountException(amount);
        }
        
        if (!this.starPieces.canSpend(amount)) {
            throw new InsufficientStarPiecesException(this.starPieces.getValue(), amount);
        }
        
        this.starPieces = this.starPieces.subtract(amount);
    }
}
```

---

#### 4. Infrastructure Layer (인프라 레이어)

**책임:**
- 데이터베이스 접근
- 외부 API 호출
- 캐시 관리
- 파일 저장소 접근
- 메시지 큐 연동

**포함 요소:**
- JPA Entity
- Repository Implementation
- External Client
- Cache Repository
- Config

**규칙:**
- Domain Layer의 인터페이스 구현
- 기술 세부사항 캡슐화
- Domain 객체 ↔ Infrastructure 객체 변환

**예시:**
```java
// JPA Entity
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String displayName;
    
    @Column(nullable = false)
    private Integer starPieces;
    
    // ... 기타 필드
}

// Repository Implementation
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    
    private final UserJpaRepository jpaRepository;
    private final UserEntityMapper entityMapper;
    
    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id)
            .map(entityMapper::toDomain);
    }
    
    @Override
    public User save(User user) {
        UserEntity entity = entityMapper.toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return entityMapper.toDomain(saved);
    }
}
```

---

### 레이어 간 의존성 규칙

```
Presentation → Application → Domain ← Infrastructure
                                ↑
                                └─── (인터페이스 구현)
```

**규칙:**
1. **상위 레이어는 하위 레이어에만 의존**
   - Presentation → Application
   - Application → Domain
   - Infrastructure → Domain (인터페이스 구현)

2. **Domain Layer는 독립적**
   - 다른 레이어에 의존하지 않음
   - 순수 Java 코드

3. **Infrastructure는 Domain 인터페이스 구현**
   - Repository 인터페이스는 Domain에 정의
   - 구현체는 Infrastructure에 위치

---

## 의존성 관리

### 루트 build.gradle

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.5' apply false
    id 'io.spring.dependency-management' version '1.1.4' apply false
    id 'com.google.protobuf' version '0.9.4' apply false
}

allprojects {
    group = 'p5laris'
    version = '0.0.1-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'org.springframework.boot'
    apply plugin: 'io.spring.dependency-management'
    
    java {
        sourceCompatibility = '17'
        targetCompatibility = '17'
    }
    
    configurations {
        compileOnly {
            extendsFrom annotationProcessor
        }
    }
    
    dependencies {
        // Spring Boot
        implementation 'org.springframework.boot:spring-boot-starter-web'
        implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
        implementation 'org.springframework.boot:spring-boot-starter-validation'
        implementation 'org.springframework.boot:spring-boot-starter-security'
        implementation 'org.springframework.boot:spring-boot-starter-data-redis'
        
        // Database
        runtimeOnly 'org.postgresql:postgresql'
        implementation 'org.flywaydb:flyway-core'
        
        // Lombok
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        
        // MapStruct
        implementation 'org.mapstruct:mapstruct:1.5.5.Final'
        annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
        
        // Test
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
        testImplementation 'org.springframework.security:spring-security-test'
    }
    
    tasks.named('test') {
        useJUnitPlatform()
    }
}
```

---

### 모듈별 build.gradle 예시

#### common/build.gradle

```gradle
dependencies {
    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'
    
    // OAuth2
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    
    // Swagger
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
}
```

#### user/build.gradle

```gradle
dependencies {
    // Common 모듈
    implementation project(':common')
    implementation project(':proto')
    
    // gRPC
    implementation 'net.devh:grpc-spring-boot-starter:2.15.0.RELEASE'
}
```

#### character/build.gradle

```gradle
dependencies {
    // Common 모듈
    implementation project(':common')
    implementation project(':proto')
    
    // gRPC
    implementation 'net.devh:grpc-spring-boot-starter:2.15.0.RELEASE'
    
    // AWS S3 (공유 카드 이미지 저장)
    implementation 'software.amazon.awssdk:s3:2.20.0'
}
```

#### mission/build.gradle

```gradle
dependencies {
    // Common 모듈
    implementation project(':common')
    implementation project(':proto')
    
    // gRPC
    implementation 'net.devh:grpc-spring-boot-starter:2.15.0.RELEASE'
    
    // HTTP Client (날씨 API)
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

#### ai/build.gradle

```gradle
dependencies {
    // Common 모듈
    implementation project(':common')
    
    // OpenAI API
    implementation 'com.theokanning.openai-gpt3-java:service:0.18.2'
    
    // HTTP Client
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    
    // Retry
    implementation 'org.springframework.retry:spring-retry'
}
```

---

## 설정 파일 관리

### application.yaml 구조

#### common/src/main/resources/application-common.yaml

```yaml
# 공통 설정
spring:
  application:
    name: polaris
  
  # JPA 설정
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        default_batch_fetch_size: 100
  
  # Redis 설정
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 10
          min-idle: 2
  
  # Jackson 설정
  jackson:
    serialization:
      write-dates-as-timestamps: false
    time-zone: Asia/Seoul
    default-property-inclusion: non_null

# Logging
logging:
  level:
    root: INFO
    p5laris: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

# JWT 설정
jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 3600000    # 1시간
  refresh-token-expiration: 2592000000 # 30일

# Swagger
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
```

---

#### user/src/main/resources/application.yaml

```yaml
spring:
  config:
    import: classpath:application-common.yaml
  
  application:
    name: user-service
  
  # Database
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:polaris_user}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  # Flyway
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: public

# Server
server:
  port: 8081
  servlet:
    context-path: /api/v1

# gRPC
grpc:
  server:
    port: 9091
  client:
    character-service:
      address: static://localhost:9092
      negotiation-type: plaintext

# OAuth2
oauth2:
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: ${GOOGLE_REDIRECT_URI}
  apple:
    client-id: ${APPLE_CLIENT_ID}
    team-id: ${APPLE_TEAM_ID}
    key-id: ${APPLE_KEY_ID}
    private-key: ${APPLE_PRIVATE_KEY}
```

---

#### character/src/main/resources/application.yaml

```yaml
spring:
  config:
    import: classpath:application-common.yaml
  
  application:
    name: character-service
  
  # Database
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:polaris_character}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
  
  # Flyway
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: public

# Server
server:
  port: 8082
  servlet:
    context-path: /api/v1

# gRPC
grpc:
  server:
    port: 9092
  client:
    user-service:
      address: static://localhost:9091
      negotiation-type: plaintext

# AWS S3 (공유 카드 이미지)
aws:
  s3:
    bucket: ${AWS_S3_BUCKET:polaris-share-cards}
    region: ${AWS_REGION:ap-northeast-2}
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}

# Character State Scheduler
character:
  state:
    decay:
      enabled: true
      cron: "0 0 * * * *"  # 매 시간 정각
      fullness-rate: -5
      energy-rate: -3
      affection-rate: -1
```

---

#### mission/src/main/resources/application.yaml

```yaml
spring:
  config:
    import: classpath:application-common.yaml
  
  application:
    name: mission-service
  
  # Database
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:polaris_mission}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
  
  # Flyway
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: public

# Server
server:
  port: 8083
  servlet:
    context-path: /api/v1

# gRPC
grpc:
  server:
    port: 9093
  client:
    user-service:
      address: static://localhost:9091
      negotiation-type: plaintext
    character-service:
      address: static://localhost:9092
      negotiation-type: plaintext
    ai-service:
      address: static://localhost:9096
      negotiation-type: plaintext

# Weather API
weather:
  api:
    url: ${WEATHER_API_URL}
    key: ${WEATHER_API_KEY}
    timeout: 3000

# Mission Scheduler
mission:
  offer:
    enabled: true
    cron: "0 0 9,15,20 * * *"  # 오전 9시, 오후 3시, 저녁 8시
  daily-limit: 15
```

---

#### ai/src/main/resources/application.yaml

```yaml
spring:
  config:
    import: classpath:application-common.yaml
  
  application:
    name: ai-service

# Server
server:
  port: 8086

# gRPC
grpc:
  server:
    port: 9096

# OpenAI
openai:
  api:
    key: ${OPENAI_API_KEY}
    model: gpt-4
    timeout: 5000
    max-retries: 2
    retry-delay: 500

# AI Generation
ai:
  generation:
    enabled: true
    fallback-rate: 0.5  # 50% Fallback 사용 (비용 절감)
    max-length: 50
    temperature: 0.7
  
  validation:
    enabled: true
    forbidden-keywords:
      - "죽"
      - "자해"
      - "게으르"
      - "나태"
```

---

#### notification/src/main/resources/application.yaml

```yaml
spring:
  config:
    import: classpath:application-common.yaml
  
  application:
    name: notification-service
  
  # Database
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:polaris_notification}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
  
  # Flyway
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: public

# Server
server:
  port: 8087
  servlet:
    context-path: /api/v1

# gRPC
grpc:
  server:
    port: 9097

# Firebase Cloud Messaging
fcm:
  credentials-path: classpath:firebase-service-account.json
  project-id: ${FCM_PROJECT_ID}

# Apple Push Notification
apns:
  key-id: ${APNS_KEY_ID}
  team-id: ${APNS_TEAM_ID}
  topic: ${APNS_TOPIC}
  private-key-path: classpath:apns-private-key.p8
  production: false

# Notification Scheduler
notification:
  daily-reminder:
    enabled: true
    cron: "0 0 9 * * *"  # 매일 오전 9시
```

---

### 환경별 설정 파일

#### application-dev.yaml (개발 환경)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  
  flyway:
    enabled: false

logging:
  level:
    root: DEBUG
    p5laris: DEBUG

# 개발 환경 데이터베이스
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/polaris_dev
```

---

#### application-prod.yaml (운영 환경)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  
  flyway:
    enabled: true

logging:
  level:
    root: INFO
    p5laris: INFO

# 운영 환경 데이터베이스
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

# 운영 환경 보안 설정
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEY_STORE_PASSWORD}
    key-store-type: PKCS12
```

---

## 데이터베이스 마이그레이션

### Flyway 마이그레이션 파일 구조

```
user/src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_user_profiles_table.sql
├── V3__create_user_sessions_table.sql
├── V4__create_star_piece_transactions_table.sql
├── V5__add_indexes_to_users.sql
└── V6__insert_initial_data.sql
```

### 마이그레이션 파일 네이밍 규칙

```
V{version}__{description}.sql
```

- **V**: Version (필수)
- **version**: 버전 번호 (1, 2, 3, ...)
- **__**: 언더스코어 2개 (필수)
- **description**: 설명 (snake_case)

### 마이그레이션 파일 예시

#### V1__create_users_table.sql

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    profile_image_url TEXT,
    auth_provider VARCHAR(50) NOT NULL,
    auth_provider_id VARCHAR(255) NOT NULL,
    star_pieces INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    
    CONSTRAINT uk_auth_provider UNIQUE (auth_provider, auth_provider_id)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NULL;

COMMENT ON TABLE users IS '사용자 기본 정보';
COMMENT ON COLUMN users.star_pieces IS '보유 별조각';
```

---

## 코드 작성 가이드

### Entity 작성 예시

#### Domain Entity (순수 Java)

```java
package p5laris.user.domain.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.user.domain.vo.Email;
import p5laris.user.domain.vo.DisplayName;
import p5laris.user.domain.vo.StarPieces;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    
    private Long id;
    private Email email;
    private DisplayName displayName;
    private String profileImageUrl;
    private String authProvider;
    private String authProviderId;
    private StarPieces starPieces;
    private UserProfile profile;
    
    @Builder
    public User(
        Long id,
        Email email,
        DisplayName displayName,
        String profileImageUrl,
        String authProvider,
        String authProviderId,
        StarPieces starPieces,
        UserProfile profile
    ) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.authProvider = authProvider;
        this.authProviderId = authProviderId;
        this.starPieces = starPieces != null ? starPieces : StarPieces.zero();
        this.profile = profile;
    }
    
    // 비즈니스 로직
    public void completeOnboarding(UserProfile profile) {
        if (this.profile != null && this.profile.isSurveyCompleted()) {
            throw new OnboardingAlreadyCompletedException();
        }
        this.profile = profile;
        this.profile.completeSurvey();
    }
    
    public void earnStarPieces(int amount, String source) {
        validateStarPieceAmount(amount);
        this.starPieces = this.starPieces.add(amount);
    }
    
    public void spendStarPieces(int amount, String purpose) {
        validateStarPieceAmount(amount);
        if (!this.starPieces.canSpend(amount)) {
            throw new InsufficientStarPiecesException(
                this.starPieces.getValue(), 
                amount
            );
        }
        this.starPieces = this.starPieces.subtract(amount);
    }
    
    private void validateStarPieceAmount(int amount) {
        if (amount <= 0) {
            throw new InvalidStarPieceAmountException(amount);
        }
    }
}
```

#### JPA Entity

```java
package p5laris.user.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    
    @Column(name = "profile_image_url")
    private String profileImageUrl;
    
    @Column(name = "auth_provider", nullable = false, length = 50)
    private String authProvider;
    
    @Column(name = "auth_provider_id", nullable = false)
    private String authProviderId;
    
    @Column(name = "star_pieces", nullable = false)
    private Integer starPieces = 0;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    // Builder 패턴
    public static UserEntity of(
        String email,
        String displayName,
        String profileImageUrl,
        String authProvider,
        String authProviderId
    ) {
        UserEntity entity = new UserEntity();
        entity.email = email;
        entity.displayName = displayName;
        entity.profileImageUrl = profileImageUrl;
        entity.authProvider = authProvider;
        entity.authProviderId = authProviderId;
        entity.starPieces = 0;
        return entity;
    }
    
    // 업데이트 메서드
    public void updateStarPieces(Integer starPieces) {
        this.starPieces = starPieces;
    }
    
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
```

---

### Repository 작성 예시

#### Repository Interface (Domain)

```java
package p5laris.user.domain.repository;

import p5laris.user.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    
    Optional<User> findById(Long id);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByAuthProvider(String provider, String providerId);
    
    User save(User user);
    
    void delete(User user);
    
    boolean existsByEmail(String email);
}
```

#### Repository Implementation (Infrastructure)

```java
package p5laris.user.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import p5laris.user.domain.model.User;
import p5laris.user.domain.repository.UserRepository;
import p5laris.user.infrastructure.persistence.entity.UserEntity;
import p5laris.user.infrastructure.persistence.mapper.UserEntityMapper;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    
    private final UserJpaRepository jpaRepository;
    private final UserEntityMapper entityMapper;
    
    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id)
            .map(entityMapper::toDomain);
    }
    
    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
            .map(entityMapper::toDomain);
    }
    
    @Override
    public Optional<User> findByAuthProvider(String provider, String providerId) {
        return jpaRepository.findByAuthProviderAndAuthProviderId(provider, providerId)
            .map(entityMapper::toDomain);
    }
    
    @Override
    public User save(User user) {
        UserEntity entity = entityMapper.toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return entityMapper.toDomain(saved);
    }
    
    @Override
    public void delete(User user) {
        jpaRepository.deleteById(user.getId());
    }
    
    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
```

#### JPA Repository

```java
package p5laris.user.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import p5laris.user.infrastructure.persistence.entity.UserEntity;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    
    Optional<UserEntity> findByEmail(String email);
    
    Optional<UserEntity> findByAuthProviderAndAuthProviderId(
        String authProvider, 
        String authProviderId
    );
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM UserEntity u WHERE u.deletedAt IS NULL AND u.id = :id")
    Optional<UserEntity> findActiveById(Long id);
}
```

---

## 📚 참고 문서

- [05. ERD (Entity-Relationship Diagram)](./05-ERD(Entity-Relationship-Diagram).md)
- [06. REST API 명세서](./06-API-spec.md)
- [10. Architecture Decision Records](./10-ADR.md)

---

## 📝 문서 히스토리

| 버전 | 날짜 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v1.0 | 2026-05-14 | Backend Team | 초기 작성 |

---

**문서 끝**
