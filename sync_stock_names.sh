#!/bin/bash

# 종목명 동기화 스크립트
# DB의 종목코드로 한투 API에서 종목명 가져와서 갱신

APP_KEY="$1"
APP_SECRET="$2"
IS_PRODUCTION="${3:-true}"

if [ -z "$APP_KEY" ] || [ -z "$APP_SECRET" ]; then
    echo "Usage: $0 <APP_KEY> <APP_SECRET> [IS_PRODUCTION]"
    echo "Example: $0 YOUR_APP_KEY YOUR_APP_SECRET true"
    exit 1
fi

echo "🔄 종목명 동기화 시작..."
echo "📊 DB에서 종목코드 조회 중..."

# API 호출
curl -X POST http://localhost:8080/api/v1/database/sync-stock-names \
  -H "Content-Type: application/json" \
  -d "{
    \"appKey\": \"$APP_KEY\",
    \"appSecret\": \"$APP_SECRET\",
    \"isProduction\": $IS_PRODUCTION
  }"

echo ""
echo ""
echo "✅ 동기화 시작됨! (백그라운드 실행)"
echo "📝 진행상황 확인:"
echo "   docker logs -f stock-hunter-kotlin | grep -E 'Progress|Sync completed'"
echo ""
echo "⏱️  예상 시간: 3,600개 × 70ms = 약 4-5분"
