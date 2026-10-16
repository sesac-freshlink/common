# common

> 여러 서비스가 **같이 쓰는 코드**를 모은 라이브러리. 쪽지(메시지) DTO 7종과 공통 오류 응답이 들어 있고, v0.2.0부터 Outbox 발행기와 멱등 처리가 들어온다. GitHub Packages에 버전을 붙여 올리고, 각 서비스가 가져다 쓴다.

| 항목 | 내용 |
| --- | --- |
| 담당 | 김진아 |
| 관련 기능 ID | E-03 Outbox 발행기, E-04 멱등 처리 |
| 기술 | Java 21, Jackson 3, 버전은 Spring Boot 4.0.8 BOM으로 맞춤(라이브러리, 실행 jar 없음). **Spring 의존성 없음.** v0.2.0(Outbox·멱등)부터 Spring Data JPA, Spring Cloud AWS 4.x (SQS) |
| 배포 | `v*` 태그 → GitHub Packages (Maven) `com.freshlink:common:<버전>` |
| 사용처 | inventory-service, order-service, member-service(오류 응답), logistics-simulator(DTO) |

---

## 1. 왜 별도 레포인가

inventory-service, order-service, logistics-simulator가 SQS로 쪽지를 주고받는다. 쪽지 클래스를 각자 만들면 필드가 어긋나서 서로 못 읽는다. 그래서 여기 한 번만 만들고 세 서비스가 같은 버전을 가져다 쓴다. Outbox·멱등 코드도 같은 이유로 여기 둔다.

## 2. 들어 있는 것 (v0.1.0)

### 쪽지 DTO 7종 (`com.freshlink.common.message`)

형식의 진실은 .github 레포 `docs/백엔드-공통작업-인수인계.md` 3-3이다. 모든 쪽지는 JSON이고 공통으로 `messageId`(UUID), `type`, `createdAt`(ISO-8601 `+09:00`)을 가진다.

| 큐 | type | 클래스 | 보내는 곳 |
| --- | --- | --- | --- |
| `inventory-commands.fifo` (그룹 = storeId) | INBOUND | `InboundMessage` | logistics-simulator |
| | RESERVE, CONFIRM, RELEASE | `ReserveMessage`, `ConfirmMessage`, `ReleaseMessage` | order-service |
| | DISPOSE | `DisposeMessage` | inventory-service |
| `inventory-results` | RESERVED, RESERVE_FAILED | `ReservedMessage`, `ReserveFailedMessage` | inventory-service |

```java
// 보내기: 팩토리가 messageId와 createdAt을 채운다
ReserveMessage m = ReserveMessage.of(orderId, storeId, productId, quantity);
String body = MessageJson.toJson(m);
m.messageGroupId();      // FIFO MessageGroupId = "1" (storeId 문자열)
m.deduplicationId();     // FIFO MessageDeduplicationId = messageId

// 받기: BaseMessage로 읽으면 type에 맞는 클래스가 나온다
BaseMessage received = MessageJson.fromJson(body);
if (received instanceof ReserveMessage r) { ... }
```

- 모르는 필드가 와도 읽힌다 (필드를 추가해도 옛 버전이 깨지지 않는다).
- 유통기한 `expiryDate`에 날짜만 오면(`"2026-10-20"`) 그날 23:59:59 한국 시간으로 읽는다.
- 서비스 안에서는 Spring이 만든 ObjectMapper(`spring.jackson.time-zone: Asia/Seoul`)로 읽고 써도 같다. 규칙이 DTO 애너테이션에 있다.

### 공통 오류 응답 (`com.freshlink.common.error`)

| 클래스 | 역할 |
| --- | --- |
| `ErrorCode` (interface) | 서비스마다 enum으로 구현한다. `status()`, `message()`. `code()`는 enum 이름 |
| `BusinessException` | 서비스 코드에서 `throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)` |
| `ErrorResponse` (record) | 모든 서비스의 응답 모양 `{"code", "message", "traceId"}` |

핸들러(`GlobalExceptionHandler`)는 각 서비스 레포에 있다. 이유는 .github 레포 `docs/백엔드-공통-규칙.md` 6장.

