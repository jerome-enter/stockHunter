#!/bin/bash

echo "🚀 Stock Hunter - 간편 시작 (FastAPI만)"
echo ""

# Xcode 라이선스 체크
if python3 -c "import sys" 2>&1 | grep -q "Xcode"; then
    echo "⚠️  Xcode 라이선스 동의가 필요합니다:"
    echo "   sudo xcodebuild -license"
    echo ""
    exit 1
fi

cd /Users/yonghokim/JeromeEnt/StockHunter/fastapi-gateway

# 의존성 설치 (처음 한 번만)
if [ ! -f ".installed" ]; then
    echo "📦 Python 패키지 설치 중..."
    python3 -m pip install --user -r requirements.txt
    touch .installed
    echo "✅ 설치 완료"
fi

echo ""
echo "🌐 FastAPI 서버 시작 중..."
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ 웹 UI: http://localhost:3000"
echo "   (브라우저에서 위 주소로 접속하세요)"
echo ""
echo "⚠️  주의: Kotlin 서버 없이 실행 중"
echo "   → 웹 UI는 보이지만 실제 스크리닝은 작동 안 함"
echo "   → 전체 기능을 사용하려면 Kotlin 서버도 필요"
echo ""
echo "   종료: Ctrl+C"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

python3 main.py
