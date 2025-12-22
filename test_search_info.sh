#!/bin/bash

# 테스트: 종목검색 API로 종목명 가져오기
# API: /uapi/domestic-stock/v1/quotations/search-info

APP_KEY="PSsvTb2a4LsrGOdKY8uSkTRfnZVHjAcWaecG"
APP_SECRET="3MjRouwOPzR+92pP5aZFH52MzNCCL0lwACx6SSzRnrHYd5vPxfypTIYdnYr8n/Yu/NXeJz8QNbj1/DaeDBsJ+c0aKZdKgYnTpmxEAyzaML8tAF1XwkHciMyYHozQBRqNbx/3653JexR5B/7td6mTvivQnduOAAxKc9gvIKU/I2G0NhDio3I="

echo "🧪 Testing search-info API for stock names..."
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
  echo "❌ Failed to get token:"
  echo $TOKEN_RESPONSE | jq .
  exit 1
fi

echo "✅ Token: ${ACCESS_TOKEN:0:30}..."
echo ""

# 2. 삼성전자 검색 (005930)
echo "2. Testing Samsung Electronics (005930)..."
SAMSUNG_RESPONSE=$(curl -s -X GET "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/search-info?PRDT_TYPE_CD=300&PDNO=005930" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H "appkey: $APP_KEY" \
  -H "appsecret: $APP_SECRET" \
  -H "tr_id: CTPF1604R" \
  -H "custtype: P")

echo "Full response:"
echo $SAMSUNG_RESPONSE | jq .
echo ""

# prdt_name 추출
SAMSUNG_NAME=$(echo $SAMSUNG_RESPONSE | jq -r '.output1[0].prdt_name // .output[0].prdt_name // .prdt_name // empty')
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ ! -z "$SAMSUNG_NAME" ]; then
  echo "✅ Samsung name: $SAMSUNG_NAME"
else
  echo "⚠️ prdt_name not found"
fi
echo ""

# 3. SK하이닉스 검색 (000660)
echo "3. Testing SK Hynix (000660)..."
sleep 0.1
HYNIX_RESPONSE=$(curl -s -X GET "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/search-info?PRDT_TYPE_CD=300&PDNO=000660" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H "appkey: $APP_KEY" \
  -H "appsecret: $APP_SECRET" \
  -H "tr_id: CTPF1604R" \
  -H "custtype: P")

echo "Full response:"
echo $HYNIX_RESPONSE | jq .
echo ""

HYNIX_NAME=$(echo $HYNIX_RESPONSE | jq -r '.output1[0].prdt_name // .output[0].prdt_name // .prdt_name // empty')
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ ! -z "$HYNIX_NAME" ]; then
  echo "✅ SK Hynix name: $HYNIX_NAME"
else
  echo "⚠️ prdt_name not found"
fi
echo ""

# 4. 코리안리 검색 (003690)
echo "4. Testing Korean Re (003690)..."
sleep 0.1
KOREAN_RESPONSE=$(curl -s -X GET "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/search-info?PRDT_TYPE_CD=300&PDNO=003690" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H "appkey: $APP_KEY" \
  -H "appsecret: $APP_SECRET" \
  -H "tr_id: CTPF1604R" \
  -H "custtype: P")

KOREAN_NAME=$(echo $KOREAN_RESPONSE | jq -r '.output1[0].prdt_name // .output[0].prdt_name // .prdt_name // empty')
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ ! -z "$KOREAN_NAME" ]; then
  echo "✅ Korean Re name: $KOREAN_NAME"
else
  echo "⚠️ prdt_name not found"
  echo "Response:"
  echo $KOREAN_RESPONSE | jq .
fi

echo ""
echo "🎉 Test completed!"
