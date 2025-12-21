# 🔍 DB 상태 확인 및 문제 해결

## 📊 현재 DB 상태

```json
{
  "initialized": true,
  "totalStocks": 393,      // ❌ 500개 예상, 393개 실제
  "totalRecords": 11790,   // ❌ 150000개 예상, 11790개 실제
  "oldestDate": "2004-06-14",
  "newestDate": "2025-12-19"
}
```

**계산:**
```
11,790 ÷ 393 = 30개/종목

예상: 300개/종목
실제: 30개/종목

→ 한투 API가 여전히 30개만 반환!
```

---

## 🐛 문제 원인

### 가능성 1: API 파라미터 문제
```kotlin
// 현재 코드
val response = kisApiClient.getDailyPrice(stockCode, days = 100)

// days 파라미터가 무시되고 있을 수 있음
```

### 가능성 2: API 제약
한국투자증권 API가 **실제로 30개만 반환**할 수 있음:
- 무료 계정 제약
- 실전투자 계정 제약
- API 버전 문제

---

## ✅ 해결 방법

### 방법 1: forceRebuild로 다시 초기화
```bash
# 기존 DB 삭제하고 재구축
curl -X POST http://localhost:8080/api/v1/database/initialize \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_KEY",
    "appSecret": "YOUR_SECRET",
    "isProduction": true,
    "forceRebuild": true
  }'

# 로그 실시간 확인
docker logs -f stock-hunter-kotlin | grep "Batch"
```

**예상 로그:**
```
[005930] Batch 1/3: API returned 100 records  ← 100개씩!
[005930] Batch 2/3: API returned 100 records
[005930] Batch 3/3: API returned 100 records
[005930] Total collected: 300 days ✅
```

**만약 여전히 30개만 나온다면:**
```
[005930] Batch 1/3: API returned 30 records  ← 문제!
[005930] Batch 2/3: API returned 30 records
[005930] Batch 3/3: API returned 30 records
[005930] Total collected: 90 days ❌ (중복 제거 후 30개)
```

---

### 방법 2: API 파라미터 확인

**KISApiClient.kt의 getDailyPrice 확인:**
```kotlin
suspend fun getDailyPrice(stockCode: String, days: Int = 30): KISPriceResponse
```

**가능한 문제:**
- `days` 파라미터를 API에 안 보내고 있음
- API가 `days`를 무시함
- 다른 파라미터가 필요함

---

### 방법 3: 기간별 API 재시도

**기간별 API (`inquire-daily-itemchartprice`)를 다시 시도:**
```kotlin
// 날짜 범위로 명시적 요청
startDate = "20250224"  // 300일 전
endDate = "20251221"    // 오늘

// 이렇게 하면 날짜 범위만큼 반환할 수도 있음
```

---

## 🧪 테스트 방법

### 1. 웹에서 재초기화
```
http://localhost:3000

1. [🚀 DB 초기화] 클릭
2. 진행률 모달에서 확인:
   - "Batch 1/3", "Batch 2/3", "Batch 3/3" 로그
   - "Total collected: 300 days" 확인
```

### 2. 로그 실시간 확인
```bash
# 터미널에서 실행
docker logs -f stock-hunter-kotlin 2>&1 | grep -E "Batch|Total collected"

# 예상 출력:
[005930] Batch 1/3: API returned 100 records
[005930] Batch 2/3: API returned 100 records  
[005930] Batch 3/3: API returned 100 records
[005930] Total collected: 300 days
```

### 3. 완료 후 상태 확인
```bash
curl -s http://localhost:8080/api/v1/database/status

# 예상:
{
  "totalStocks": 500,
  "totalRecords": 150000,  // 300 × 500
  "oldestDate": "2025-02-24"
}
```

---

## 🎯 예상 결과

### 성공 시나리오
```
종목당 API 호출: 3회
종목당 데이터: 300개
전체 레코드: 150,000개 (500 × 300)
소요 시간: 2~3분

→ ma224 계산 가능! ✅
```

### 실패 시나리오 (여전히 30개만)
```
종목당 API 호출: 3회
종목당 데이터: 30개 (중복 제거)
전체 레코드: 15,000개 (500 × 30)

→ ma60까지만 가능 ❌
```

---

## 💡 최종 해결책

**만약 한투 API가 정말 30개만 준다면:**

### 옵션 A: 30개로 타협
- ma5, ma20, ma60까지만 사용
- 장기 지표 포기

### 옵션 B: 외부 데이터 소스
- 네이버 금융 크롤링
- Yahoo Finance API
- FinanceDataReader (Python)

### 옵션 C: 주봉/월봉 API
- 한투의 주봉 API 사용
- 더 긴 기간 조회 가능

---

## 🚀 지금 테스트!

```
http://localhost:3000

1. [🚀 DB 초기화] (forceRebuild)
2. 로그 확인
3. 300개 나오는지 체크!
```

**로그에 "Batch 1/3: API returned X records" 확인하세요!**

X가 100이면 성공! ✅  
X가 30이면 API 제약! ❌
