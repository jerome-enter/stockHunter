# 🚀 Stock Hunter - 빠른 시작 가이드

## 5분 안에 시작하기

### Step 1: API 키 발급 (5분)

1. [한국투자증권 홈페이지](https://www.koreainvestment.com/) 접속
2. 상단 메뉴 → **트레이딩** → **KIS Developers**
3. 회원가입/로그인
4. **앱 등록** 클릭
5. 앱 이름 입력 후 **모의투자** 선택
6. **APP KEY**와 **APP SECRET** 발급 (복사해두기)

---

### Step 2: 서비스 실행 (1분)

#### 방법 A: Docker 사용 (권장)

```bash
# 프로젝트 디렉토리로 이동
cd StockHunter

# 서비스 시작
docker-compose up -d

# 상태 확인
docker-compose ps
```

#### 방법 B: 로컬 실행

**터미널 1 - Kotlin 서버**
```bash
cd kotlin-screener
./gradlew run
```

**터미널 2 - FastAPI 서버**
```bash
cd fastapi-gateway
pip install -r requirements.txt
python main.py
```

---

### Step 3: 웹 UI 접속 (1분)

1. 브라우저에서 **`http://localhost:3000`** 접속
2. 발급받은 **APP KEY**와 **APP SECRET** 입력
3. 원하는 조건 설정:
   - ✅ **112일 이평선**: 95~105% (기본값)
   - ✅ **ETF/ETN 제외**: 체크
   - ⚙️ 볼린저밴드, 거래량 등 추가 설정 가능
4. **"조건 검색 실행"** 버튼 클릭

> 💡 **참고**: FastAPI가 웹 UI를 서빙하므로 HTML 파일을 직접 열 필요가 없습니다!

---

## 💡 추천 스크리닝 조건

### 초보자용 - 안정적 종목

```
✅ 112일 이평선: 95~105%
✅ ETF/ETN 제외
✅ 이평선 정배열 (옵션)
```

### 중급자용 - 반등 기회

```
✅ 112일 이평선: 95~105%
✅ 볼린저 밴드: 하단 터치 (20일, 2σ)
✅ 거래량: 평균 대비 1.5배 이상
```

### 고급자용 - 강한 상승

```
✅ 60일/112일 이평선: 95~105%
✅ 볼린저 밴드: 상단 돌파
✅ 이평선 정배열
✅ 거래량 급증
```

---

## 🎯 첫 테스트

### 간단한 API 테스트

```bash
# 헬스 체크
curl http://localhost:3000/health

# API 키 검증
curl -X POST http://localhost:3000/api/v1/validate-credentials \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET"
  }'
```

**응답 예시:**
```json
{
  "valid": true,
  "message": "인증 성공"
}
```

---

## ❓ 자주 묻는 질문 (FAQ)

### Q1: 검색이 너무 오래 걸려요
**A:** 현재 30개 종목 기준 약 3초 소요됩니다. API Rate Limit(초당 20건) 때문에 대량 종목은 시간이 걸립니다.

### Q2: "토큰 발급 실패" 오류가 나요
**A:** 
- APP KEY와 SECRET이 정확한지 확인
- 모의투자용 키를 사용했는지 확인
- 한국투자증권 API 서버 상태 확인

### Q3: Docker 없이 실행할 수 있나요?
**A:** 네! JDK 17과 Python 3.11만 있으면 됩니다. 위의 "방법 B" 참고.

### Q4: 전체 코스피 종목을 검색하고 싶어요
**A:** `KISApiClient.kt`의 `getAllStockCodes()` 함수에 원하는 종목 코드를 추가하세요. 또는 종목 마스터 파일을 다운로드하여 사용하세요.

### Q5: 실전 계좌로 사용할 수 있나요?
**A:** `isProduction=true`로 설정하면 가능하지만, 충분히 테스트 후 사용하세요.

---

## 🔧 문제 해결

### Docker 재실행 방법

```bash
# 간단한 재시작 (코드 변경 없을 때)
docker-compose restart

# 특정 서비스만 재시작
docker-compose restart kotlin-screener
docker-compose restart fastapi-gateway

# 완전 재시작 (코드 변경 있을 때)
docker-compose down
docker-compose up -d --build

# 강제 재빌드 (캐시 무시)
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### 서비스가 시작되지 않을 때

```bash
# 1. 포트 충돌 확인
lsof -i :8080  # Kotlin
lsof -i :3000  # FastAPI

# 2. 로그 확인
docker-compose logs -f                    # 전체 로그
docker-compose logs kotlin-screener       # Kotlin만
docker-compose logs fastapi-gateway       # FastAPI만

# 3. 실시간 로그 보기
docker-compose logs -f --tail=50

# 4. 컨테이너 상태 확인
docker-compose ps
docker ps -a
```

### 빌드 오류가 날 때

```bash
# Gradle 캐시 삭제
cd kotlin-screener
./gradlew clean

# Docker 이미지 재빌드
docker-compose build --no-cache
```

---

## 📞 지원

- 📖 [전체 README](./README.md) 참고
- 🐛 버그 리포트: GitHub Issues
- 💬 질문: Discussions

---

**🎉 축하합니다! 이제 Stock Hunter를 사용할 준비가 되었습니다!**

편안한 투자 되세요! 📈
