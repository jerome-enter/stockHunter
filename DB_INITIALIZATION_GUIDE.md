# 📊 Stock Hunter DB 초기화 가이드

## 🎯 개요

Stock Hunter는 **SQLite 기반 가격 데이터 캐시**를 사용하여:
- 60일/112일/224일 이동평균선 정확히 계산
- 초고속 스크리닝 (2초!)
- 한국투자증권 API 호출 최소화

---

## 🚀 Step 1: DB 상태 확인

### API 호출
```bash
curl http://localhost:8080/api/v1/database/status
```

### 응답 예시
```json
{
    "initialized": false,
    "totalStocks": 0,
    "totalRecords": 0,
    "oldestDate": null,
    "newestDate": null,
    "lastInit": null,
    "lastUpdate": null
}
```

**initialized가 false면 초기화 필요!**

---

## 🏗️ Step 2: DB 초기화 (최초 1회만!)

### API 호출
```bash
curl -X POST http://localhost:8080/api/v1/database/initialize \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET",
    "isProduction": true
  }'
```

### 성공 응답
```json
{
    "message": "Database initialization started",
    "estimatedTime": "15-20 minutes"
}
```

### ⚠️ 이미 초기화된 경우
```json
{
    "error": "Database already initialized",
    "totalStocks": 500,
    "lastInit": "2025-12-21",
    "message": "Use forceRebuild=true to rebuild, or use /update endpoint for daily updates"
}
```

**→ 이미 구축되었으므로 다시 할 필요 없음!** ✅

---

## 🔄 Step 3: 일일 업데이트 (매일 실행)

### 첫 스크리닝 전에 실행
```bash
curl -X POST http://localhost:8080/api/v1/database/update
```

### 응답
```json
{
    "message": "Daily update started"
}
```

**예상 시간:** 2~3분 (백그라운드 실행)

---

## 📊 진행 상황 확인

### 로그 모니터링
```bash
# 실시간 로그 확인
docker logs -f stock-hunter-kotlin

# 진행 상황만 확인
docker logs stock-hunter-kotlin | grep "Progress:"

# 예시 출력:
# Progress: 50/500 (50 success, 0 failed)
# Progress: 100/500 (100 success, 0 failed)
# Progress: 150/500 (150 success, 0 failed)
```

### 완료 메시지
```
✅ Database initialization completed!
Success: 500 / 500
Skipped: 0 (already exists)
Failed: 0
Total time: 120s (2m 0s)
```

---

## ⚠️ 중복 구축 방지

### 자동 방지 로직
```
1차 실행: 500개 종목 구축 (2분)
2차 실행: "Database already initialized" 에러 반환 ✅
3차 실행: "Database already initialized" 에러 반환 ✅
```

**→ 안심하고 여러 번 호출해도 괜찮습니다!**

### 강제 재구축 (필요 시)
```bash
curl -X POST http://localhost:8080/api/v1/database/initialize \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET",
    "isProduction": true,
    "forceRebuild": true
  }'
```

**주의:** 기존 데이터는 유지하되, 없는 종목만 추가합니다.

---

## 📈 동작 원리

### 초기 구축 (최초 1회)
```
1. 500개 종목 리스트 로드
2. 각 종목당:
   - 최근 100일 (API 호출 1회)
   - 그 전 100일 (API 호출 1회)
   - 그 전 100일 (API 호출 1회)
   = 종목당 3회, 총 1,500회

3. Rate Limiter: 초당 15건
   → 1,500 ÷ 15 = 100초 = 약 2분

4. SQLite DB 저장:
   ~/.stockhunter/price_data.db
```

### 일일 업데이트 (매일)
```
1. DB에 저장된 500개 종목 조회
2. 각 종목당 최신 날짜 확인
3. 오늘 데이터만 추가 (API 호출 1회)
   = 총 500회

4. Rate Limiter: 초당 15건
   → 500 ÷ 15 = 33초

5. 300일 이전 데이터 자동 삭제
```

### 스크리닝 (사용자 요청)
```
1. DB에서 300일 데이터 조회 (SQL)
2. ma60/ma112/ma224 계산
3. 볼린저밴드, 거래량 등 계산
4. 필터링

→ API 호출 0회! 초고속! ⚡
```

---

## 🎯 실전 사용 예시

### 시나리오: 첫 사용
```bash
# 1. 상태 확인
curl http://localhost:8080/api/v1/database/status
# → initialized: false

# 2. 초기화 시작
curl -X POST http://localhost:8080/api/v1/database/initialize \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "PSsvTb2a4LsrGOdKY8uSkTRfnZVHjAcWaecG",
    "appSecret": "3MjRouwOPzR+...",
    "isProduction": true
  }'
# → 2분 대기

# 3. 로그 확인
docker logs -f stock-hunter-kotlin
# → ✅ Database initialization completed!

# 4. 상태 재확인
curl http://localhost:8080/api/v1/database/status
# → initialized: true, totalStocks: 500

# 5. 웹에서 스크리닝 실행!
open http://localhost:3000
```

### 시나리오: 다음날 사용
```bash
# 1. 일일 업데이트 (자동 또는 수동)
curl -X POST http://localhost:8080/api/v1/database/update
# → 30초 대기

# 2. 스크리닝 실행 (즉시!)
# → DB에서 바로 조회, 2초 완료! ⚡
```

---

## 📁 DB 파일 위치

```
Docker 컨테이너 내부:
/root/.stockhunter/price_data.db

호스트 (Docker 볼륨):
stockhunter_token-cache 볼륨에 저장

확인 방법:
docker exec stock-hunter-kotlin ls -lh /root/.stockhunter/
```

---

## ⚠️ 주의사항

### 1. 한국투자증권 API 제약 준수
- ✅ 초당 15건으로 제한 (안전 마진 25%)
- ✅ 자동 재시도 (exponential backoff)
- ✅ 토큰 재사용 (Mutex 보호)

### 2. 중복 구축 방지
- ✅ 이미 초기화된 경우 에러 반환
- ✅ 종목별로 이미 있으면 건너뛰기
- ✅ forceRebuild=true로만 재구축 가능

### 3. 디스크 용량
- 500개 종목 × 300일 = 150,000 레코드
- 예상 크기: 약 30~50MB
- 충분히 작음! ✅

### 4. 백업
```bash
# DB 백업
docker cp stock-hunter-kotlin:/root/.stockhunter/price_data.db ./backup.db

# DB 복원
docker cp ./backup.db stock-hunter-kotlin:/root/.stockhunter/price_data.db
```

---

## 🎉 완료 체크리스트

- [ ] DB 상태 확인 (`initialized: false`)
- [ ] 초기화 API 호출 (POST /initialize)
- [ ] 로그에서 "✅ completed" 확인
- [ ] DB 상태 재확인 (`initialized: true`)
- [ ] 웹에서 스크리닝 테스트
- [ ] 결과에 종목 표시 확인
- [ ] 속도 체감 (2초!) ⚡

**모든 체크 완료 시 → 성공!** 🎊

---

## 🚀 다음 단계

1. **매일 업데이트** - 매일 오전 9시 자동 실행 (TODO)
2. **종목 확장** - 500개 → 2,500개 (CSV 추가)
3. **실시간 진행률** - WebSocket으로 UI 표시 (TODO)

**지금은 500개 종목으로 완벽히 작동합니다!** ✅
