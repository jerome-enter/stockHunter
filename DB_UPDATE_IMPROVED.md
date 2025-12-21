# 🔄 DB 업데이트 개선 완료!

## ✅ 문제 해결

### 기존 문제 ❌

**시나리오:**
```
DB 최신 데이터: 12월 5일
오늘: 12월 16일

기존 동작:
→ 12월 16일 (1일)만 가져옴 ❌

결과:
→ 12월 6일 ~ 15일 (10일치) 누락! ❌
```

**코드:**
```kotlin
// 항상 1일만 가져옴
val response = kisApiClient.getDailyPrice(stockCode, days = 1)
```

---

## ✅ 개선된 방식

### 누락 기간 자동 계산 및 채우기

**시나리오:**
```
DB 최신 데이터: 12월 5일
오늘: 12월 16일

개선된 동작:
1. DB에서 마지막 날짜 확인 → 12월 5일
2. 누락 일수 계산 → 11일 (12/6 ~ 12/16)
3. 11일치 데이터 요청 ✅
4. 중복 제거하고 새로운 데이터만 저장 ✅

결과:
→ 12월 6일 ~ 16일 (11일치) 모두 저장! ✅
```

**코드:**
```kotlin
// DB에서 마지막 날짜 확인
val latestDate = database.getLatestDate(stockCode)
val daysSinceLastUpdate = ChronoUnit.DAYS.between(latestDate, today).toInt()

// 누락된 기간만큼 가져오기 (최대 100일)
val daysToFetch = minOf(daysSinceLastUpdate + 1, 100)

// API 호출
val response = kisApiClient.getDailyPrice(stockCode, days = daysToFetch)

// 기존 데이터보다 새로운 것만 필터링 및 저장
val newData = response.output.filter { it.date > latestDate }
database.savePriceBatch(stockCode, newData)
```

---

## 📊 동작 방식

### Case 1: 하루 빠진 경우 (정상)

```
DB 최신: 12월 15일
오늘: 12월 16일
→ 2일치 요청 (15일, 16일)
→ 16일만 저장 (15일은 이미 있음)
API 호출: 1회
```

### Case 2: 주말 지나서 업데이트

```
DB 최신: 12월 13일 (금요일)
오늘: 12월 16일 (월요일)
→ 4일치 요청 (13, 14, 15, 16)
→ 16일만 저장 (주말 데이터 없음)
API 호출: 1회
```

### Case 3: 일주일 이상 누락

```
DB 최신: 12월 5일
오늘: 12월 16일
→ 12일치 요청 (5~16일)
→ 6~16일 저장 (5일은 이미 있음)
API 호출: 1회
```

### Case 4: 100일 이상 누락

```
DB 최신: 9월 1일
오늘: 12월 16일
→ 100일치 요청 (최대값)
→ 새로운 100일 저장
API 호출: 1회

⚠️ 100일 이상 누락 시:
→ DB 재구축 권장 (전체 280일 다시 수집)
```

---

## 🎯 장점

### 1. 자동 갭 채우기 ✅
```
1일 누락이든 10일 누락이든 자동으로 전부 채움
→ 수동 관리 불필요
```

### 2. API 효율성 ✅
```
기존: 1일만 가져옴 (나머지 누락)
개선: 필요한 만큼만 가져옴 (딱 맞게)

예: 10일 누락 시
→ 10일치 1회 API 호출로 해결
```

### 3. 중복 방지 ✅
```
filter { price.date > latestDate }
→ 이미 있는 데이터는 저장 안 함
```

### 4. 안전성 ✅
```
최대 100일까지만 요청
→ API 부하 방지
→ 100일 이상은 재구축 권장
```

---

## 🧪 테스트 시나리오

### 1. 정상 업데이트 (1일 누락)

```bash
# DB 상태
curl http://localhost:8080/api/v1/database/status
# newestDate: "2025-12-15"

# 업데이트 실행
curl -X POST http://localhost:8080/api/v1/database/update \
  -H "Content-Type: application/json" \
  -d '{"appKey":"...","appSecret":"...","isProduction":true}'

# 결과
# newestDate: "2025-12-16" ✅
```

### 2. 주말 후 업데이트 (3일 누락)

```bash
# 금요일 DB: 2025-12-13
# 월요일 업데이트: 2025-12-16

# 자동으로:
# - 12/14 (토) - API에 데이터 없음
# - 12/15 (일) - API에 데이터 없음  
# - 12/16 (월) - 저장 ✅
```

### 3. 일주일 누락 (7일)

```bash
# 마지막: 2025-12-09
# 오늘: 2025-12-16

# 7일치 요청:
# → 12/10, 12/11, 12/12, 12/13 (평일) ✅
# → 12/14, 12/15 (주말 제외)
# → 12/16 (월) ✅
# 총 5영업일 저장!
```

---

## ⏱️ 성능

### API 호출 횟수

| 누락 기간 | API 호출 | 시간 |
|----------|---------|------|
| 1일 | 3,615회 (종목당 1회) | ~4분 |
| 7일 | 3,615회 (종목당 1회) | ~4분 |
| 30일 | 3,615회 (종목당 1회) | ~4분 |
| 100일 | 3,615회 (종목당 1회) | ~4분 |

**🎯 핵심:**
- 누락 기간과 관계없이 **종목당 1회 API 호출**
- 한 번에 필요한 만큼 가져옴
- 효율적! ✅

---

## 🚀 사용 방법

### 웹에서 업데이트

```
http://localhost:3000
→ [✨ DB 업데이트] 클릭
→ 4분 대기
→ 완료!
```

### API로 업데이트

```bash
curl -X POST http://localhost:8080/api/v1/database/update \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "YOUR_APP_KEY",
    "appSecret": "YOUR_APP_SECRET",
    "isProduction": true
  }'
```

### 로그 확인

```bash
docker logs -f stock-hunter-kotlin | grep -E "Latest|fetching|Updated"
```

**예상 로그:**
```
[005930] Latest: 2025-12-05, fetching 12 days
[005930] Updated 11 new records
[000660] Latest: 2025-12-10, fetching 7 days
[000660] Updated 6 new records
```

---

## 🎯 권장 사항

### 정기 업데이트

```
매일 장 마감 후 (오후 4시 이후):
→ [✨ DB 업데이트] 클릭
→ 4분 소요
→ 최신 상태 유지!
```

### 오랜만에 업데이트하는 경우

```
30일 이하 누락:
→ [✨ DB 업데이트] 사용 ✅

100일 이상 누락:
→ [🔄 DB 재구축] 권장 ✅
→ 전체 280일 데이터 다시 수집
```

---

## ✅ 결론

**이제 DB 업데이트가 훨씬 똑똑해졌습니다!**

- ✅ 자동으로 누락 기간 계산
- ✅ 필요한 만큼만 API 호출
- ✅ 중복 데이터 자동 제거
- ✅ 안정적이고 효율적

**언제든지 업데이트 버튼만 누르면 최신 상태 유지!** 🎉
