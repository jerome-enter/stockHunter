# 🔐 토큰 Race Condition 해결

## 🐛 문제점

### 증상
웹 페이지를 새로고침할 때마다 **여러 번 토큰을 발급**받는 현상

### 원인
```
Request 1 (10:28:23.045) → 토큰 체크 → 없음 → 발급 요청!
Request 2 (10:28:23.047) → 토큰 체크 → 없음 → 발급 요청!  ← Race!
```

**Race Condition**: 두 요청이 동시에 들어왔을 때
- 둘 다 "토큰이 없다"고 판단
- 둘 다 한국투자증권 API 호출
- **2번 발급!** ❌

---

## ✅ 해결 방법

### Mutex 추가

**Before (버그):**
```kotlin
suspend fun getAccessToken(): String {
    // 두 요청이 동시에 여기 도달 가능!
    if (cachedToken == null) {
        // 둘 다 발급 요청!
        requestNewToken()
    }
}
```

**After (수정):**
```kotlin
private val tokenMutex = Mutex()

suspend fun getAccessToken(): String = tokenMutex.withLock {
    // 한 번에 하나씩만 실행!
    if (cachedToken == null) {
        requestNewToken()  // 한 번만 실행됨 ✅
    }
}
```

---

## 🔄 동작 흐름

### 시나리오: 웹페이지 새로고침 (여러 API 요청 동시 발생)

```
시간 t=0
├─ Request A: /stock-codes 호출
│  └─ getAccessToken() → Mutex 획득 ✅
│     └─ 토큰 없음
│        └─ API 호출 시작... (2초 소요)
│
├─ Request B: /screening 호출 (0.01초 후)
│  └─ getAccessToken() → Mutex 대기 중... ⏳
│
└─ Request C: /screening 호출 (0.02초 후)
   └─ getAccessToken() → Mutex 대기 중... ⏳

시간 t=2초
└─ Request A: API 응답 받음
   └─ 토큰 캐시에 저장
      └─ Mutex 해제 ✅

시간 t=2.01초
└─ Request B: Mutex 획득 ✅
   └─ 캐시 확인 → 있음! ✅
      └─ 캐시된 토큰 반환 (API 호출 안 함!)
         └─ Mutex 해제

시간 t=2.02초
└─ Request C: Mutex 획득 ✅
   └─ 캐시 확인 → 있음! ✅
      └─ 캐시된 토큰 반환 (API 호출 안 함!)
```

**결과:**
- API 호출: 1회만! ✅
- Request A: 2초 (API 호출)
- Request B: 2.01초 (대기 + 캐시)
- Request C: 2.02초 (대기 + 캐시)

---

## 📊 Before vs After

### Before (Race Condition)
```
웹페이지 새로고침
├─ 3개 API 요청 동시 발생
├─ 3개 모두 토큰 발급 요청
└─ 한국투자증권 API 3회 호출 ❌
   └─ 1일 제한 위반 위험!
```

### After (Mutex 보호)
```
웹페이지 새로고침
├─ 3개 API 요청 동시 발생
├─ 첫 번째만 토큰 발급 요청
└─ 한국투자증권 API 1회 호출 ✅
   ├─ Request 1: 새 토큰 발급 (API 호출)
   ├─ Request 2: 캐시 사용 (대기 후)
   └─ Request 3: 캐시 사용 (대기 후)
```

---

## 🔧 적용된 파일

1. **KISApiClient.kt** (국내주식)
   ```kotlin
   private val tokenMutex = Mutex()
   
   suspend fun getAccessToken(): String = tokenMutex.withLock {
       // 동시 접근 방지
   }
   ```

2. **KISUSApiClient.kt** (해외주식)
   ```kotlin
   private val tokenMutex = Mutex()
   
   suspend fun getAccessToken(): String = tokenMutex.withLock {
       // 동시 접근 방지
   }
   ```

---

## 🎯 효과

### 성능
- ✅ 불필요한 API 호출 제거
- ✅ 대기 시간 최소화 (밀리초 수준)
- ✅ 캐시 일관성 보장

### 안정성
- ✅ 한국투자증권 1일 1회 정책 준수
- ✅ API 제한 위반 방지
- ✅ 토큰 캐시 일관성

### 사용자 경험
- ✅ 웹 페이지 새로고침 안전
- ✅ 동시 스크리닝 요청 안전
- ✅ 빠른 응답 (캐시 사용)

---

## 📝 테스트 방법

### 1. 동시 요청 테스트
```bash
# 3개 요청 동시에 발생
curl http://localhost:3000/api/v1/stock-codes &
curl http://localhost:3000/api/v1/stock-codes &
curl http://localhost:3000/api/v1/stock-codes &
wait

# 로그 확인
docker logs stock-hunter-kotlin | grep "token"
# → "Requesting NEW access token" 한 번만 나와야 함!
```

### 2. 웹 브라우저 테스트
1. `http://localhost:3000` 접속
2. **F5 여러 번 빠르게 연타**
3. 로그 확인:
   ```bash
   docker logs stock-hunter-kotlin --tail=50 | grep "token"
   ```
4. **"Requesting NEW access token"이 한 번만 나오면 성공!**

---

## ⚠️ 주의사항

### Mutex vs Lock
- ✅ Kotlin Coroutine의 `Mutex` 사용 (비동기 안전)
- ❌ Java의 `synchronized` 사용 불가 (코루틴에서 블로킹 위험)

### 성능 영향
- 대기 시간: 보통 ~10ms (거의 느낌 없음)
- 토큰 발급 시간: ~2초 (API 호출)
- 캐시 사용 시간: ~1ms

### 만료된 토큰
- 만료 5분 전부터 새로 발급
- 여전히 Mutex로 보호됨
- 한 번만 발급!

---

## 🎉 결론

**웹 페이지를 아무리 새로고침해도 토큰은 하루에 한 번만 발급됩니다!**

- ✅ Race Condition 해결
- ✅ 1일 1회 정책 완벽 준수
- ✅ 동시 요청 안전
- ✅ 캐시 일관성

**이제 안심하고 사용하세요!** 🚀
