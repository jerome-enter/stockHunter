#!/bin/bash

# 한국투자증권 API 고급 테스트 (토큰 캐싱 포함)

set -e

# 색상 정의
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🔍 한국투자증권 API 테스트 (토큰 캐싱)${NC}"
echo "======================================"
echo ""

# .env.test 파일 로드
if [ ! -f "kotlin-screener/.env.test" ]; then
    echo -e "${RED}❌ .env.test 파일이 없습니다!${NC}"
    exit 1
fi

source kotlin-screener/.env.test

if [ -z "$KIS_APP_KEY" ] || [ "$KIS_APP_KEY" = "여기에_앱키_붙여넣기" ]; then
    echo -e "${RED}❌ KIS_APP_KEY가 설정되지 않았습니다!${NC}"
    exit 1
fi

# 토큰 캐시 파일
TOKEN_CACHE="/tmp/kis_token_cache.json"
TOKEN=""

# 토큰 캐시 확인 함수
get_cached_token() {
    if [ -f "$TOKEN_CACHE" ]; then
        # 만료 시간 확인 (24시간)
        CACHE_TIME=$(stat -f %m "$TOKEN_CACHE" 2>/dev/null || stat -c %Y "$TOKEN_CACHE" 2>/dev/null)
        CURRENT_TIME=$(date +%s)
        AGE=$((CURRENT_TIME - CACHE_TIME))
        
        # 23시간 (82800초) 이내면 재사용
        if [ $AGE -lt 82800 ]; then
            TOKEN=$(jq -r '.access_token' "$TOKEN_CACHE")
            echo -e "${GREEN}✅ 캐시된 토큰 사용 (나이: $((AGE / 3600))시간)${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠️ 캐시된 토큰이 만료됨 (나이: $((AGE / 3600))시간)${NC}"
        fi
    fi
    return 1
}

# 새 토큰 발급 함수
issue_new_token() {
    echo -e "${YELLOW}🔄 새 토큰 발급 중...${NC}"
    
    RESPONSE=$(curl -s -X POST "https://openapi.koreainvestment.com:9443/oauth2/tokenP" \
        -H "Content-Type: application/json" \
        -d "{
            \"grant_type\": \"client_credentials\",
            \"appkey\": \"$KIS_APP_KEY\",
            \"appsecret\": \"$KIS_APP_SECRET\"
        }")
    
    # 응답 저장
    echo "$RESPONSE" > "$TOKEN_CACHE"
    
    TOKEN=$(echo "$RESPONSE" | jq -r '.access_token')
    
    if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
        echo -e "${RED}❌ 토큰 발급 실패!${NC}"
        echo "$RESPONSE" | jq '.'
        exit 1
    fi
    
    echo -e "${GREEN}✅ 새 토큰 발급 성공!${NC}"
    echo "   토큰: ${TOKEN:0:50}..."
}

# 토큰 획득
if ! get_cached_token; then
    issue_new_token
fi

echo ""

# 기간별 시세 API 테스트
echo "📊 기간별 시세 API 테스트"
echo "======================================"

# 테스트 1: 최근 100일
echo ""
echo "테스트 1: 삼성전자 (005930) - 최근 100일"
START_DATE=$(date -v-100d +%Y%m%d 2>/dev/null || date -d "100 days ago" +%Y%m%d)
END_DATE=$(date +%Y%m%d)

echo "기간: $START_DATE ~ $END_DATE"
echo ""

RESPONSE=$(curl -s "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930&FID_INPUT_DATE_1=$START_DATE&FID_INPUT_DATE_2=$END_DATE&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0" \
    -H "authorization: Bearer $TOKEN" \
    -H "appkey: $KIS_APP_KEY" \
    -H "appsecret: $KIS_APP_SECRET" \
    -H "tr_id: FHKST03010100")

RT_CD=$(echo "$RESPONSE" | jq -r '.rt_cd')
MSG=$(echo "$RESPONSE" | jq -r '.msg1')

if [ "$RT_CD" = "0" ]; then
    # output2 확인
    COUNT=$(echo "$RESPONSE" | jq '.output2 | length' 2>/dev/null || echo "0")
    
    if [ "$COUNT" = "null" ] || [ "$COUNT" = "0" ]; then
        # output 확인
        COUNT=$(echo "$RESPONSE" | jq '.output | length' 2>/dev/null || echo "0")
        FIELD="output"
    else
        FIELD="output2"
    fi
    
    echo -e "${GREEN}✅ 성공: $MSG${NC}"
    echo "   데이터 개수: $COUNT"
    
    if [ "$COUNT" != "0" ] && [ "$COUNT" != "null" ]; then
        echo ""
        echo "최근 5일 데이터:"
        echo "$RESPONSE" | jq -r ".${FIELD}[0:5][] | \"   \(.stck_bsop_date): \(.stck_clpr)원\""
    else
        echo -e "${YELLOW}⚠️ 데이터가 비어있습니다!${NC}"
        echo "전체 응답:"
        echo "$RESPONSE" | jq '.'
    fi
else
    echo -e "${RED}❌ 실패: $MSG (코드: $RT_CD)${NC}"
    echo "$RESPONSE" | jq '.'
fi

echo ""
echo "======================================"
echo "테스트 완료!"
echo ""
echo "토큰 캐시 위치: $TOKEN_CACHE"
echo "토큰 재사용 가능: 23시간"
