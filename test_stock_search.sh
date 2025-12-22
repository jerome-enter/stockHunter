#!/bin/bash

# 테스트: 한투 종목검색 API로 종목명 가져오기

APP_KEY="PSsvTb2a4LsrGOdKY8uSkTRfnZVHjAcWaecG"
APP_SECRET="3MjRouwOPzR+92pP5aZFH52MzNCCL0lwACx6SSzRnrHYd5vPxfypTIYdnYr8n/Yu/NXeJz8QNbj1/DaeDBsJ+c0aKZdKgYnTpmxEAyzaML8tAF1XwkHciMyYHozQBRqNbx/3653JexR5B/7td6mTvivQnduOAAxKc9gvIKU/I2G0NhDio3I="

echo "🧪 Testing stock search API for stock name..."
echo ""

# 1. 토큰 발급
echo "1. Getting access token..."
TOKEN_RESPONSE=$(curl -s -X POST https://openapi.koreainvestment.com:9443/oauth2/tokenP \
  -H "Content-Type: application/json" \
  -d "{
    \"grant_type\": \"client_credentials\",
    \"appkey\": \"$APP_KEY\",
    \"appsecret\": \"$APP_SECRET\"
  }")

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
  echo "❌ Failed to get token"
  exit 1
fi

echo "✅ Token received"
echo ""

# 2. 종목 검색 API (종목명 포함)
echo "2. Testing stock search for Samsung (005930)..."
SEARCH_RESPONSE=$(curl -s -X GET "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/search-stock-info?PRDT_TYPE_CD=300&PDNO=005930" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H "appkey: $APP_KEY" \
  -H "appsecret: $APP_SECRET" \
  -H "tr_id: CTPF1604R" \
  -H "custtype: P")

echo "Response:"
echo $SEARCH_RESPONSE | jq .

STOCK_NAME=$(echo $SEARCH_RESPONSE | jq -r '.output1.prdt_name // .output.prdt_name // empty')
if [ ! -z "$STOCK_NAME" ]; then
  echo ""
  echo "✅ Stock name found: $STOCK_NAME"
else
  echo ""
  echo "⚠️ No stock name field"
fi
