#!/bin/bash

echo "🚀 Stock Hunter - 로컬 실행 스크립트"
echo ""

# Python 확인
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3가 설치되어 있지 않습니다."
    echo "   Homebrew로 설치: brew install python@3.11"
    exit 1
fi

PYTHON_VERSION=$(python3 --version 2>&1 | awk '{print $2}' | cut -d. -f1,2)
echo "✅ Python $PYTHON_VERSION 발견"

# Java 확인
if ! command -v java &> /dev/null; then
    echo "⚠️  Java가 설치되어 있지 않습니다."
    echo "   Kotlin 서버를 실행하려면 JDK 17+ 필요"
    echo "   설치: brew install openjdk@17"
    echo ""
    echo "   FastAPI만 실행하시겠습니까? (y/n)"
    read -r response
    if [[ "$response" != "y" ]]; then
        exit 1
    fi
    SKIP_KOTLIN=true
else
    JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
    echo "✅ Java $JAVA_VERSION 발견"
    SKIP_KOTLIN=false
fi

echo ""
echo "📦 1단계: Python 의존성 설치 중..."
cd fastapi-gateway
if [ ! -d "venv" ]; then
    echo "   가상환경 생성 중..."
    python3 -m venv venv
fi

source venv/bin/activate
pip install -q --upgrade pip
pip install -q -r requirements.txt

echo "✅ Python 의존성 설치 완료"

if [ "$SKIP_KOTLIN" = false ]; then
    echo ""
    echo "🔧 2단계: Kotlin 서버 빌드 중..."
    cd ../kotlin-screener
    
    # Gradle wrapper 실행 권한 부여
    chmod +x gradlew
    
    # 백그라운드에서 Kotlin 서버 실행
    echo "   Kotlin 서버 시작 중... (Port 8080)"
    ./gradlew run > ../logs/kotlin.log 2>&1 &
    KOTLIN_PID=$!
    echo "   Kotlin 서버 PID: $KOTLIN_PID"
    
    # 서버 시작 대기
    echo "   서버 준비 중... (30초)"
    sleep 30
fi

echo ""
echo "🌐 3단계: FastAPI 서버 시작 중..."
cd ../fastapi-gateway
source venv/bin/activate

if [ "$SKIP_KOTLIN" = true ]; then
    echo ""
    echo "⚠️  주의: Kotlin 서버가 없어 실제 스크리닝은 작동하지 않습니다."
    echo "   웹 UI만 확인할 수 있습니다."
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Stock Hunter 서버가 시작되었습니다!"
echo ""
echo "   🌐 웹 UI: http://localhost:3000"
echo "   📡 API 문서: http://localhost:3000/docs"
if [ "$SKIP_KOTLIN" = false ]; then
    echo "   🔧 Kotlin 서버: http://localhost:8080"
fi
echo ""
echo "   종료하려면 Ctrl+C를 누르세요"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# FastAPI 실행
python main.py
