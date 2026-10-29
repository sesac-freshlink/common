# common

> 여러 서비스가 **같이 쓰는 코드**를 모은 라이브러리. 쪽지(메시지) DTO 7종, 공통 오류 응답, Outbox 발행기, 멱등 처리가 들어 있다. GitHub Packages에 버전을 붙여 올리고, 각 서비스가 가져다 쓴다.

| 항목 | 내용 |
| --- | --- |
| 담당 | 김진아 |
| 관련 기능 ID | E-03 Outbox 발행기(v0.4.0 보낸 줄 정리), E-04 멱등 처리, I-03 관측성(Outbox 추적 잇기, v0.3.0) |
| 기술 | Java 21, Jackson 3, 버전은 Spring Boot 4.0.8 BOM으로 맞춤(라이브러리, 실행 jar 없음). Outbox·멱등(v0.2.0)은 Spring JDBC, Spring Cloud AWS 4.2.0 (SQS), ShedLock 7.10.1을, 추적 잇기(v0.3.0)는 Micrometer Tracing을 쓰지만 전부 `compileOnly`라 쓰지 않는 서비스에는 끌려가지 않는다 |
| 배포 | `v*` 태그 → GitHub Packages (Maven) `com.freshlink:common:<버전>` |
| 사용처 | inventory-service, order-service, member-service(오류 응답), logistics-simulator(DTO) |

---

## 1. 왜 별도 레포인가

inventory-service, order-service, logistics-simulator가 SQS로 쪽지를 주고받는다. 쪽지 클래스를 각자 만들면 필드가 어긋나서 서로 못 읽는다. 그래서 여기 한 번만 만들고 세 서비스가 같은 버전을 가져다 쓴다. Outbox·멱등 코드도 같은 이유로 여기 둔다.

## 2. 들어 있는 것 (v0.4.0)

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

### Outbox 발행기 (E-03, `com.freshlink.common.outbox`)와 멱등 처리 (E-04, `com.freshlink.common.idempotency`)

DB를 바꾸고 쪽지를 보내는 서비스(inventory, order)가 쓴다. 설계와 이유는 .github 레포 `docs/common-v0.2.0-인수인계.md`.

| 클래스 | 하는 일 |
| --- | --- |
| `OutboxWriter.save(queueName, message)` | 쪽지를 비즈니스 변경과 **같은 트랜잭션**으로 `outbox` 표에 적는다. 트랜잭션 밖에서 부르면 예외(전파 MANDATORY와 같음). `.fifo` 큐면 `group_id = storeId` |
| `OutboxPublisher` | `publish-interval`(기본 1초)마다 안 보낸 행을 `id` 순으로 최대 `batch-size`(기본 100)개 꺼내 `SqsTemplate`으로 보낸다. FIFO면 그룹 ID = storeId, 중복 제거 ID = messageId. ShedLock 잠금 `freshlink-outbox-publisher`로 한 번에 한 파드만 보낸다. 보내다 실패하면 그 회차를 멈추고 다음 회차에 그 행부터 다시 보낸다(점포 안 순서 유지) |
| `MessageCleaner` (v0.4.0) | `interval`(기본 1시간)마다 보낸 지 `outbox-retention`(기본 7일) 넘은 `outbox` 행(`published = TRUE`)과 `processed-retention`(기본 30일) 넘은 `processed_message` 행을 `batch-size`(기본 1000)개씩 나눠 지운다. 안 보낸 행은 지우지 않는다. ShedLock 잠금 `freshlink-message-cleanup`(발행기와 다른 이름이라 지우는 동안에도 발행은 계속된다)으로 한 파드만 지운다. `processed-retention`은 SQS 본 큐 4일 + DLQ 14일 = 18일보다 길어야 한다(짧으면 DLQ에서 재처리한 쪽지를 두 번 반영한다). 표 변경·Flyway 파일은 없다 |
| `ProcessedMessages.markIfNew(messageId)` | 처음 보는 messageId면 `processed_message`에 적고 `true`, 이미 있으면 `false`. 쪽지 처리와 **같은 트랜잭션**이어야 한다(밖에서 부르면 예외). `INSERT IGNORE`의 행 수로 판단해서 중복이어도 트랜잭션이 롤백 전용으로 바뀌지 않는다 |

### Outbox 추적 잇기 (v0.3.0, I-03, `com.freshlink.common.tracing`)

Outbox 발행기는 요청과 다른 스레드에서 보내서, 그냥 두면 SQS 너머 처리(inventory의 예약 등)가 새 traceId로 시작한다. v0.3.0은 Zipkin·Grafana에서 주문 요청 → RESERVE 쪽지 → inventory 처리가 **traceId 하나**로 보이게 잇는다.

| 언제 | 하는 일 |
| --- | --- |
| `OutboxWriter.save` | 지금 요청의 추적을 W3C `traceparent`(`00-<traceId>-<spanId>-01`)로 `outbox.trace_parent`에 적는다. 추적 중이 아니면 NULL |
| `OutboxPublisher` 전송 | 행의 `trace_parent`를 부모로 `outbox publish` 구간을 열고, SQS 메시지 속성 `traceparent`를 붙여 보낸다. NULL인 행(v0.2.0으로 적은 행)은 예전처럼 그냥 보낸다 |
| 받는 쪽 `@SqsListener` | `spring.cloud.aws.sqs.observation-enabled: true`면 Spring Cloud AWS가 `traceparent` 속성을 읽어 같은 추적을 잇는다(4-3) |

