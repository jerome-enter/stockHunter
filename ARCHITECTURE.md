# 🏛️ Stock Hunter - 아키텍처 문서

## 시스템 개요

Stock Hunter는 **마이크로서비스 아키텍처** 기반의 주식 스크리닝 시스템으로, 다음과 같은 계층으로 구성됩니다:

```
┌────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│                  (HTML/JavaScript Client)                   │
└─────────────────────────┬──────────────────────────────────┘
                          │ HTTP REST API
┌─────────────────────────▼──────────────────────────────────┐
│                     API Gateway Layer                       │
│                    (FastAPI - Python)                       │
│  - Request Routing                                          │
│  - Error Handling                                           │
│  - Response Transformation                                  │
└─────────────────────────┬──────────────────────────────────┘
                          │ HTTP REST API
┌─────────────────────────▼──────────────────────────────────┐
│                   Business Logic Layer                      │
│                   (Ktor Server - Kotlin)                    │
│  - Stock Screening Engine                                   │
│  - Technical Indicators Calculation                         │
│  - Parallel Data Processing (Coroutines)                    │
└─────────────────────────┬──────────────────────────────────┘
                          │ HTTPS REST API
┌─────────────────────────▼──────────────────────────────────┐
│                    External API Layer                       │
│              (한국투자증권 OpenAPI)                         │
│  - OAuth 2.0 Authentication                                 │
│  - Daily Price Data                                         │
│  - Stock Master Data                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 계층별 상세 설명

### 1️⃣ Presentation Layer (Client)

**기술 스택:**
- HTML5 + Vanilla JavaScript
- TailwindCSS (UI 프레임워크)

**책임:**
- 사용자 입력 수집 (API 키, 스크리닝 조건)
- API 호출 및 응답 렌더링
- 에러 메시지 표시

**주요 파일:**
- `stock_screener.html`

---

### 2️⃣ API Gateway Layer (FastAPI)

**기술 스택:**
- Python 3.11
- FastAPI 0.109
- Uvicorn (ASGI Server)
- Pydantic (데이터 검증)

**책임:**
- 클라이언트 요청 라우팅
- 요청/응답 검증
- CORS 처리
- 에러 핸들링 및 변환
- 헬스 체크

**포트:** 3000

**주요 엔드포인트:**
```
POST /api/v1/screen                    # 스크리닝 실행
POST /api/v1/validate-credentials      # API 키 검증
GET  /api/v1/stock-codes               # 종목 코드 목록
GET  /health                           # 헬스 체크
```

**파일 구조:**
```
fastapi-gateway/
├── main.py              # FastAPI 애플리케이션
├── requirements.txt     # Python 의존성
├── Dockerfile          # Docker 이미지 정의
└── .env.example        # 환경 변수 예시
```

---

### 3️⃣ Business Logic Layer (Kotlin)

**기술 스택:**
- Kotlin 1.9.22
- Ktor 2.3.7 (웹 프레임워크)
- Kotlin Coroutines (병렬 처리)
- Ktor Client (HTTP 클라이언트)
- Guava RateLimiter (API 제한)

**책임:**
- 한국투자증권 API 통신
- OAuth 토큰 관리
- 주식 데이터 수집 (병렬)
- 기술적 지표 계산
- 조건 기반 필터링

**포트:** 8080

**주요 컴포넌트:**

#### 📦 Model Layer (`model/Models.kt`)
```kotlin
- StockData           // 종목 데이터
- DailyPrice          // 일별 시세
- ScreeningCondition  // 스크리닝 조건
- ScreeningResult     // 스크리닝 결과
- BollingerBands      // 볼린저 밴드
```

#### 🔌 Client Layer (`client/KISApiClient.kt`)
```kotlin
class KISApiClient {
  - getAccessToken()        // OAuth 토큰 발급
  - getDailyPrice()         // 일별 시세 조회
  - getDailyPriceBatch()    // 배치 조회
  - getAllStockCodes()      // 종목 코드 목록
}
```

#### 🧠 Service Layer (`service/StockScreener.kt`)
```kotlin
class StockScreener {
  - screen()                // 메인 스크리닝 함수
  - fetchAndFilter()        // 개별 종목 처리
  - screenStreaming()       // 스트리밍 스크리닝
}
```

#### 🔧 Util Layer
- `TechnicalIndicators.kt`: SMA, 볼린저밴드, RSI, MACD 계산
- `Extensions.kt`: Kotlin 확장 함수

**파일 구조:**
```
kotlin-screener/
├── src/main/kotlin/com/jeromeent/stockhunter/
│   ├── model/
│   │   └── Models.kt
│   ├── client/
│   │   └── KISApiClient.kt
│   ├── service/
│   │   └── StockScreener.kt
│   ├── util/
│   │   ├── TechnicalIndicators.kt
│   │   └── Extensions.kt
│   └── Application.kt
├── src/main/resources/
│   └── logback.xml
├── build.gradle.kts
└── Dockerfile
```

---

## 데이터 흐름

### 스크리닝 요청 플로우

```
1. Client
   │
   ├─→ POST /api/v1/screen { appKey, appSecret, conditions }
   │
