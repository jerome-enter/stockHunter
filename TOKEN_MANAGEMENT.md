# 🔐 한국투자증권 API 토큰 관리

## 📋 한국투자증권 API 토큰 정책

### 공식 정책
- **유효기간**: 24시간 (86,400초)
- **발급 제한**: **1일 1회 권장**
- **과도한 발급 시**: API 사용 제한 가능
- **권장 사항**: 토큰 재사용 필수

---

## ✅ Stock Hunter의 토큰 관리 전략

### 3단계 캐싱 시스템

```
1️⃣ 메모리 캐시 (가장 빠름)
   ↓ (없으면)
2️⃣ 파일 캐시 (서버 재시작 후에도 유지)
   ↓ (없으면)
3️⃣ API 호출 (새 토큰 발급)
```

---

## 🔧 구현 상세

### 1. 메모리 캐시
```kotlin
private var cachedToken: String? = null
private var tokenExpireTime: Instant? = null

// 만료 5분 전까지 재사용
if (Instant.now().isBefore(tokenExpireTime!!.minusSeconds(300))) {
    return cachedToken!!
}
```

**장점:**
- ⚡ 가장 빠름 (메모리 접근)
- 💰 API 호출 0회

**단점:**
- ⚠️ 서버 재시작 시 사라짐

---

### 2. 파일 캐시 (핵심!)

**위치:** `~/.stockhunter/token_dev_XXXX.json`

```json
{
  "token": "eyJ0eXAiOiJKV1Q...",
  "expiresAt": 1734739200,
  "issuedAt": 1734652800
}
```

**파일명 규칙:**
- `token_dev_XXXX.json` - 모의투자
- `token_prod_XXXX.json` - 실전투자
- `XXXX` = APP KEY 해시값

**장점:**
- ✅ 서버 재시작 후에도 토큰 유지
- ✅ 1일 1회 발급 준수
- ✅ Docker 컨테이너 재시작에도 안전

**구현:**
```kotlin
object TokenCache {
    private val cacheDir = File(System.getProperty("user.home"), ".stockhunter")
    
    fun saveToken(appKey: String, token: String, expiresInSeconds: Int, isProduction: Boolean) {
        val cacheData = CachedTokenData(
            token = token,
            expiresAt = now + expiresInSeconds,
            issuedAt = now
        )
        cacheFile.writeText(json.encodeToString(cacheData))
    }
    
    fun loadToken(appKey: String, isProduction: Boolean): String? {
        val cacheData = json.decodeFromString<CachedTokenData>(cacheFile.readText())
        
        // 만료 5분 전까지 유효
        if (now < cacheData.expiresAt - 300) {
            return cacheData.token
        }
        return null
    }
}
```

---

### 3. API 호출 (최후의 수단)

```kotlin
suspend fun getAccessToken(): String {
    // 1. 메모리 확인
    if (cachedToken != null && !expired) return cachedToken!!
    
    // 2. 파일 확인
    val cachedFromFile = TokenCache.loadToken(appKey, isProduction)
    if (cachedFromFile != null) {
        logger.info { "✅ Reusing cached token from file (no API call)" }
        return cachedFromFile
    }
    
    // 3. 새 토큰 발급
    logger.warn { "⚠️ Requesting NEW token from API (1일 1회 권장)" }
    val response = httpClient.post("$baseUrl/oauth2/tokenP") { ... }
    
    // 파일에 저장
    TokenCache.saveToken(appKey, token, expiresInSeconds, isProduction)
    
    return token
}
```

---

## 📊 실전 시나리오

### 시나리오 1: 정상적인 사용
```
10:00 - 첫 스크리닝 실행
        → 새 토큰 발급 (API 호출 1회)
        → 파일에 캐시 저장

11:00 - 두 번째 스크리닝
        → 메모리 캐시 사용 (API 호출 0회)

12:00 - 서버 재시작
        → 메모리 캐시 사라짐

13:00 - 세 번째 스크리닝
        → 파일 캐시 로드 (API 호출 0회) ✅
        → 메모리에 다시 로드

다음날 10:00
        → 24시간 경과, 토큰 만료
        → 새 토큰 발급 (API 호출 1회)
```

**결과:** 하루 1회만 API 호출! ✅

---

