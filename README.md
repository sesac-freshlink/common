# common

> 여러 서비스가 **같이 쓰는 코드**를 모은 라이브러리. Outbox 발행기, 멱등 처리, 쪽지(메시지) DTO가 들어 있다. GitHub Packages에 버전을 붙여 올리고, 각 서비스가 가져다 쓴다.

| 항목 | 내용 |
| --- | --- |
| 담당 | 김진아 |
| 관련 기능 ID | E-03 Outbox 발행기, E-04 멱등 처리 |
| 기술 | Java 21, Spring Boot 3 (라이브러리), Spring Data JPA, Spring Cloud AWS (SQS) |
| 배포 | `v*` 태그 → GitHub Packages (Maven) `com.freshlink:common:<버전>` |
| 사용처 | inventory-service, order-service, logistics-simulator(DTO) |

---

## 1. 왜 별도 레포인가

inventory-service와 order-service가 같은 Outbox·멱등 코드를 쓴다. 복사해서 쓰면 한쪽만 고쳐지는 사고가 난다. 그래서 라이브러리로 한 번 만들고 버전으로 관리한다.

## 2. 들어 있는 것

### Outbox 발행기 (E-03)

**문제:** 주문을 DB에 저장한 직후 쪽지를 보내다 서버가 죽으면, 주문은 있는데 쪽지는 안 간다.
**해결:** 쪽지를 먼저 같은 트랜잭션으로 `outbox` 표에 적어 두고, 발행기가 **1초마다** 꺼내 SQS로 보낸 뒤 '발행 완료'로 바꾼다.

| 표 | 컬럼 |
| --- | --- |
| `outbox` | id, queue_name, group_id, payload, published(true/false), created_at |

### 멱등 처리 (E-04)

**문제:** SQS는 같은 쪽지를 두 번 줄 수 있다.
**해결:** 처리한 `messageId`를 기록해 두고, 이미 있으면 건너뛴다. 같은 쪽지를 두 번 보내도 재고·주문은 한 번만 바뀐다.

| 표 | 컬럼 |
| --- | --- |
| `processed_message` | message_id(PK), processed_at |

### 쪽지 DTO

모든 쪽지는 JSON이고 공통으로 `messageId`와 `type`을 가진다.

| 큐 | type | 보내는 곳 |
| --- | --- | --- |
| `inventory-commands.fifo` (그룹 = storeId) | INBOUND | logistics-simulator |
| | RESERVE, CONFIRM, RELEASE | order-service |
| | DISPOSE | inventory-service |
| `inventory-results` | RESERVED, RESERVE_FAILED | inventory-service |

## 3. 폴더 구조

```
common/
├── src/main/java/com/freshlink/common/
│   ├── outbox/        # Outbox 엔티티, 저장 헬퍼, 1초 주기 발행기
│   ├── idempotency/   # processed_message 기록·확인
│   └── message/       # 쪽지 DTO (InboundMessage, ReserveMessage ...)
└── src/test/java/com/freshlink/common/
```

## 4. 서비스에서 가져다 쓰기

```groovy
// build.gradle (inventory-service, order-service)
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/sesac-freshlink/common")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN") // read:packages 권한
        }
    }
}
dependencies {
    implementation "com.freshlink:common:0.1.0"
}
```

## 5. 버전 규칙

- SemVer 태그 `vMAJOR.MINOR.PATCH` (예: `v0.1.0` → `v0.2.0`). 태그를 달면 GitHub Packages에 배포한다.
- 0.x 동안은 쪽지 형식이 바뀔 수 있다. **버전을 올릴 때 이태우·김경우에게 알린다.**

## 6. 로컬에서 빌드하기

> 아직 뼈대만 있는 상태다. 첫 작업으로 Gradle `java-library` 프로젝트를 만들고 `maven-publish` 플러그인을 설정한다.

```bash
./gradlew build
./gradlew publishToMavenLocal   # 서비스에서 mavenLocal()로 바로 테스트
```

## 7. 브랜치와 기여

- `main`(배포) ← `dev`(통합, 기본 브랜치) ← `feat/<기능ID>-<설명>` (예: `feat/E-03-outbox-publisher`)
- 쪽지 형식을 바꾸는 PR은 `message-spec` 라벨을 달고 이태우·김경우 둘 다 리뷰한다.

자세한 규칙은 [.github 레포](https://github.com/sesac-freshlink/.github)를 참고한다.
