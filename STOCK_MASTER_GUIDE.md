# 📋 종목 마스터 데이터 관리 가이드

## 🎯 현재 상황

### ⚠️ 알려드립니다

**현재 구현:**
- ❌ 30개 종목만 하드코딩
- ✅ 조건 필터링은 완벽히 작동
- ✅ 마스터 파일 캐싱 구조 완성
- ⏳ 전체 종목 다운로드 API 통합 필요

**검색 가능한 종목 (30개):**
- 삼성전자, SK하이닉스, NAVER, LG화학, 삼성SDI, 카카오, 현대차, 현대모비스, 신한지주, 삼성바이오로직스, 셀트리온, 삼성물산, 한국전력, SK텔레콤, SK이노베이션, 기아, 포스코퓨처엠, KB금융, SK, LG, 삼성전기, S-Oil, 롯데케미칼, 삼성생명, LG전자, 하나금융지주, 삼성에스디에스, 한국조선해양, 삼성화재, KT&G

---

## 🏗️ 구현된 캐싱 구조

### 파일 기반 마스터 캐싱

```
~/.stockhunter/
├── stock_master_KOSPI_KOSDAQ.json  # 종목 마스터 (7일 캐시)
├── token_dev_XXXX.json              # API 토큰 (24시간 캐시)
└── token_prod_XXXX.json
```

### 동작 방식

```kotlin
fun getAllStockCodes(): List<String> {
    // 1. 캐시 확인 (7일 이내)
    val cached = StockMasterCache.loadMasterData()
    if (cached != null) return cached.map { it.code }
    
    // 2. API에서 다운로드
    val stocks = downloadStockMaster()
    
    // 3. 캐시에 저장
    StockMasterCache.saveMasterData(stocks)
    
    return stocks.map { it.code }
}
```

**장점:**
- ✅ 7일간 캐시 유지 (상장/폐지가 자주 없음)
- ✅ 서버 재시작 후에도 유지
- ✅ Docker 볼륨에 저장 (영구 보관)
- ✅ 자동 갱신

---

## 🔧 전체 종목 가져오기 방법

### 방법 1: 한국투자증권 API (TODO)

**필요한 API:**
```
업종별 현재가 (FHKST01010900)
또는
종목 마스터 파일 다운로드 API
```

**구현 예정:**
```kotlin
suspend fun downloadStockMaster(): List<StockInfo> {
    // KOSPI 전체 종목
    val kospiStocks = fetchSectorStocks("0001")
    
    // KOSDAQ 전체 종목  
    val kosdaqStocks = fetchSectorStocks("1001")
    
    return kospiStocks + kosdaqStocks
}
```

**문제점:**
- API 호출 횟수 많음 (Rate Limit 고려)
- 응답 구조 확인 필요
- 초기 다운로드 시간 오래 걸림

---

### 방법 2: CSV 파일 사용 (추천)

**KRX (한국거래소)에서 제공하는 전체 종목 리스트를 CSV로 받아서 사용**

#### Step 1: 종목 리스트 CSV 준비

```csv
code,name,market,sector
005930,삼성전자,KOSPI,전기전자
000660,SK하이닉스,KOSPI,전기전자
035420,NAVER,KOSPI,서비스업
...
```