### 시나리오 2: Docker 재시작
```
10:00 - 스크리닝 실행
        → 새 토큰 발급
        → /root/.stockhunter/token_dev_XXX.json 저장

11:00 - docker-compose down
        → 컨테이너 삭제
        → 메모리 캐시 사라짐
        → 파일은 볼륨 마운트로 유지 ✅

11:05 - docker-compose up
        → 새 컨테이너 시작

11:10 - 스크리닝 실행
        → 파일 캐시 로드 (API 호출 0회) ✅
```

**주의:** Docker 컨테이너 내부의 `~/.stockhunter`는 컨테이너가 삭제되면 사라집니다.  
**해결책:** 볼륨 마운트 추가 (아래 참고)

---

## 🐳 Docker 볼륨 마운트 (권장)

### docker-compose.yml 수정

```yaml
services:
  kotlin-screener:
    volumes:
      - token-cache:/root/.stockhunter  # 토큰 캐시 영구 저장

volumes:
  token-cache:
    driver: local
```

**장점:**
- 컨테이너 삭제 후에도 토큰 유지
- 진짜 1일 1회 발급 달성

---

## 🔍 토큰 상태 확인 (디버깅)

### API 엔드포인트

```bash
# 토큰 상태 조회
curl "http://localhost:8080/api/v1/debug/token-status?appKey=YOUR_KEY"

# 응답 예시
{
  "status": "success",
  "tokenStats": "Token Age: 2.3h\nRemaining: 21.7h\nIssued At: 2024-12-20T10:00:00Z\nExpires At: 2024-12-21T10:00:00Z",
  "message": "한국투자증권 API 토큰은 24시간 유효하며, 파일 캐시를 통해 재사용됩니다."
}
```

### 캐시 초기화 (개발용)

```bash
# 모든 토큰 캐시 삭제
curl -X DELETE http://localhost:8080/api/v1/debug/clear-token-cache
```

---

## 📈 로그 확인

### 토큰 재사용 시 (정상)
```
✅ Reusing cached token from file (no API call needed)
Token Age: 3.2h
Remaining: 20.8h
```

### 새 토큰 발급 시 (경고)
```
⚠️ No valid cached token. Requesting NEW access token from API...
⚠️ 한국투자증권 API 정책: 1일 1회 토큰 발급 권장. 과도한 발급 시 제한될 수 있습니다.
✅ New access token acquired and cached. Expires in 86400s (~24h)
```

---

## ⚠️ 주의사항

### 하루에 여러 번 토큰을 발급받는 경우

**원인:**
1. 캐시 파일이 삭제됨
2. 시스템 시간이 변경됨
3. 다른 APP KEY 사용
4. Docker 컨테이너가 재생성됨 (볼륨 미사용)

**해결책:**
1. Docker 볼륨 마운트 사용
2. `~/.stockhunter` 디렉토리 백업
3. 로그 확인: `docker logs stock-hunter-kotlin | grep token`

---

## 🎯 권장 사항

### ✅ DO
- Docker 볼륨 마운트 사용
- 하루 1회 토큰 발급 확인
- 로그 모니터링
- 프로덕션 환경에서 주의

### ❌ DON'T
- 캐시 파일 수동 삭제
- 매 요청마다 새 API 클라이언트 생성
- 여러 서버에서 같은 APP KEY 동시 사용

---

## 📞 문제 해결

### 토큰이 계속 새로 발급되는 경우

```bash
# 1. 캐시 디렉토리 확인
docker exec stock-hunter-kotlin ls -la /root/.stockhunter/

# 2. 캐시 파일 내용 확인
docker exec stock-hunter-kotlin cat /root/.stockhunter/token_dev_*.json

# 3. 로그 확인
docker logs stock-hunter-kotlin | grep -i "token\|cache"
```

### API 제한에 걸린 경우

- 24시간 대기
- 한국투자증권 고객센터 문의
- 다른 APP KEY 사용 고려

---

## 📚 참고 자료

- [한국투자증권 OpenAPI 포털](https://apiportal.koreainvestment.com/)
- [OAuth 2.0 토큰 관리 가이드](https://apiportal.koreainvestment.com/apiservice/oauth2)
- 프로젝트 내 `KISApiClient.kt`
- 프로젝트 내 `TokenCache.kt`

---

**요약:** Stock Hunter는 **파일 기반 캐시**를 통해 한국투자증권 API의 **1일 1회 토큰 발급** 정책을 완벽하게 준수합니다! 🎉
