#!/bin/bash

# 테스트: 한투 API로 종목명 가져오기
# 삼성전자(005930), SK하이닉스(000660) 테스트

APP_KEY="PSsvTb2a4LsrGOdKY8uSkTRfnZVHjAcWaecG"
APP_SECRET="3MjRouwOPzR+92pP5aZFH52MzNCCL0lwACx6SSzRnrHYd5vPxfypTIYdnYr8n/Yu/NXeJz8QNbj1/DaeDBsJ+c0aKZdKgYnTpmxEAyzaML8tAF1XwkHciMyYHozQBRqNbx/3653JexR5B/7td6mTvivQnduOAAxKc9gvIKU/I2G0NhDio3I="

echo "🧪 Testing stock name API..."
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

echo "✅ Token received: ${ACCESS_TOKEN:0:20}..."
echo ""

# 2. 삼성전자 종목명 조회
echo "2. Testing Samsung Electronics (005930)..."
SAMSUNG_RESPONSE=$(curl -s -X GET "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=005930" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H "appkey: $APP_KEY" \
  -H "appsecret: $APP_SECRET" \
  -H "tr_id: FHKST01010100")

SAMSUNG_NAME=$(echo $SAMSUNG_RESPONSE | jq -r '.output.prdy_vrss // empty')
echo "Response:"
echo $SAMSUNG_RESPONSE | jq .

if [ ! -z "$SAMSUNG_NAME" ]; then
  echo "✅ Samsung data received"
else
  echo "⚠️ No name field found"
fi
echo ""

# 3. SK하이닉스 종목명 조회
echo "3. Testing SK Hynix (000660)..."
sleep 0.1
HYNIX_RESPONSE=$(curl -s -X GET "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=000660" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H "appkey: $APP_KEY" \
  -H "appsecret: $APP_SECRET" \
  -H "tr_id: FHKST01010100")

echo "Response:"
echo $HYNIX_RESPONSE | jq .
echo ""

echo "🎉 Test completed!"