**다운로드 방법:**
1. [KRX 정보데이터시스템](http://data.krx.co.kr) 접속
2. 기본통계 → 주식 → 개별종목 시세 → 전종목 시세
3. Excel/CSV 다운로드

또는

[한국투자증권 개발자 포털](https://apiportal.koreainvestment.com/)에서 마스터 파일 다운로드

#### Step 2: CSV 파싱 구현

```kotlin
fun loadStocksFromCSV(filePath: String): List<StockInfo> {
    val stocks = mutableListOf<StockInfo>()
    
    File(filePath).bufferedReader().use { reader ->
        reader.lineSequence()
            .drop(1) // 헤더 스킵
            .forEach { line ->
                val cols = line.split(",")
                stocks.add(StockInfo(
                    code = cols[0],
                    name = cols[1],
                    market = cols[2],
                    sector = cols.getOrNull(3)
                ))
            }
    }
    
    return stocks
}
```

#### Step 3: 프로젝트에 포함

```
StockHunter/
└── kotlin-screener/
    └── src/main/resources/
        └── stock_master.csv  # 전체 종목 리스트
```

**장점:**
- ✅ API 호출 불필요
- ✅ 빠른 로딩
- ✅ 간단한 관리
- ✅ 수동 업데이트 가능

**단점:**
- ❌ 수동 갱신 필요 (월 1회 정도)
- ❌ 신규 상장 종목 즉시 반영 안 됨

---

### 방법 3: 크롤링 (비추천)

KRX 웹사이트를 크롤링하여 종목 리스트 수집

**문제점:**
- 법적 이슈 가능성
- 웹사이트 구조 변경 시 동작 안 함
- 불안정

---

## 🚀 권장 구현 순서

### Phase 1: CSV 파일 방식 (지금 바로 가능) ✅

```bash
# 1. KRX에서 전종목 리스트 다운로드 (Excel)
# 2. CSV로 변환
# 3. 프로젝트에 포함

StockHunter/
└── kotlin-screener/
    └── src/main/resources/
        └── stock_master.csv  # 2,500+ 종목
```

**구현 시간:** 30분
**검색 가능 종목:** 전체 (2,500+개)

---

### Phase 2: 한국투자증권 API 통합 (향후)

```kotlin
// 업종별 종목 조회 API 활용
suspend fun downloadFromKIS(): List<StockInfo> {
    // 전체 업종 코드 순회
    // 각 업종별 종목 수집
    // 중복 제거
}
```

**장점:**
- 자동 갱신
- 최신 데이터 보장

**단점:**
- API 호출 많음
- 초기 로딩 느림
- Rate Limit 고려 필요

---

## 📊 현재 vs 개선 후 비교

| 항목 | 현재 (하드코딩) | CSV 방식 | API 방식 |
|------|----------------|----------|----------|
| **종목 수** | 30개 | 2,500+개 | 2,500+개 |
| **초기 로딩** | 즉시 | ~1초 | ~30초 |
| **데이터 신선도** | 고정 | 수동 갱신 | 자동 갱신 |
| **API 호출** | 0회 | 0회 | 100+회 |
| **유지보수** | 어려움 | 쉬움 | 자동 |
| **구현 난이도** | ✅ 완료 | 🟡 쉬움 | 🔴 중간 |

---

## 🎯 제 추천

### 단기 (지금 바로)
**CSV 파일 방식 사용**

1. KRX에서 전종목 리스트 다운로드
2. CSV 파싱 코드 추가 (간단)
3. 마스터 캐시에 저장
4. 전체 종목 검색 가능!

**시간:** 30분 ~ 1시간
**효과:** 30개 → 2,500개 종목 검색

### 장기 (향후 개선)
**한국투자증권 API 통합**

- 자동 갱신
- 최신 데이터 보장
- 완전 자동화

---

## 🔍 디버그 API

### 마스터 캐시 상태 확인

```bash
curl http://localhost:8080/api/v1/debug/master-status

# 응답 예시
{
  "status": "cached",
  "totalStocks": 2547,
  "downloadedAt": "2024-12-20T10:00:00Z",
  "age": "2.5 days",
  "remaining": "4.5 days"
}
```

### 캐시 강제 갱신

```bash
curl -X DELETE http://localhost:8080/api/v1/debug/clear-master-cache
```

---

## 📝 TODO

### 우선순위 높음
- [ ] CSV 파일로 전종목 리스트 추가
- [ ] CSV 파싱 코드 구현
- [ ] 테스트 (2,500개 종목 검색)

### 우선순위 중간
- [ ] 한국투자증권 업종별 조회 API 연동
- [ ] 자동 갱신 로직
- [ ] 성능 최적화

### 우선순위 낮음
- [ ] 실시간 상장/폐지 감지
- [ ] 종목 정보 자동 업데이트

---

## ❓ FAQ

### Q: 왜 30개만 검색되나요?
**A:** 종목 리스트가 하드코딩되어 있기 때문입니다. CSV 파일 방식으로 전환하면 전체 종목 검색 가능합니다.

### Q: 얼마나 자주 갱신하나요?
**A:** 
- 토큰: 24시간마다 자동
- 마스터: 7일마다 자동 (CSV는 수동)

### Q: 신규 상장 종목은?
**A:** 
- CSV 방식: 수동 업데이트 필요
- API 방식: 자동 반영

### Q: 지금 바로 전체 종목 검색하려면?
**A:** CSV 파일을 추가하고 파싱 코드를 구현하면 됩니다 (30분 소요)

---

## 🎉 결론

**현재 상태:**
- ✅ 마스터 캐싱 구조 완성
- ✅ 토큰 관리 완벽
- ⏳ 전체 종목 데이터 필요

**다음 단계:**
1. KRX에서 CSV 다운로드
2. 파싱 코드 추가
3. 2,500개 종목 검색 가능!

**지금 바로 구현해드릴까요?** 🚀
