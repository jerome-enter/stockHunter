# StockHunter 작업 로그

> 💡 **AI/개발자는 작업 시작 전 이 파일을 반드시 읽어 최근 변경사항과 진행 상황을 파악하세요!**

---

## 2024-12-23

### ✅ 완료된 작업

#### 1. 데이터 수집 방식 변경 (280일 → 400일)
- **변경사항**: API 호출 4회 → 6회로 증가
- **코드**: `PriceDataCollector.kt`
  - Line 134-135: `for (batch in 0 until 6)` - 6회 호출
  - Line 106-108: `cleanOldData(keepDays = 400)` - 400일 유지
- **이유**: ma224 계산을 위해 더 많은 과거 데이터 필요

#### 2. 자동 정리 정책 명확화
- **초기화 시**: 400일 이전 데이터 자동 삭제 (`cleanOldData` 호출)
- **일일 업데이트 시**: 데이터 삭제 안 함 (계속 누적)
- **코드**: `PriceDataCollector.kt` Line 305-306 주석 참조

#### 3. `.cursorrules` 업데이트
- 데이터 수집: 4회 280일 → **6회 400일**
- DB 아키텍처: 초기화 시에만 정리, 일일 업데이트는 누적
- 절대 금지 항목 추가: "일일 업데이트 시 오래된 데이터 삭제 금지"

#### 4. 코드 분석 완료
- **`stock_screener.html`** (2,345줄):
  - 스크리닝: `runScreener()` - Line 1716-1779
  - 차트 그리기: `drawCharts()` - Line 865-1358
  - 보조지표 계산:
    * 이동평균: `calculateMA()` - Line 1361-1378
    * 일목균형표: `calculateIchimoku()` - Line 1381-1421
    * 추세선: `calculateTrendline()` - Line 1508-1532
    * 지지/저항선: `calculateSupport/Resistance()` - Line 1535-1574
    * 추세 변곡점: Line 1423-1505
  - 차트 구조:
    * 280일 데이터 기반
    * 가격/거래량 차트 분리
    * Shift + 드래그 패닝
    * Chart.js 사용

---

## 📋 진행 중 / 예정 작업

### 🔄 다음 작업
- [ ] 차트/보조지표 관련 수정 (사용자 요청 대기 중)

### 💡 검토 필요 사항
- 단일 HTML 유지 vs 파일 분리 → **단일 HTML 유지 결정**

---

## 🐛 알려진 이슈

(없음)

---

## 📝 중요 참고사항

### 코드 위치
- **백엔드**: `kotlin-screener/src/main/kotlin/com/jeromeent/stockhunter/`
- **프론트엔드**: `stock_screener.html` (단일 파일)
- **규칙**: `.cursorrules`

### 데이터베이스
- **위치**: `/root/.stockhunter/price_data.db` (SQLite)
- **구조**:
  - `daily_prices`: 가격 데이터 (400일)
  - `stock_master`: 종목 마스터 (코드 + 이름)
  - `db_metadata`: 메타데이터

### API 엔드포인트
- `POST /api/v1/database/initialize` - DB 초기화 (2,500종목 × 6회)
- `POST /api/v1/database/update` - 일일 업데이트
- `POST /api/v1/screen` - 스크리닝
- `GET /api/v1/stocks/{code}/prices?days=280` - 차트 데이터

---

## 📌 작업 시 주의사항

1. ❌ **절대 금지**: 일일 업데이트 시 오래된 데이터 삭제
2. ❌ **절대 금지**: API 30일 데이터로 ma60/112/224 계산
3. ❌ **절대 금지**: 사용자 요청 없이 문서 자동 생성
4. ✅ **필수**: 모든 스크리닝은 DB에서 수행
5. ✅ **필수**: 이동평균은 라디오 버튼 (하나만 선택)

---

## 🔗 관련 문서

- `.cursorrules` - 프로젝트 규칙 (최우선 참조)
- `WORK_LOG.md` (이 파일) - 작업 이력

---

_마지막 업데이트: 2024-12-23_
