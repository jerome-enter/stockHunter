# 📥 Stock Hunter - 설치 가이드

## 🎯 3가지 실행 방법

### 방법 선택 가이드

| 방법 | 필요 | 장점 | 단점 |
|------|------|------|------|
| **간편 실행** | Python만 | 가장 쉬움 | 실제 스크리닝 불가 (UI만) |
| **완전 실행** | Python + Java | 모든 기능 사용 | 설치 필요 |
| **Docker** | Docker | 클린 환경 | 설치 용량 큼 |

---

## 🥇 방법 1: 간편 실행 (FastAPI만)

**웹 UI만 보고 싶을 때 - 5분**

### 1️⃣ Xcode Command Line Tools 라이선스 동의

```bash
sudo xcodebuild -license
# 'q' 눌러서 끝까지 스크롤
# 'agree' 입력
```

### 2️⃣ 실행

```bash
cd /Users/yonghokim/JeromeEnt/StockHunter
./start_simple.sh
```

브라우저에서 `http://localhost:3000` 접속!

> ⚠️ **제한사항**: 웹 UI는 보이지만 실제 스크리닝 기능은 작동 안 함 (Kotlin 서버 필요)

---

## 🥈 방법 2: 완전 실행 (전체 기능)

**실제 스크리닝까지 하고 싶을 때 - 15분**

### 1️⃣ Java (JDK 17+) 설치

```bash
# Homebrew로 설치
brew install openjdk@17

# 환경 변수 설정
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 확인
java -version
```

### 2️⃣ Python 3.11+ 설치

```bash
# Homebrew로 설치
brew install python@3.11

# 확인
python3 --version
```

### 3️⃣ Xcode Command Line Tools 라이선스

```bash
sudo xcodebuild -license
```

### 4️⃣ 전체 실행

```bash
cd /Users/yonghokim/JeromeEnt/StockHunter
./start_local.sh
```

이 스크립트는 자동으로:
1. Python 의존성 설치
2. Kotlin 서버 빌드 및 시작 (Port 8080)
3. FastAPI 서버 시작 (Port 3000)

브라우저에서 `http://localhost:3000` 접속!

---

## 🥉 방법 3: Docker 사용

**클린 환경에서 실행하고 싶을 때**

### 1️⃣ Docker Desktop 설치

[Docker Desktop for Mac 다운로드](https://www.docker.com/products/docker-desktop/)

설치 후:
```bash
docker --version
docker-compose --version
```

### 2️⃣ 실행

```bash
cd /Users/yonghokim/JeromeEnt/StockHunter
docker-compose up -d
```

브라우저에서 `http://localhost:3000` 접속!

---

## 🔧 수동 실행 (개발자용)

### Terminal 1: Kotlin 서버

```bash
cd kotlin-screener
./gradlew run
```

### Terminal 2: FastAPI 서버

```bash
cd fastapi-gateway
pip3 install -r requirements.txt
python3 main.py
```

---

## ❓ 설치 문제 해결

### Python이 없다고 나올 때

```bash
# Homebrew 설치 (없는 경우)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Python 설치
brew install python@3.11

# 경로 추가
echo 'export PATH="/opt/homebrew/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

### Java가 없다고 나올 때

```bash
# JDK 17 설치
brew install openjdk@17

# 심볼릭 링크 생성
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk

# 확인
java -version
```

---

### "Permission denied" 오류

```bash
chmod +x start_simple.sh
chmod +x start_local.sh
cd kotlin-screener && chmod +x gradlew
```

---

### 포트가 이미 사용 중일 때

```bash
# 8080 포트 사용 중인 프로세스 확인
lsof -ti:8080

# 종료
kill -9 $(lsof -ti:8080)

# 3000 포트도 동일
lsof -ti:3000
kill -9 $(lsof -ti:3000)
```

---

## 🎯 추천 설치 방법

### 처음 사용하는 경우
👉 **방법 1 (간편 실행)**: UI만 먼저 확인

### 실제로 사용하려는 경우  
👉 **방법 2 (완전 실행)**: 전체 기능 사용

### 깔끔하게 관리하고 싶은 경우
👉 **방법 3 (Docker)**: 격리된 환경

---

## 📊 설치 완료 확인

### 헬스 체크

```bash
# FastAPI
curl http://localhost:3000/health

# Kotlin (완전 설치 시)
curl http://localhost:8080/health
```

### 웹 브라우저

`http://localhost:3000` 접속 → 화면이 보이면 성공! ✅

---

## 🚀 다음 단계

설치가 완료되면:
1. [QUICK_START.md](./QUICK_START.md) - 5분 안에 시작하기
2. [README.md](./README.md) - 전체 가이드
3. [TEST_GUIDE.md](./TEST_GUIDE.md) - 테스트 방법

---

**문제가 계속되면 GitHub Issues에 올려주세요!** 🙏
