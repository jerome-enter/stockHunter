# 📤 종목 마스터 파일 업로드 가이드

## ✅ 구현 완료!

### 🎯 기능

**수동 파일 업로드로 간단하게 전체 종목 관리!**

- ✅ 한국투자증권 종목정보파일 직접 업로드
- ✅ KOSPI + KOSDAQ 파일 분리 업로드
- ✅ DB 자동 저장 및 캐싱 (7일간 유효)
- ✅ 2,500+ 개 종목 즉시 반영

---

## 📋 사용 방법

### 1단계: 종목 파일 다운로드

**한국투자증권 API 포털**에서 종목정보파일 다운로드:

```
https://apiportal.koreainvestment.com/apiservice-category
→ API 문서 > 종목정보파일
```

**다운로드할 파일:**
- KOSPI 종목코드 파일 (`.mst` 또는 `.txt`)
- KOSDAQ 종목코드 파일 (`.mst` 또는 `.txt`)

**파일 형식 예시:**
```
005930   KR7005930003삼성전자                                ST1...
000660   KR7000660001SK하이닉스                             ST1...
035720   KR7035720002카카오                                 ST1...
```

---

### 2단계: 웹에서 파일 업로드

```
http://localhost:3000
```

**📊 데이터베이스 상태 섹션** 아래에 있는 **📁 종목 마스터 파일 업로드** 섹션:

1. **KOSPI 파일 선택** - KOSPI 종목코드 파일 업로드
2. **KOSDAQ 파일 선택** - KOSDAQ 종목코드 파일 업로드
3. **[📤 종목 파일 업로드]** 버튼 클릭

**결과:**
```
✅ 업로드 완료! KOSPI: 912개, KOSDAQ: 1,621개, 총: 2,533개
```

---

### 3단계: DB 초기화 실행

종목 파일 업로드 후 **[🚀 DB 초기화]** 또는 **[🔄 DB 재구축]** 버튼 클릭:

```
업로드된 2,533개 종목에 대해 280일 가격 데이터 수집 시작!

예상 시간: 2,533 × 4회 = 10,132회 API 호출
         = 10,132 ÷ 15건/초 = 675초 = 약 11분
```

---

## 🔄 파일 구조 & 파싱

### 지원 파일 형식

```
종목코드 (6자리) + ISIN + 종목명 + 시장코드 + ...
│                  │        │        │
005930             KR7...   삼성전자  ST1 (KOSPI)
259960             KR7...   크래프톤  ST2 (KOSDAQ 추정)
```

**파싱 로직:**
- **위치 0-5**: 종목코드 (6자리, 예: `005930`)
- **위치 61-63**: 시장코드 (추후 자동 감지 가능)
- 현재는 파일명으로 시장 구분 (`kospi` → KOSPI, `kosdaq` → KOSDAQ)

---

## 💾 DB 저장 구조

### stock_master 테이블

```sql
CREATE TABLE stock_master (
    stock_code TEXT PRIMARY KEY,   -- 005930
    market TEXT NOT NULL,           -- KOSPI/KOSDAQ
    is_active INTEGER DEFAULT 1,    -- 활성 여부
    created_at TEXT,
    updated_at TEXT
);
```

### 자동 갱신 정책

```
업로드 시: DB에 즉시 저장 ✅
유효 기간: 7일
7일 후: 재업로드 필요 (자동 체크)
```

---

## 🎯 장점

### 1. 간단함 ⚡
```
기존: 네이버 크롤링 (불안정, 느림)
→ 새로운 방식: 파일 업로드 (확실, 빠름)
```

### 2. 공식 데이터 📊
```
한국투자증권 공식 종목정보파일
→ 정확하고 믿을 수 있음
```

### 3. 캐싱 💾
```
한 번 업로드 → 7일간 재사용
→ 매번 크롤링 불필요
```

### 4. 유연성 🔧
```
원하는 시점에 수동 갱신 가능
→ 신규 상장/상장 폐지 즉시 반영
```

---

## 🧪 테스트

### 1. 웹 열기
```bash
open http://localhost:3000
```

### 2. 파일 업로드
- KOSPI 파일 선택
- KOSDAQ 파일 선택
- [📤 종목 파일 업로드] 클릭

### 3. 로그 확인
```bash
docker logs -f stock-hunter-kotlin | grep -E "Uploaded|stocks"
```

**기대 로그:**
```
✅ Uploaded KOSPI: 912 stocks
✅ Uploaded KOSDAQ: 1621 stocks
✅ Stock master refreshed: 2533 stocks
```

### 4. DB 상태 확인
```bash
curl http://localhost:8080/api/v1/database/status
```

**기대 응답:**
```json
{
  "initialized": false,
  "totalStocks": 0,  // 아직 가격 데이터는 없음 (초기화 필요)
  "message": "종목은 업로드됨, 이제 DB 초기화 필요"
}
```

### 5. DB 초기화
웹에서 **[🚀 DB 초기화]** 클릭 → 11분 대기 → 완료!

---

## 📊 API 엔드포인트

### POST /api/v1/database/upload-stock-master

**요청:**
```http
POST /api/v1/database/upload-stock-master
Content-Type: multipart/form-data

files: [kospi_code.mst, kosdaq_code.mst]
```

**응답:**
```json
{
  "message": "Stock master files uploaded successfully",
  "kospiCount": 912,
  "kosdaqCount": 1621,
  "totalCount": 2533
}
```

---

## 🔧 문제 해결

### Q1: 파일 형식이 다른 경우?

**A:** 파일의 앞 6자리가 종목코드라면 자동으로 파싱됩니다:
```
005930   ... (어떤 내용이든)
000660   ...
035720   ...
```

### Q2: 파일명을 다르게 해도 되나요?

**A:** 파일명에 `kospi` 또는 `kosdaq`가 포함되어야 합니다:
- ✅ `kospi_code.mst`
- ✅ `KOSPI_20250122.txt`
- ✅ `kosdaq_master.dat`
- ❌ `stock_list.txt` (시장 구분 불가)

### Q3: 한 번에 하나만 업로드해도 되나요?

**A:** 네! KOSPI만 업로드하고 나중에 KOSDAQ 추가 가능합니다.

### Q4: 기존 데이터가 덮어씌워지나요?

**A:** `INSERT OR REPLACE` 방식이라 중복은 덮어쓰고, 새로운 것은 추가됩니다.

---

## 🚀 다음 단계

**1. 파일 업로드 완료** ✅
**2. [🚀 DB 초기화] 클릭** → 280일 가격 데이터 수집 (11분)
**3. 스크리닝 실행!** 🎯

---

**이제 2,500+ 개 전체 종목으로 ma224 전략을 사용할 수 있습니다!** 🎉
