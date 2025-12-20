# 📈 Stock Hunter - 주식 스크리닝 시스템

**한국투자증권 OpenAPI 기반 고성능 주식 조건 검색 서비스**

Kotlin 코루틴과 FastAPI를 활용한 마이크로서비스 아키텍처로 구현된 주식 스크리너입니다.

---

## 🎯 주요 기능

### 📊 기술적 분석 지표
- **이동평균선 (MA)**: 60일, 112일, 224일 이평선 기준 필터링
- **볼린저 밴드 (BB)**: 상단/하단 밴드 돌파 및 위치 기반 스크리닝
- **거래량 분석**: 평균 대비 거래량 급증 감지
- **가격 변동**: 등락률 범위 설정

### 🚀 성능 최적화
- **병렬 처리**: Kotlin 코루틴으로 수천 종목 동시 분석
- **Rate Limiting**: 초당 20건 API 호출 제한 준수
- **토큰 캐싱**: Access Token 자동 갱신 및 재사용
- **마이크로서비스**: Kotlin + FastAPI 이중 구조

### 🛡️ 안정성
- Docker Compose 기반 배포
- Health Check 및 자동 재시작
- 상세한 로깅 및 에러 핸들링

---

## 🏗️ 아키텍처

```
┌─────────────────────┐
│   Client (HTML/JS)  │  ← 웹 인터페이스
└──────────┬──────────┘
           │ HTTP
┌──────────▼──────────┐
│  FastAPI Gateway    │  ← Python (Port 3000)
│  (API Gateway)      │     - 요청 라우팅
└──────────┬──────────┘     - 에러 처리
           │ HTTP
┌──────────▼──────────┐
│  Kotlin Screener    │  ← Kotlin/Ktor (Port 8080)
│  (Core Engine)      │     - 병렬 데이터 수집
└──────────┬──────────┘     - 기술적 지표 계산
           │                - 조건 필터링
┌──────────▼──────────┐
│ 한국투자증권 OpenAPI │
└─────────────────────┘
```

---

## 📦 프로젝트 구조

```
StockHunter/
├── kotlin-screener/              # Kotlin 스크리닝 엔진
│   ├── src/main/kotlin/
│   │   └── com/jeromeent/stockhunter/
│   │       ├── model/            # 데이터 모델
│   │       │   └── Models.kt
│   │       ├── client/           # API 클라이언트
│   │       │   └── KISApiClient.kt
│   │       ├── service/          # 비즈니스 로직
│   │       │   └── StockScreener.kt
│   │       ├── util/             # 유틸리티
│   │       │   ├── TechnicalIndicators.kt
│   │       │   └── Extensions.kt
│   │       └── Application.kt    # 메인 진입점
│   ├── build.gradle.kts
│   └── Dockerfile
│
├── fastapi-gateway/              # FastAPI 게이트웨이
│   ├── main.py
│   ├── requirements.txt
│   └── Dockerfile
│
├── stock_screener.html           # 웹 클라이언트
├── docker-compose.yml            # Docker 오케스트레이션
├── Makefile                      # 편의 명령어
└── README.md
```

---

## 🚀 빠른 시작

### 사전 요구사항