Micrometer Tracing(서비스의 `spring-boot-starter-zipkin`)이 클래스 경로에 있을 때만 켜진다. 없으면 `trace_parent`는 NULL이고 나머지는 v0.2.0과 같다. `trace_parent` 칸은 v0.2.0 표에 이미 있어서 **Flyway 파일을 새로 만들 필요가 없다**.

Outbox는 "적어도 한 번" 보낸다. 보낸 뒤 `published = true`로 바꾸기 전에 파드가 죽으면 같은 쪽지가 한 번 더 나간다. 그래서 받는 쪽은 꼭 `markIfNew`로 거른다. 둘이 짝을 이뤄 "정확히 한 번 반영"이 된다.

`freshlink.messaging.enabled=true`일 때만 빈이 생긴다(4-3). member-service, logistics-simulator는 켜지 않는다.

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
│   ├── outbox/        # OutboxWriter(저장), OutboxPublisher(발행기), OutboxProperties
│   ├── idempotency/   # ProcessedMessages (processed_message 기록·확인)
│   ├── tracing/       # OutboxTracing, MicrometerOutboxTracing (v0.3.0, Outbox 추적 잇기)
│   └── autoconfigure/ # MessagingAutoConfiguration (freshlink.messaging.enabled=true일 때만)
├── src/main/resources/META-INF/spring/…AutoConfiguration.imports
└── src/test/java/com/freshlink/common/
    ├── message/MessageJsonTest.java   # 7종 왕복, 예시 JSON, 유통기한, 모르는 필드, 그룹 ID
    ├── error/ErrorResponseTest.java   # 세 키, enum 이름 = code
    ├── outbox/                        # 저장·롤백·트랜잭션 밖 예외·그룹 ID, 발행 순서·실패 시 멈춤·잠금, traceparent 속성
    ├── tracing/                       # traceparent 만들기·잇기 (Brave, 서비스와 같은 W3C 형식)
    ├── idempotency/                   # 두 번째는 false, 롤백 뒤 다시 true, 중복이어도 커밋
    └── autoconfigure/                 # 꺼져 있으면 빈 없음, LockProvider 없으면 기동 실패
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
    implementation "com.freshlink:common:0.3.0"
}
```

**태그를 달기 전에 시험하기:** 이 레포에서 아래를 실행하면 `~/.m2`에 `0.3.0`이 올라간다. 서비스 쪽 `build.gradle`에는 `mavenLocal()`을 커밋하지 말고, 검증할 때만 init 스크립트나 로컬 수정으로 넣는다.

```bash
GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.3.0 ./gradlew publishToMavenLocal
```

### 4-1. Outbox·멱등을 쓰는 서비스에 더 필요한 의존성 (inventory, order)

common은 아래를 `compileOnly`로만 갖고 있다. 쓰는 서비스가 직접 넣는다. 버전이 없는 줄은 Boot 4.0.8 BOM이 맞춘다.

```groovy
dependencies {
    implementation "com.freshlink:common:0.3.0"
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'                 // JPA starter를 쓰면 이미 들어 있다
    implementation platform('io.awspring.cloud:spring-cloud-aws-dependencies:4.2.0')
    implementation 'io.awspring.cloud:spring-cloud-aws-starter-sqs'                    // SqsTemplate, @SqsListener
    implementation 'net.javacrumbs.shedlock:shedlock-spring:7.10.1'
    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1'   // LockProvider
    implementation 'org.springframework.boot:spring-boot-starter-zipkin'               // 추적 (v0.3.0 Outbox 추적 잇기). 모든 서비스에 들어간다 (할 일 16)
}
```

ShedLock `LockProvider` 빈이 꼭 있어야 한다. 없으면 앱이 뜰 때 실패한다(파드 두 개가 같은 쪽지를 같이 보내는 것을 막으려는 것).

```java
@Bean
LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
}
```

서비스 IRSA 역할에 보낼 큐의 `sqs:SendMessage`, `sqs:GetQueueUrl`, `sqs:GetQueueAttributes` 권한이 있어야 한다(infra 담당 김진아가 맞춘다).

### 4-2. 표 만들기 (각 서비스 Flyway)

common은 Flyway 파일을 들고 다니지 않는다. 아래를 자기 레포에 `V<n>__outbox_processed_message.sql`로 그대로 넣는다. `shedlock` 표가 아직 없으면 같이 넣는다.

```sql
CREATE TABLE outbox (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id   VARCHAR(36)  NOT NULL,          -- 쪽지 messageId. FIFO 중복 제거 ID로도 쓴다
    message_type VARCHAR(30)  NOT NULL,          -- RESERVE, RESERVED … (로그·조회용)
    queue_name   VARCHAR(200) NOT NULL,          -- 실제 큐 이름. 예: dev-inventory-commands.fifo
    group_id     VARCHAR(64)  NULL,              -- FIFO면 storeId 문자열, 표준 큐면 NULL
    payload      TEXT         NOT NULL,          -- MessageJson.toJson(쪽지)
    trace_parent VARCHAR(55)  NULL,              -- v0.3.0에서 채운다. v0.2.0은 항상 NULL
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)  NOT NULL,
    published_at DATETIME(6)  NULL,
    UNIQUE KEY uk_outbox_message_id (message_id),
    KEY idx_outbox_unpublished (published, id)
);

