# 📊 Stock Hunter - 프로젝트 요약

## 🎯 프로젝트 개요

**Stock Hunter**는 한국투자증권 OpenAPI를 활용한 고성능 주식 스크리닝 시스템입니다.

### 핵심 가치

✅ **고성능**: Kotlin 코루틴으로 수천 종목 병렬 처리  
✅ **확장 가능**: 마이크로서비스 아키텍처 (Kotlin + FastAPI)  
✅ **실전 투자**: 60일/112일/224일 이평선, 볼린저밴드 등 실무 지표  
✅ **사용 편의성**: 웹 UI + Docker로 5분 안에 시작  

---

## 📁 프로젝트 구조

```
StockHunter/
│
├── 📄 README.md                     # 메인 문서
├── 📄 QUICK_START.md                # 빠른 시작 가이드
├── 📄 ARCHITECTURE.md               # 아키텍처 상세 문서
├── 📄 PROJECT_SUMMARY.md            # 이 파일
│
├── 🐳 docker-compose.yml            # Docker 오케스트레이션
├── 🛠️ Makefile                      # 편의 명령어
├── 📝 .env.example                  # 환경 변수 예시
├── 🌐 stock_screener.html           # 웹 클라이언트
│
├── 🟦 kotlin-screener/              # Kotlin 스크리닝 엔진 (Port 8080)
│   ├── src/main/kotlin/com/jeromeent/stockhunter/
│   │   ├── 📦 model/
│   │   │   └── Models.kt            # 데이터 모델 (StockData, Condition 등)
│   │   ├── 🔌 client/
│   │   │   └── KISApiClient.kt      # 한국투자증권 API 클라이언트
│   │   ├── 🧠 service/
│   │   │   └── StockScreener.kt     # 스크리닝 비즈니스 로직
│   │   ├── 🔧 util/
│   │   │   ├── TechnicalIndicators.kt  # 기술적 지표 계산
│   │   │   └── Extensions.kt           # Kotlin 확장 함수
│   │   └── Application.kt           # 메인 진입점 (Ktor 서버)
│   ├── src/main/resources/
│   │   └── logback.xml              # 로깅 설정
│   ├── build.gradle.kts             # Gradle 빌드 파일
│   └── Dockerfile                   # Docker 이미지 정의
│
└── 🐍 fastapi-gateway/              # FastAPI 게이트웨이 (Port 3000)
    ├── main.py                      # FastAPI 애플리케이션
    ├── requirements.txt             # Python 의존성
    ├── .env.example                 # 환경 변수
    └── Dockerfile                   # Docker 이미지 정의
```

---

## 🔧 기술 스택

### Backend (Kotlin)

| 기술 | 버전 | 용도 |
|------|------|------|
| Kotlin | 1.9.22 | 메인 언어 |
| Ktor | 2.3.7 | 웹 프레임워크 |
| Kotlin Coroutines | 1.7.3 | 비동기/병렬 처리 |
| Guava | 32.1.3 | Rate Limiting |
| Logback | 1.4.14 | 로깅 |

### API Gateway (Python)

| 기술 | 버전 | 용도 |
|------|------|------|
| Python | 3.11 | 메인 언어 |
| FastAPI | 0.109 | 웹 프레임워크 |
| Uvicorn | 0.27 | ASGI 서버 |
| Pydantic | 2.5 | 데이터 검증 |
| HTTPX | 0.26 | HTTP 클라이언트 |

### Frontend

| 기술 | 버전 | 용도 |
|------|------|------|
| HTML5 | - | 마크업 |
| Vanilla JS | ES6+ | 로직 |
| TailwindCSS | 3.x | 스타일링 |

### Infrastructure

| 기술 | 버전 | 용도 |
|------|------|------|
| Docker | 24+ | 컨테이너화 |
| Docker Compose | 2.x | 오케스트레이션 |
| Gradle | 8.5 | 빌드 도구 |

---

## 🚀 주요 기능

### 1. 이동평균선 필터링

- **60일선**: 중기 추세 파악
- **112일선**: 장기 추세 (가장 많이 사용)
- **224일선**: 초장기 추세
- 현재가 대비 비율 설정 (예: 95~105%)

### 2. 볼린저 밴드 분석

- **3가지 프리셋**:
  - 단기 트레이딩 (10일, ±1.5σ)
  - 일반적 (20일, ±2σ) ⭐
  - 장기 투자 (30일, ±3σ)
- **위치 필터링**: 상단/중간/하단
- **돌파 감지**: 상단 돌파 (강세), 하단 터치 (반등 기회)

### 3. 거래량 분석

- 20일 평균 대비 배수 설정
- 거래량 급증 종목 포착

### 4. 기타 조건