2. FastAPI Gateway
   │
   ├─→ 요청 검증 (Pydantic)
   ├─→ POST http://kotlin-screener:8080/api/v1/screen
   │
3. Kotlin Screener
   │
   ├─→ OAuth 토큰 발급/캐시 확인
   ├─→ 종목 코드 목록 로드
   ├─→ 병렬 처리 시작 (Coroutines)
   │   │
   │   ├─→ [종목 1~100] → fetchAndFilter()
   │   ├─→ [종목 101~200] → fetchAndFilter()
   │   └─→ ...
   │
   └─→ 각 종목별:
       ├─→ API 호출 (Rate Limited)
       ├─→ 기술적 지표 계산 (MA, BB)
       ├─→ 조건 필터링
       └─→ 결과 수집
   │
4. 결과 반환
   │
   ├─→ ScreeningResult { stocks, count, time }
   │
5. FastAPI → Client
   │
   └─→ JSON 응답
```

---

## 병렬 처리 전략

### Kotlin Coroutines 활용

```kotlin
suspend fun screen(condition: ScreeningCondition): ScreeningResult = coroutineScope {
    val stockCodes = getAllStockCodes() // 2000+ 종목
    
    // 100개씩 청크로 분할
    val results = stockCodes
        .chunked(100)
        .map { chunk ->
            async(Dispatchers.IO) {  // 각 청크를 병렬 처리
                chunk.mapNotNull { code ->
                    fetchAndFilter(code, condition)
                }
            }
        }
        .awaitAll()  // 모든 코루틴 완료 대기
        .flatten()
    
    ScreeningResult(...)
}
```

**장점:**
- 수천 개 종목을 동시 처리
- API Rate Limit 준수하면서 최대 성능
- Non-blocking I/O

---

## Rate Limiting 전략

### Guava RateLimiter 사용

```kotlin
class KISApiClient {
    private val rateLimiter = RateLimiter.create(20.0) // 초당 20건
    
    suspend fun getDailyPrice(code: String): Response {
        rateLimiter.acquire()  // 토큰 획득 (blocking)
        return httpClient.get(...)
    }
}
```

**효과:**
- 한국투자증권 API 제한(초당 20건) 준수
- 429 Too Many Requests 에러 방지
- 안정적인 서비스 운영

---

## 토큰 관리 전략

### Access Token 캐싱

```kotlin
class KISApiClient {
    private var cachedToken: String? = null
    private var tokenExpireTime: Instant? = null
    
