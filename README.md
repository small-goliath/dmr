# DMR (Don't Merge without Review)

AI 기반 자동 코드 리뷰 시스템으로, GitLab Merge Request에 대해 지능적인 라인별 및 의존된 코드의 리뷰 댓글을 자동으로 생성합니다.

## 주요 기능

### AI 기반 코드 리뷰
- **라인별 리뷰**: 변경된 코드 라인에 대한 상세한 리뷰 댓글 자동 생성
- **의존성 분석**: 파일 간 의존성을 분석하여 연관된 파일을 함께 리뷰
- **크로스 파일 영향 분석**: 다른 파일에 미치는 영향도 분석
- **부작용 감지**: 데이터베이스, API, 파일 I/O 등 부작용 패턴 탐지

### 청킹
- **대용량 MR 처리**: 많은 파일이 변경된 경우 청크 단위로 분할하여 리뷰
- **유연한 전략**: 파일 단위 또는 라인 수 단위 청킹 전략 선택 가능

### 알림 시스템
- **Google Chat 통합**: 리뷰 완료 및 오류 발생 시 Google Chat으로 알림 전송
- **상세한 통계**: 변경 파일 수, 추가/삭제 라인 수, 리뷰 댓글 수 등 제공

### 유연한 설정
- **파일 필터링**: 확장자 및 경로 기반 파일 제외
- **크기 제한**: 파일 크기 및 개수 제한 설정
- **AI 파라미터**: Temperature, Max Tokens, Top-P 등 AI 파라미터 조정 가능

## 기술 스택

### Backend
- **Kotlin** 2.1.0
- **Spring Boot** 3.5.7
- **Spring AI** 1.0.0-M5 (OpenAI 호환)
- **Kotlin Coroutines** 1.9.0
- **Spring WebFlux**

### AI
- OpenAI 호환 API (vLLM, Ollama 등)
- DeepSeek-V3, GPT 시리즈 등 다양한 모델 지원

### Testing
- MockK
- Kotlin Coroutines Test

### Build
- Gradle Kotlin DSL
- Java 21

## 아키텍처

```
┌───────────────────────────────────────────────┐
│               GitLab Webhook                  │
└─────────────────┬─────────────────────────────┘
                  │
                  ▼
┌───────────────────────────────────────────────┐
│               GitLabWebhookController         │
└─────────────────┬─────────────────────────────┘
                  │
                  ▼
┌───────────────────────────────────────────────┐
│                 CodeReviewService             │
│  • MR 이벤트 처리                                │
│  • 리뷰 워크플로우 조정                            │
└─────────────────┬─────────────────────────────┘
                  │
         ┌────────┴────────┐
         ▼                 ▼
┌──────────────────┐  ┌──────────────────┐
│ContextBuilder    │  │LineByLineReview  │
│Service           │  │Service           │
│ • 컨텍스트 빌드     │   │ • 의존성 분석      │
│ • 파일 필터링       │  │ • AI 리뷰 생성     │
└──────────────────┘  │ • 댓글 포스팅       │
                      └────────┬─────────┘
                               │
                      ┌────────┴────────┐
                      ▼                 ▼
              ┌──────────────┐  ┌──────────────┐
              │ Dependency   │  │ CrossFile    │
              │ Analyzer     │  │ Impact       │
              │              │  │ Analyzer     │
              └──────────────┘  └──────────────┘
                      │
                      ▼
              ┌──────────────┐
              │ Spring AI    │
              │ ChatClient   │
              └──────────────┘
```

### 주요 컴포넌트

#### Controllers
- **GitLabWebhookController**: GitLab 웹훅 이벤트 수신 및 검증

#### Services
- **CodeReviewService**: 코드 리뷰 워크플로우 조정 및 전체 프로세스 관리
- **ContextBuilderService**: MR 컨텍스트 구성 및 파일 필터링
- **LineByLineReviewService**: 라인별 리뷰 수행 및 댓글 생성
- **ChunkedReviewService**: 대용량 MR에 대한 청크 단위 리뷰
- **GoogleChatNotifier**: Google Chat 알림 전송

#### Clients
- **GitLabApiClient**: GitLab API 통신 (MR 정보, 변경사항, 댓글 작성)
- **GoogleChatClient**: Google Chat Webhook 통신

#### Analyzers
- **DependencyAnalyzer**: 파일 간 의존성 분석
- **CrossFileImpactAnalyzer**: 크로스 파일 영향도 분석

#### Parsers
- **JsonResponseParser**: AI 응답 JSON 파싱
- **DiffParser**: Git diff 파싱

## 설치 및 실행

### 사전 요구사항
- Java 21 이상
- Gradle 8.x
- GitLab 인스턴스 (액세스 권한 필요)
- OpenAI 호환 API 서버 (vLLM, Ollama 등)

### 1. 프로젝트 클론

```bash
git clone https://github.com/small-goliath/dmr.git
cd DMR
```

### 2. 환경 변수 설정

```bash
cp .env.example .env
```

`.env` 파일 편집:

