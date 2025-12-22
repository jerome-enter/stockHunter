#!/bin/bash

# 테스트: 주식현재가 시세 API의 모든 필드 확인

APP_KEY="PSsvTb2a4LsrGOdKY8uSkTRfnZVHjAcWaecG"
APP_SECRET="3MjRouwOPzR+92pP5aZFH52MzNCCL0lwACx6SSzRnrHYd5vPxfypTIYdnYr8n/Yu/NXeJz8QNbj1/DaeDBsJ+c0aKZdKgYnTpmxEAyzaML8tAF1XwkHciMyYHozQBRqNbx/3653JexR5B/7td6mTvivQnduOAAxKc9gvIKU/I2G0NhDio3I="

echo "🧪 Testing ALL fields from inquire-price API..."
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

echo "✅ Token: ${ACCESS_TOKEN:0:30}..."
echo ""

# 2. 삼성전자 조회 (custtype: P 추가)
echo "2. Calling inquire-price API for Samsung (005930) with custtype=P..."
RESPONSE=$(curl -s -X GET "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=005930" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H "appkey: $APP_KEY" \
  -H "appsecret: $APP_SECRET" \
  -H "tr_id: FHKST01010100" \
  -H "custtype: P")

echo "✅ Response received"
echo ""

# 3. 종목명 관련 필드만 추출
echo "3. Checking stock name fields..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 업종 한글명
BSTP_KOR_ISNM=$(echo $RESPONSE | jq -r '.output.bstp_kor_isnm // empty')
echo "bstp_kor_isnm (업종 한글명): $BSTP_KOR_ISNM"

# 단축종목코드
STCK_SHRN_ISCD=$(echo $RESPONSE | jq -r '.output.stck_shrn_iscd // empty')
echo "stck_shrn_iscd (단축종목코드): $STCK_SHRN_ISCD"

# 대표시장 한글명
RPRS_MRKT_KOR_NAME=$(echo $RESPONSE | jq -r '.output.rprs_mrkt_kor_name // empty')
echo "rprs_mrkt_kor_name (대표시장명): $RPRS_MRKT_KOR_NAME"

echo ""
echo "4. Checking for any '종목명' or '이름' fields..."
echo $RESPONSE | jq '.output' | grep -i "name\|isnm\|nm\|종목"

echo ""
echo "5. Full output keys:"
echo $RESPONSE | jq '.output | keys' | head -30

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 RESULT:"
if [ ! -z "$BSTP_KOR_ISNM" ]; then
  echo "✅ bstp_kor_isnm found: $BSTP_KOR_ISNM"
  echo "⚠️  But this is SECTOR name, not STOCK name"
else
  echo "❌ No bstp_kor_isnm field"
fi

echo ""
echo "📋 Full response (output only):"
echo $RESPONSE | jq '.output'