- **Docker & Docker Compose** (권장)
- 또는 **JDK 17+** + **Python 3.11+**
- **한국투자증권 OpenAPI 키** ([발급 방법](https://apiportal.koreainvestment.com/))

### 1️⃣ Docker로 실행 (권장)

```bash
# 프로젝트 클론
git clone <repository-url>
cd StockHunter

# 서비스 시작
make up

# 또는
docker-compose up -d
```

**서비스 URL:**
- 웹 UI: `http://localhost:3000` ← **여기로 접속하세요!**
- FastAPI Gateway: `http://localhost:3000/api`
- Kotlin Screener: `http://localhost:8080`

### 2️⃣ 로컬 개발 환경 실행

#### Kotlin 서버 실행

```bash
cd kotlin-screener

# Gradle 빌드 (첫 실행 시)
./gradlew build

# 서버 실행
./gradlew run

# 또는
make dev-kotlin
```

#### FastAPI 서버 실행

```bash
cd fastapi-gateway

# 가상환경 생성 (선택사항)
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 의존성 설치
pip install -r requirements.txt

# 서버 실행
python main.py

# 또는
make dev-fastapi
```

### 3️⃣ 웹 UI 접속

브라우저에서 `http://localhost:3000` 접속:
1. 한국투자증권 APP KEY 및 APP SECRET 입력
2. 스크리닝 조건 설정 (이평선, 볼린저밴드 등)
3. "조건 검색 실행" 버튼 클릭

> 💡 **참고**: FastAPI가 HTML을 서빙하므로 별도로 파일을 열 필요가 없습니다!

---

## 🔧 API 사용 예시

### 스크리닝 실행

```bash
curl -X POST http://localhost:3000/api/v1/screen \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET",
    "ma112Enabled": true,
    "ma112Min": 95,
    "ma112Max": 105,
    "bbEnabled": true,
    "bbPeriod": 20,
    "bbMultiplier": 2.0,
    "volumeEnabled": true,
    "volumeMultiple": 1.5,
    "excludeETF": true
  }'
```

### API 키 검증

```bash
curl -X POST http://localhost:3000/api/v1/validate-credentials \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET"
  }'
```

### 헬스 체크

```bash
curl http://localhost:3000/health
curl http://localhost:8080/health
```

---

## 📊 스크리닝 조건 설명

### 이동평균선 (MA)

| 파라미터 | 설명 | 기본값 |
|---------|------|--------|
| `ma60Enabled` | 60일 이평선 사용 여부 | false |
| `ma60Min`, `ma60Max` | 현재가/60일선 비율 (%) | 95~105 |
| `ma112Enabled` | 112일 이평선 사용 여부 | true |
| `ma112Min`, `ma112Max` | 현재가/112일선 비율 (%) | 95~105 |
| `ma224Enabled` | 224일 이평선 사용 여부 | false |

**예시:** `ma112Min=95, ma112Max=105` → 112일선 대비 95%~105% 범위 종목 필터

### 볼린저 밴드 (BB)

| 파라미터 | 설명 | 옵션 |
|---------|------|------|
| `bbEnabled` | BB 조건 사용 여부 | true/false |
| `bbPeriod` | 이동평균 기간 | 10/20/30 |
| `bbMultiplier` | 표준편차 승수 | 1.5/2.0/3.0 |
| `bbPosition` | 현재 위치 | all/upper/middle/lower |
| `bbUpperBreak` | 상단 밴드 돌파 | true/false |
| `bbLowerBreak` | 하단 밴드 터치 | true/false |

**프리셋:**
- 단기 트레이딩: `bbPeriod=10, bbMultiplier=1.5`
- 일반적: `bbPeriod=20, bbMultiplier=2.0` ⭐
- 장기 투자: `bbPeriod=30, bbMultiplier=3.0`

### 거래량

| 파라미터 | 설명 |
|---------|------|
| `volumeEnabled` | 거래량 조건 사용 |
| `volumeMultiple` | 20일 평균 대비 배수 (예: 1.5배) |

---

## 🛠️ 유용한 명령어 (Makefile)

```bash
make help           # 사용 가능한 명령어 확인
make build          # Docker 이미지 빌드
make up             # 서비스 시작
make down           # 서비스 중지
make restart        # 서비스 재시작
make logs           # 전체 로그 확인
make logs-kotlin    # Kotlin 로그
make logs-fastapi   # FastAPI 로그
make clean          # 컨테이너/이미지 삭제
make health         # 헬스 체크
```

---

## 🔐 환경 변수

### Kotlin Screener

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `JAVA_OPTS` | JVM 옵션 | `-Xmx512m -Xms256m` |

### FastAPI Gateway

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `KOTLIN_SERVICE_URL` | Kotlin 서버 URL | `http://localhost:8080` |
| `GATEWAY_PORT` | 게이트웨이 포트 | `3000` |
| `LOG_LEVEL` | 로그 레벨 | `INFO` |

---

## 📈 성능

### 벤치마크 (예상)

| 종목 수 | 소요 시간 | 처리량 |
|---------|----------|--------|
| 30개 | ~3초 | 10 TPS |
| 100개 | ~10초 | 10 TPS |
| 500개 | ~50초 | 10 TPS |
| 2000개 | ~200초 (3.3분) | 10 TPS |

*한국투자증권 API Rate Limit (초당 20건) 기준*

### 최적화 포인트

- ✅ **병렬 처리**: 100개 종목씩 청크로 분할하여 코루틴 처리
- ✅ **Rate Limiting**: Guava RateLimiter로 API 제한 준수
- ✅ **토큰 캐싱**: 24시간 유효한 Access Token 재사용
- ⏳ **추가 개선**: Redis 캐싱, 종목 데이터 사전 수집

---

## 🐛 트러블슈팅

### 1. "Connection refused" 오류

```bash
# 서비스 상태 확인
docker-compose ps

# 로그 확인
make logs

# 서비스 재시작
make restart
```

### 2. "Invalid credentials" 오류

- 한국투자증권 API 키가 올바른지 확인
- 모의투자 계좌용 키를 사용했는지 확인
- [API 포털](https://apiportal.koreainvestment.com/)에서 키 재발급

### 3. "Rate limit exceeded" 오류

- API 호출이 초당 20건을 초과하지 않도록 자동 제어됨
- 대량 종목 스크리닝 시 시간이 오래 걸릴 수 있음

### 4. 빌드 오류

```bash
# Gradle 캐시 삭제
cd kotlin-screener
./gradlew clean build --refresh-dependencies

# Docker 이미지 재빌드
docker-compose build --no-cache
```

---

## 📚 추가 자료

- [한국투자증권 OpenAPI 문서](https://apiportal.koreainvestment.com/apiservice-apiservice)
- [Ktor 공식 문서](https://ktor.io/)
- [FastAPI 공식 문서](https://fastapi.tiangolo.com/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

## 🤝 기여

버그 리포트, 기능 요청, Pull Request 환영합니다!

---

## 📄 라이선스

MIT License

---

## 👨‍💻 개발자

**Jerome Entertainment**
- 프로젝트: Stock Hunter
- 버전: 1.0.0

---

## ⚠️ 면책 조항

본 소프트웨어는 **교육 및 연구 목적**으로 제공됩니다. 실제 투자 결정에 사용 시 발생하는 손실에 대해 개발자는 책임지지 않습니다. 투자 결정은 본인의 판단과 책임 하에 이루어져야 합니다.
