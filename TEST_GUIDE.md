# 🧪 Stock Hunter - 테스트 가이드

## 빠른 테스트 (로컬 개발 환경)

### 1️⃣ FastAPI 서버만 실행하여 테스트

```bash
cd fastapi-gateway

# 의존성 설치 (처음 한 번만)
pip install -r requirements.txt

# 서버 실행
python main.py
```

브라우저에서 `http://localhost:3000` 접속!

> **장점**: Kotlin 서버 없이도 웹 UI 확인 가능  
> **단점**: 실제 스크리닝 기능은 Kotlin 서버 필요

---

### 2️⃣ 전체 시스템 테스트 (Docker)

```bash
# 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 브라우저 접속
# http://localhost:3000
```

---

## 단계별 테스트 체크리스트

### ✅ Step 1: 서비스 헬스 체크

```bash
# FastAPI 헬스 체크
curl http://localhost:3000/health

# 기대 결과:
# {
#   "status": "healthy",
#   "services": {
#     "gateway": "healthy",
#     "kotlin_screener": "healthy"
#   }
# }
```

```bash
# Kotlin 서버 헬스 체크
curl http://localhost:8080/health

# 기대 결과:
# {
#   "status": "healthy",
#   "service": "stock-hunter",
#   "version": "1.0.0"
# }
```

---

### ✅ Step 2: API 키 검증 테스트

```bash
curl -X POST http://localhost:3000/api/v1/validate-credentials \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET"
  }'

# 성공 시:
# {"valid": true, "message": "인증 성공"}

# 실패 시:
# {"valid": false, "message": "인증 실패"}
```

---

### ✅ Step 3: 종목 코드 조회 테스트

```bash
curl http://localhost:3000/api/v1/stock-codes

# 기대 결과:
# {
#   "codes": ["005930", "000660", ...],
#   "count": 30
# }
```

---

### ✅ Step 4: 간단한 스크리닝 테스트

```bash
curl -X POST http://localhost:3000/api/v1/screen \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET",
    "ma112Enabled": true,
    "ma112Min": 95,
    "ma112Max": 105,
    "excludeETF": true,
    "targetCodes": []
  }' | jq '.'

# 성공 시 JSON 결과:
# {
#   "stocks": [...],
#   "totalScanned": 30,
#   "matchedCount": 5,
#   "executionTimeMs": 3000
# }
```

---

## 웹 UI 테스트 시나리오

### 시나리오 1: 기본 스크리닝

1. `http://localhost:3000` 접속
2. API 키 입력
3. 기본 설정 유지 (112일선 ±5%)
4. **"조건 검색 실행"** 클릭
5. 결과 테이블 확인

**예상 결과**: 3~10개 종목 매칭

---

### 시나리오 2: 볼린저 밴드 활성화

1. **"BB 조건 사용"** 체크
2. 일반적 (20일, ±2σ) 선택
3. 위치: "하단 밴드 근처 (과매도)" 선택
4. 검색 실행

**예상 결과**: 과매도 상태 종목 필터링

---

### 시나리오 3: 거래량 급증 종목

1. **"평균 대비"** 체크
2. 배수: 2.0 입력
3. 검색 실행

**예상 결과**: 거래량이 평균의 2배 이상인 종목만 표시

---

## 문제 해결

### 🚨 "Cannot connect to Kotlin service" 오류

```bash
# Kotlin 서버 상태 확인
docker-compose ps kotlin-screener

# 로그 확인
docker-compose logs kotlin-screener

# 재시작
docker-compose restart kotlin-screener
```

---

### 🚨 "API 인증 실패" 오류

**원인**:
- API 키가 잘못됨
- 모의투자 vs 실전투자 불일치
- 네트워크 이슈

**해결**:
1. [한국투자증권 API 포털](https://apiportal.koreainvestment.com/) 접속
2. 발급된 키 확인
3. 모의투자용 키인지 확인
4. 새 키 발급 시도

---

### 🚨 HTML 페이지가 안 뜨는 경우

```bash
# 파일 존재 확인
ls -la stock_screener.html

# FastAPI 로그 확인
docker-compose logs fastapi-gateway | grep "Serving HTML"

# 파일 권한 확인
chmod 644 stock_screener.html
```

---

### 🚨 CORS 오류

브라우저 콘솔에 CORS 오류가 뜨는 경우:

```
Access to fetch at 'http://localhost:3000/api/v1/screen' 
from origin 'null' has been blocked by CORS policy
```

**해결**: 항상 `http://localhost:3000`으로 접속하세요 (file:// 프로토콜 사용 금지)

---

## 성능 테스트

### 30개 종목 스크리닝 시간 측정

```bash
time curl -X POST http://localhost:3000/api/v1/screen \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_KEY",
    "appSecret": "YOUR_SECRET",
    "ma112Enabled": true,
    "ma112Min": 95,
    "ma112Max": 105
  }' | jq '.executionTimeMs'

# 예상: 2000-5000ms (2-5초)
```

---

## 로그 분석

### FastAPI 로그 확인

```bash
docker-compose logs -f fastapi-gateway

# 주요 로그 메시지:
# - "Received screening request"
# - "Screening completed: X matches"
# - "Credential validation failed"
```

### Kotlin 로그 확인

```bash
docker-compose logs -f kotlin-screener

# 주요 로그 메시지:
# - "Starting stock screening"
# - "Screening completed: X/Y stocks"
# - "Failed to process XXX: ..."
```

---

## 디버깅 모드

### FastAPI 디버그 모드

```bash
cd fastapi-gateway

# 환경 변수 설정
export LOG_LEVEL=DEBUG

# 실행
uvicorn main:app --host 0.0.0.0 --port 3000 --reload --log-level debug
```

### Kotlin 디버그 로그

`kotlin-screener/src/main/resources/logback.xml`:

```xml
<logger name="com.jeromeent.stockhunter" level="DEBUG"/>
```

---

## API 문서 확인

FastAPI는 자동으로 API 문서를 생성합니다:

- **Swagger UI**: `http://localhost:3000/docs`
- **ReDoc**: `http://localhost:3000/redoc`

여기서 각 엔드포인트를 직접 테스트할 수 있습니다!

---

## 통합 테스트 스크립트

```bash
#!/bin/bash
# test_all.sh

echo "🧪 Stock Hunter 통합 테스트"

echo "1. 서비스 시작..."
docker-compose up -d

echo "2. 30초 대기 (서비스 준비)..."
sleep 30

echo "3. FastAPI 헬스 체크..."
curl -s http://localhost:3000/health | jq '.status'

echo "4. Kotlin 헬스 체크..."
curl -s http://localhost:8080/health | jq '.status'

echo "5. 종목 코드 조회..."
curl -s http://localhost:3000/api/v1/stock-codes | jq '.count'

echo "✅ 모든 테스트 완료!"
```

실행:
```bash
chmod +x test_all.sh
./test_all.sh
```

---

## 단위 테스트 (향후 추가 예정)

### Kotlin 테스트

```bash
cd kotlin-screener
./gradlew test
```

### Python 테스트

```bash
cd fastapi-gateway
pytest tests/
```

---

**테스트 중 문제가 발생하면 GitHub Issues에 로그와 함께 올려주세요!** 🙏
