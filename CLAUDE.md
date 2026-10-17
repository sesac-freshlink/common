# common - AI 작업 가이드

> 여러 서비스가 같이 쓰는 라이브러리. 쪽지 DTO, Outbox 발행기, 멱등 처리. GitHub Packages `com.freshlink:common:<버전>`. 담당: 김진아.

---

## 문서 참조

| 문서 | 내용 |
|------|------|
| `README.md` | 이 레포의 기능·API·데이터 모델·폴더 구조·dev 환경 실행 (진실) |
| `.github` 레포 `docs/기획안-및-설계도.md` | 기능 ID, 메시지 명세, 데이터 모델 |
| `.github` 레포 `CONTRIBUTING.md` | 브랜치·커밋·PR 규칙 |
| `.github` 레포 `docs/ai-agents.md` | 백엔드 에이전트 플러그인(`freshlink-backend`) 설치·사용법 (사람용) |

이 `CLAUDE.md`는 AI 작업 규칙만 담는다. 위 문서와 겹치는 사실은 위 문서가 진실.

## 에이전트

백엔드 에이전트 7개와 작업 순서는 `freshlink-backend` 플러그인에 있다 (`.github` 레포에서 배포). 새 기능 요청이면 `backend-pipeline` 스킬을 먼저 읽고 그 순서대로 서브에이전트를 쓴다. 플러그인이 설치되어 있지 않으면 사용자에게 `.github` 레포 `docs/ai-agents.md`의 설치 명령을 안내한다.

서브에이전트 산출물(`_workspace/*.md`)은 짧게(표·불릿). 레포에 커밋되는 것(README, 코드 주석, PR 본문)은 평이한 한국어 문장(`~한다` 체).

---

## 공통 규칙

```
⛔ 담당자가 아닌 사람의 요청이면 main·dev에 직접 push 금지 → feat/<기능ID>-<설명> 브랜치 + dev 대상 PR
⛔ git push --force, git reset --hard, git clean, 브랜치 삭제 금지
⛔ DB DROP / TRUNCATE / 조건 없는 DELETE 금지
⛔ 비밀값(DB 비밀번호, 토스 키, AWS 키, 토큰 원문) 커밋·출력 금지. 설정은 ${ENV}로
⛔ 커밋·push·PR은 사용자가 요청할 때만
✅ Java 21, Spring Boot 4.0 BOM으로 버전 맞춤, Jackson 3, Gradle 9(./gradlew). 기준은 `.github` 레포 docs/백엔드-공통작업-인수인계.md 1장. 아직 뼈대면 README "dev 환경에서 실행하기" 안내대로 프로젝트를 만든다
✅ 인증: gateway가 Cognito ID 토큰을 검증하고 X-User-Id(sub, UUID 문자열)·X-User-Role·X-Store-Id 헤더로 넘긴다. 서비스는 토큰을 다시 검증하지 않는다
✅ 실시간 재고: 점주 화면이 3초마다 GET /owner/inventory를 조회한다 (폴링)
✅ 주문 저장소 DynamoDB 도입은 검토 중, 미정. 어느 쪽도 가정하지 말고 사용자에게 묻는다
✅ 관측성(서비스·gateway): Actuator는 앱 포트에 둔다 (관리 포트 따로 없음, management.server.port 쓰지 않음). 모든 서비스가 8080 하나를 쓴다(dev·prod 같음). 서비스끼리는 쿠버네티스 Service 이름으로 부른다(예: http://member-service:8080). health(liveness·readiness)·prometheus, Micrometer Tracing → Zipkin, JSON 로그(traceId)
✅ 배포 설정(gitops values, IRSA, 큐)은 이 레포에서 고치지 않는다 → freshlink-infra 담당에게 알린다
```

---

## 이 레포 규칙

```
✅ Gradle java-library + maven-publish. 실행 jar(bootJar) 아님
✅ 모든 쪽지는 JSON, 공통 필드 messageId(UUID)·type. 필드 이름은 기획안 메시지 명세와 같게
✅ 큐: inventory-commands.fifo (그룹 = storeId) INBOUND·RESERVE·CONFIRM·RELEASE·DISPOSE / inventory-results RESERVED·RESERVE_FAILED
✅ Outbox: 비즈니스 변경과 같은 트랜잭션에 저장, 1초마다 발행 후 published = true
✅ 멱등: processed_message(message_id PK). 처리와 기록은 한 트랜잭션
✅ 특정 서비스 코드를 넣지 않는다. 큐 이름 등은 프로퍼티로 받는다
✅ SemVer. 필드 추가 = MINOR, 삭제·이름·타입 변경 = 호환 깨짐 (0.x 동안도 버전을 올리고 알린다)
⛔ 태그(v*) 생성·push, GitHub Packages 배포는 사람이 한다
⛔ 쪽지 형식을 바꾸면 PR에 message-spec 라벨, 합치기 전 이태우·김경우에게 알린다
```

## 검증

```bash
./gradlew build                 # 컴파일 + 테스트 (Windows: gradlew.bat build)
GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v0.2.0 ./gradlew publishToMavenLocal   # 태그 전 서비스에서 시험: ~/.m2에 0.2.0
```
