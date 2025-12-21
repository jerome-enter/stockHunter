#!/bin/bash

# 한국투자증권 API 테스트 스크립트

echo "🔍 한국투자증권 API 테스트"
echo "======================================"
echo ""

# .env.test 파일 확인
if [ ! -f "kotlin-screener/.env.test" ]; then
    echo "❌ .env.test 파일이 없습니다!"
    echo "kotlin-screener/.env.test 파일에 API 키를 입력하세요."
    exit 1
fi

# API 키 로드
source kotlin-screener/.env.test

if [ -z "$KIS_APP_KEY" ] || [ "$KIS_APP_KEY" = "여기에_앱키_붙여넣기" ]; then
    echo "❌ KIS_APP_KEY가 설정되지 않았습니다!"
    echo "kotlin-screener/.env.test 파일을 확인하세요."
    exit 1
fi

echo "✅ API 키 로드 완료"
echo ""

# Docker에서 curl 테스트
echo "📡 API 테스트 실행 중..."
echo ""

# 토큰 발급 테스트
echo "1. 토큰 발급 테스트..."
docker exec stock-hunter-kotlin curl -s -X POST \
  "https://openapi.koreainvestment.com:9443/oauth2/tokenP" \
  -H "Content-Type: application/json" \
  -d "{
    \"grant_type\": \"client_credentials\",
    \"appkey\": \"$KIS_APP_KEY\",
    \"appsecret\": \"$KIS_APP_SECRET\"
  }" | head -n 10

echo ""
echo "======================================"
echo "테스트 완료!"