### v0.2.0에 들어올 것 (김진아)

| 기능 | 표 | 요약 |
| --- | --- | --- |
| Outbox 발행기 (E-03) | `outbox` (id, queue_name, group_id, payload, published, created_at) | 쪽지를 같은 트랜잭션으로 outbox에 적고, 발행기가 1초마다 SQS로 보낸다 |
| 멱등 처리 (E-04) | `processed_message` (message_id PK, processed_at) | 처리한 messageId를 기록해 같은 쪽지를 두 번 반영하지 않는다 |

## 3. 폴더 구조

```
common/
├── build.gradle                       # java-library + maven-publish. 버전은 태그에서
├── .github/workflows/publish.yml      # v* 태그 → 테스트 → GitHub Packages 배포
├── src/main/java/com/freshlink/common/
│   ├── message/       # BaseMessage, StoreScoped, MessageType, MessageJson (공통 뼈대와 변환기)
│   │   ├── command/   # inventory-commands.fifo로 가는 것: Inbound(+InboundItem)·Reserve·Confirm·Release(+ReleaseReason)·Dispose
│   │   ├── result/    # inventory-results로 가는 것: Reserved(+ReservedLot)·ReserveFailed(+ReserveFailReason)
│   │   └── json/      # ExpiryDateDeserializer
│   ├── error/         # ErrorCode, BusinessException, ErrorResponse
│   ├── outbox/        # (v0.2.0) Outbox 엔티티, 저장 헬퍼, 발행기
│   └── idempotency/   # (v0.2.0) processed_message 기록·확인
└── src/test/java/com/freshlink/common/
    ├── message/MessageJsonTest.java   # 7종 왕복, 예시 JSON, 유통기한, 모르는 필드, 그룹 ID
    └── error/ErrorResponseTest.java   # 세 키, enum 이름 = code
```

## 4. 서비스에서 가져다 쓰기

GitHub Packages는 읽기에도 토큰이 필요하다. 각자 PC 환경 변수에 `GITHUB_ACTOR`(GitHub 아이디), `GITHUB_TOKEN`(`read:packages` 권한 PAT)을 둔다. GitHub Actions 안에서는 기본 제공된다.

```groovy
// build.gradle (member-service, order-service, inventory-service, logistics-simulator)
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/sesac-freshlink/common")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation "com.freshlink:common:0.1.0"
}
```

**태그를 달기 전에 내 PC에서 시험하기:** 이 레포에서 아래를 실행하면 `~/.m2`에 `0.1.0`이 올라간다. 서비스 쪽 `build.gradle`에는 `mavenLocal()`을 커밋하지 말고, 검증할 때만 init 스크립트나 로컬 수정으로 넣는다.

```bash
GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.1.0 ./gradlew publishToMavenLocal
```

## 5. 버전 규칙

- SemVer 태그 `vMAJOR.MINOR.PATCH` (예: `v0.1.0` → `v0.2.0`). 태그를 push하면 `publish.yml`이 테스트 뒤 GitHub Packages에 배포한다. 태그는 사람이 단다.
- 버전은 `build.gradle`이 태그 이름에서 읽는다 (`v0.1.0` → `0.1.0`). 태그 빌드가 아니면 `0.1.0-SNAPSHOT`.
- 0.x 동안은 쪽지 형식이 바뀔 수 있다. 필드 추가 = MINOR, 삭제·이름·타입 변경 = 호환이 깨진다. **버전을 올릴 때 이태우·김경우에게 알린다.**

## 6. 빌드하기

```bash
./gradlew build                 # 컴파일 + 테스트 (Windows: gradlew.bat build)
./gradlew publishToMavenLocal   # ~/.m2에 0.1.0-SNAPSHOT
```

## 7. 브랜치와 기여

- `main`(배포) ← `dev`(통합, 기본 브랜치) ← `feat/<기능ID>-<설명>` (예: `feat/E-03-outbox-publisher`)
- 쪽지 형식을 바꾸는 PR은 `message-spec` 라벨을 달고 합치기 전에 이태우·김경우에게 꼭 알린다.

자세한 규칙은 [.github 레포](https://github.com/sesac-freshlink/.github)를 참고한다.