```properties
# Server
SERVER_PORT=8080

# vLLM / OpenAI-compatible API
VLLM_BASE_URL=http://localhost:8000/v1
VLLM_API_KEY=EMPTY
VLLM_MODEL=openai/gpt-oss-20b

# GitLab
GITLAB_URL=https://gitlab.example.com
GITLAB_TOKEN=glpat-YOUR_GITLAB_TOKEN_HERE
GITLAB_WEBHOOK_SECRET=your-webhook-secret-token-here

# Google Chat
GOOGLE_CHAT_WEBHOOK_URL=https://chat.googleapis.com/v1/spaces/...
GOOGLE_CHAT_ENABLED=true
```

### 3. 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 또는 JAR 파일 실행
java -jar build/libs/dmr-0.0.1-SNAPSHOT.jar
```

## 설정 가이드

### application.yml 주요 설정

#### 코드 리뷰 설정

```yaml
code-review:
  max-files: 100                    # 최대 리뷰 파일 수
  max-file-size: 1048576            # 최대 파일 크기 (1MB)
  line-by-line-enabled: true        # 라인별 리뷰 활성화

  chunking:
    enabled: true                   # 청킹 활성화
    files-per-chunk: 5              # 청크당 파일 수
    strategy: file                  # file 또는 line

  ai:
    temperature: 0.3                # AI 창의성 (0.0-1.0)
    max-tokens: 12000               # 최대 토큰 수
    top-p: 0.95                     # Nucleus sampling

  excluded-extensions:              # 제외할 확장자
    - .md
    - .json
    - .txt

  excluded-paths:                   # 제외할 경로
    - build/
    - gradle/
    - .git/
```

#### AI 모델 설정

```yaml
spring:
  ai:
    openai:
      base-url: ${VLLM_BASE_URL}
      api-key: ${VLLM_API_KEY}
      chat:
        options:
          model: ${VLLM_MODEL}
          temperature: 0.3
          max-tokens: 4000
```

## GitLab 웹훅 설정

### 1. Personal Access Token 생성

GitLab → Settings → Access Tokens:
- **Name**: DMR Code Review
- **Scopes**: `api`, `read_api`, `write_repository`
- 생성된 토큰을 `.env` 파일의 `GITLAB_TOKEN`에 설정

### 2. 웹훅 추가

GitLab Project → Settings → Webhooks:

**URL**: `http://your-server:8080/api/gitlab/webhook`

**Secret Token**: `.env` 파일의 `GITLAB_WEBHOOK_SECRET` 값 사용

**Trigger**:
- ✓ Merge request events

**SSL verification**: 환경에 따라 설정

## 사용 방법

### 자동 리뷰 트리거

다음 이벤트 발생 시 자동으로 리뷰가 시작됩니다:

1. **MR 생성** (`open`)
2. **MR 재개** (`reopen`)
3. **MR 업데이트** (`update`) - 변경사항이 있을 때만

### 리뷰 프로세스

1. **웹훅 수신**: GitLab에서 MR 이벤트 수신
2. **컨텍스트 구성**: 변경된 파일 및 diff 정보 수집
3. **의존성 분석**: 파일 간 의존성 및 영향도 분석
4. **AI 리뷰**: 컨텍스트를 기반으로 AI가 리뷰 수행
5. **댓글 생성**: GitLab MR에 라인별 리뷰 댓글 작성
6. **알림 전송**: Google Chat으로 리뷰 완료 알림

### 제외 조건

다음 경우에는 리뷰가 수행되지 않습니다:

- Draft MR (제목에 `Draft:` 또는 `WIP:`)
- Work In Progress MR
- 변경된 파일이 없는 경우
- 모든 파일이 필터링된 경우

## 개발 가이드

### 프로젝트 구조

```
src/
├── main/
│   ├── kotlin/at/tori/dmr/
│   │   ├── analyzer/           # 코드 분석기
│   │   ├── client/             # 외부 API 클라이언트
│   │   ├── config/             # 설정 클래스
│   │   ├── controller/         # REST 컨트롤러
│   │   ├── domain/             # 도메인 모델
│   │   ├── exception/          # 예외 처리
│   │   ├── parser/             # 파서
│   │   ├── prompt/             # AI 프롬프트 관리
│   │   └── service/            # 비즈니스 로직
│   └── resources/
│       ├── application.yml     # 애플리케이션 설정
│       └── prompts/            # AI 프롬프트 템플릿
└── test/
    └── kotlin/at/tori/dmr/     # 테스트 코드
```

### 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 패키지 테스트
./gradlew test --tests "at.tori.dmr.service.*"

# 테스트 리포트 확인
open build/reports/tests/test/index.html
```

## 테스트 커버리지

현재 테스트 현황:

| 모듈 | 테스트 수 | 상태 |
|------|----------|------|
| Analyzer | 40 | ✅ PASS |
| Client | 27 | ✅ PASS |
| Controller | 13 | ✅ PASS |
| Parser | 47 | ✅ PASS |
| Service | 61 | ✅ PASS |
| **Total** | **188** | **✅ PASS** |

## 트러블슈팅

### AI 응답이 느린 경우
- `application.yml`의 `read-timeout` 값 증가
- AI 모델의 `max-tokens` 값 감소
- 청킹 기능 활성화 및 `files-per-chunk` 감소

### GitLab API 오류
- Personal Access Token 권한 확인
- GitLab API rate limit 확인
- 네트워크 연결 상태 확인

### Google Chat 알림 실패
- Webhook URL이 올바른지 확인
- `GOOGLE_CHAT_ENABLED=true` 설정 확인
- 네트워크 방화벽 설정 확인