    suspend fun ensureAccessToken() {
        if (cachedToken == null || isTokenExpired()) {
            cachedToken = getAccessToken()  // 재발급
            tokenExpireTime = Instant.now().plusSeconds(86400)
        }
    }
}
```

**장점:**
- 불필요한 토큰 재발급 방지
- API 호출 횟수 절약
- 응답 시간 단축

---

## 에러 처리 전략

### 계층별 에러 핸들링

```
1. Kotlin Layer
   ├─→ try-catch로 개별 종목 실패 처리
   ├─→ 실패한 종목은 로그 기록 후 스킵
   └─→ 전체 스크리닝은 계속 진행

2. FastAPI Layer
   ├─→ HTTPException으로 변환
   ├─→ 상태 코드별 분기 (400, 401, 500 등)
   └─→ JSON 에러 응답 반환

3. Client Layer
   ├─→ 에러 메시지 파싱
   └─→ 사용자 친화적 메시지 표시
```

---

## 확장 가능성

### 향후 개선 방안

1. **Redis 캐싱**
   ```
   종목 데이터 → Redis → 24시간 TTL
   중복 API 호출 제거
   ```

2. **WebSocket 스트리밍**
   ```
   Client ←─ WebSocket ─→ FastAPI ←─ Kotlin
   실시간 진행률 표시
   ```

3. **데이터베이스 도입**
   ```
   PostgreSQL: 스크리닝 결과 저장
   TimescaleDB: 시계열 데이터 저장
   ```

4. **종목 마스터 파일**
   ```
   전체 코스피/코스닥 종목 자동 로드
   매일 새벽 자동 업데이트
   ```

5. **백테스팅 기능**
   ```
   과거 데이터로 전략 검증
   수익률 시뮬레이션
   ```

---

## 보안 고려사항

### 현재 구현

- ✅ API 키를 클라이언트에서 입력 (세션 저장 없음)
- ✅ HTTPS 통신 (한국투자증권 API)
- ✅ CORS 설정

### 프로덕션 권장사항

- 🔒 API 키를 서버 측 환경 변수로 관리
- 🔒 JWT 인증 도입
- 🔒 Rate Limiting (클라이언트별)
- 🔒 HTTPS 강제 (Let's Encrypt)

---

## 모니터링 & 로깅

### 로그 수준

```
DEBUG: 개발 시 상세 정보
INFO:  주요 이벤트 (스크리닝 시작/완료)
WARN:  경고 (개별 종목 실패)
ERROR: 심각한 오류 (서비스 장애)
```

### 로그 파일

```
kotlin-screener/logs/stock-hunter.log    # 30일 보관
fastapi-gateway → stdout (Docker logs)
```

### Health Check

```bash
GET /health
{
  "status": "healthy",
  "services": {
    "gateway": "healthy",
    "kotlin_screener": "healthy"
  }
}
```

---

## 성능 메트릭

| 항목 | 목표 | 현재 |
|------|------|------|
| 스크리닝 속도 (30종목) | < 5초 | ~3초 |
| API 응답 시간 | < 100ms | ~50ms |
| 메모리 사용 (Kotlin) | < 512MB | ~300MB |
| 메모리 사용 (FastAPI) | < 256MB | ~100MB |
| 동시 사용자 | 10명 | 지원 |

---

## 배포 아키텍처

### Docker Compose

```yaml
services:
  kotlin-screener:
    - Port: 8080
    - Memory: 512MB
    - Health Check: /health
    
  fastapi-gateway:
    - Port: 3000
    - Depends on: kotlin-screener
    - Health Check: /health
```

### 프로덕션 배포

```
AWS ECS / GCP Cloud Run / Azure Container Instances
│
├─ Load Balancer (ALB/NLB)
├─ Container: FastAPI (2 replicas)
└─ Container: Kotlin (2 replicas)
```

---

이 문서는 Stock Hunter 시스템의 전체 아키텍처를 설명합니다. 추가 질문이나 개선 제안은 언제든 환영합니다! 🚀