CREATE TABLE processed_message (
    message_id   VARCHAR(36) PRIMARY KEY,
    processed_at DATETIME(6) NOT NULL
);

-- ShedLock (없으면)
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL
);
```

### 4-3. 켜기 (`application.yml`)

```yaml
freshlink:
  messaging:
    enabled: true            # 기본 false. inventory·order만 켠다
    outbox:
      publish-interval: 1s   # 기본 1s
      batch-size: 100        # 기본 100
    cleanup:                 # v0.4.0. 보낸 줄·처리 기록 정리. 기본값이면 적지 않아도 된다
      enabled: true          # 기본 true
      interval: 1h           # 기본 1h
      outbox-retention: 7d   # 기본 7d. 보낸 지 이만큼 지난 outbox 행을 지운다
      processed-retention: 30d   # 기본 30d. 18일(SQS 본 큐 + DLQ)보다 길어야 한다
      batch-size: 1000       # 기본 1000

spring:
  cloud:
    aws:
      sqs:
        observation-enabled: true   # v0.3.0: 받는 쪽이 traceparent 속성으로 추적을 잇는다. 보내는 SqsTemplate도 구간을 남긴다
```

### 4-4. 코드에서 쓰는 모습

큐 이름은 코드에 쓰지 않고 설정 값으로 받는다(`@Value("${app.queue.commands}")`).

```java
// 받기: 리스너는 얇게, 처리는 @Transactional 서비스 메서드에서
@SqsListener("${app.queue.commands}")
void onCommand(String body) {
    reservationService.handle(MessageJson.fromJson(body));
}

@Transactional
public void handle(BaseMessage message) {
    if (!processedMessages.markIfNew(message.messageId())) {
        return;                                   // 이미 처리한 쪽지
    }
    if (message instanceof ReserveMessage r) {
        // FEFO로 재고를 잡는다 …
        outboxWriter.save(resultsQueue, ReservedMessage.of(/* … */));   // 같은 트랜잭션
    }
}

// 보내기만 할 때 (order: 주문 저장 + RESERVE)
@Transactional
public Order create(...) {
    Order order = orderRepository.save(...);
    outboxWriter.save(commandsQueue, ReserveMessage.of(order.getId(), storeId, productId, quantity));
    return order;
}
```

처리 중 예외가 나면 트랜잭션이 롤백되고(처리 기록도 같이), 쪽지는 확인되지 않아 SQS가 다시 보낸다. 계속 실패하면 DLQ로 간다.

## 5. 버전 규칙

- SemVer 태그 `vMAJOR.MINOR.PATCH` (예: `v0.1.0` → `v0.2.0`). 태그를 push하면 `publish.yml`이 테스트 뒤 GitHub Packages에 배포한다. 태그는 사람이 단다.
- 버전은 `build.gradle`이 태그 이름에서 읽는다 (`v0.3.0` → `0.3.0`). 태그 빌드가 아니면 `0.3.0-SNAPSHOT`.
- 0.x 동안은 쪽지 형식이 바뀔 수 있다. 필드 추가 = MINOR, 삭제·이름·타입 변경 = 호환이 깨진다. **버전을 올릴 때 이태우·김경우에게 알린다.**

## 6. 빌드하기

```bash
./gradlew build                 # 컴파일 + 테스트 (Windows: gradlew.bat build)
./gradlew publishToMavenLocal   # ~/.m2에 0.3.0-SNAPSHOT
```

Outbox·멱등 테스트는 MySQL이 필요하다. 기본은 Testcontainers가 Docker로 MySQL 8.4를 띄운다(GitHub Actions에는 Docker가 있다). Docker가 없으면 이미 떠 있는 MySQL을 환경 변수로 준다. 테스트가 그 DB의 `outbox`·`processed_message`·`shedlock` 표를 지우고 다시 만들므로 **시험 전용 스키마**만 쓴다.

```bash
FRESHLINK_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/freshlink_test \
FRESHLINK_TEST_MYSQL_USERNAME=test FRESHLINK_TEST_MYSQL_PASSWORD=test ./gradlew build
```

## 7. 브랜치와 기여

- `main`(배포) ← `dev`(통합, 기본 브랜치) ← `feat/<기능ID>-<설명>` (예: `feat/E-03-outbox-publisher`)
- 쪽지 형식을 바꾸는 PR은 `message-spec` 라벨을 달고 합치기 전에 이태우·김경우에게 꼭 알린다.

자세한 규칙은 [.github 레포](https://github.com/sesac-freshlink/.github)를 참고한다.