- ETF/ETN 제외
- 이평선 정배열 (5>20>60>112)
- 가격 변동률 범위 설정

---

## 📊 성능 지표

### 처리 속도

| 종목 수 | 예상 시간 | 비고 |
|---------|----------|------|
| 30개 | ~3초 | 테스트 완료 |
| 100개 | ~10초 | |
| 500개 | ~50초 | |
| 2000개 | ~3.3분 | 전체 코스피 예상 |

*API Rate Limit: 초당 20건*

### 리소스 사용

| 컴포넌트 | CPU | 메모리 |
|----------|-----|--------|
| Kotlin Screener | ~30% | ~300MB |
| FastAPI Gateway | ~10% | ~100MB |
| 합계 | ~40% | ~400MB |

---

## 🎯 사용 시나리오

### 시나리오 1: 안정적 투자 종목 발굴

```
조건:
- 112일선 ±5% 이내 (95~105%)
- 이평선 정배열
- ETF/ETN 제외

→ 추세를 따르는 안정적 종목 포착
```

### 시나리오 2: 과매도 반등 기회

```
조건:
- 112일선 ±5% 이내
- 볼린저 밴드 하단 터치
- 거래량 1.5배 이상

→ 저점 반등 가능성 높은 종목
```

### 시나리오 3: 강한 상승 추세

```
조건:
- 60/112일선 모두 사용
- 볼린저 밴드 상단 돌파
- 이평선 정배열

→ 강력한 상승 모멘텀 종목
```

---

## 🔐 보안 & 제한사항

### 현재 보안 수준

✅ HTTPS 통신 (한국투자증권 API)  
✅ API 키 클라이언트 입력 (세션 저장 없음)  
✅ CORS 설정  
⚠️ API 키 서버 저장 미지원 (향후 개선)  

### API 제한사항

- **Rate Limit**: 초당 20건
- **일일 호출 제한**: 한국투자증권 정책 따름
- **토큰 유효기간**: 24시간

---

## 🛠️ 빠른 명령어

```bash
# 서비스 시작
make up

# 로그 확인
make logs

# 헬스 체크
make health

# 서비스 중지
make down

# 완전 삭제
make clean
```

---

## 📈 향후 개선 계획

### Phase 1 (완료 ✅)
- [x] Kotlin 스크리닝 엔진
- [x] FastAPI 게이트웨이
- [x] Docker 배포 환경
- [x] 웹 UI

### Phase 2 (계획)
- [ ] Redis 캐싱
- [ ] WebSocket 실시간 스트리밍
- [ ] PostgreSQL 결과 저장
- [ ] 종목 마스터 파일 자동 로드

### Phase 3 (장기)
- [ ] 백테스팅 기능
- [ ] 알림 기능 (Slack, Email)
- [ ] 모바일 앱 (Flutter)
- [ ] 머신러닝 예측 모델

---

## 📚 주요 파일 설명

### Kotlin 핵심 파일

| 파일 | 라인 수 | 주요 책임 |
|------|---------|----------|
| `Models.kt` | ~200 | 데이터 모델 정의 |
| `KISApiClient.kt` | ~300 | API 통신, 토큰 관리 |
| `StockScreener.kt` | ~250 | 스크리닝 로직, 병렬 처리 |
| `TechnicalIndicators.kt` | ~150 | 기술적 지표 계산 |
| `Application.kt` | ~150 | REST API 엔드포인트 |

### Python 핵심 파일

| 파일 | 라인 수 | 주요 책임 |
|------|---------|----------|
| `main.py` | ~250 | API 라우팅, 에러 처리 |

---

## 🎓 학습 포인트

이 프로젝트에서 배울 수 있는 것들:

1. **Kotlin Coroutines**: 수천 개 작업을 효율적으로 병렬 처리
2. **마이크로서비스**: Kotlin과 Python의 조합
3. **API 통합**: OAuth 2.0, REST API, Rate Limiting
4. **Docker**: 멀티 컨테이너 애플리케이션 배포
5. **금융 데이터 처리**: 이동평균, 볼린저밴드 등 실전 지표
6. **에러 처리**: 계층별 에러 핸들링 전략

---

## 🤝 기여 방법

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

---

## 📞 문의 & 지원

- 📧 Email: help@jeromeent.com
- 🐛 Issues: GitHub Issues
- 💬 Discussions: GitHub Discussions

---

## 📄 라이선스

MIT License - 자유롭게 사용, 수정, 배포 가능

---

## ⚠️ 면책 조항

본 소프트웨어는 교육 및 연구 목적으로 제공됩니다. 실제 투자 손실에 대한 책임은 사용자에게 있습니다.

---

**🎉 Happy Trading! 📈**

*Made with ❤️ by Jerome Entertainment*
