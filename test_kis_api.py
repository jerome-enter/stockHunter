#!/usr/bin/env python3
"""한국투자증권 API 테스트"""

import os
import json
import requests
from datetime import datetime, timedelta

# .env.test 파일 로드
env_path = "kotlin-screener/.env.test"
if not os.path.exists(env_path):
    print("❌ .env.test 파일이 없습니다!")
    exit(1)

config = {}
with open(env_path) as f:
    for line in f:
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            config[key] = value

APP_KEY = config.get('KIS_APP_KEY', '')
APP_SECRET = config.get('KIS_APP_SECRET', '')
IS_PRODUCTION = config.get('KIS_IS_PRODUCTION', 'true') == 'true'

if not APP_KEY or APP_KEY == '여기에_앱키_붙여넣기':
    print("❌ KIS_APP_KEY가 설정되지 않았습니다!")
    exit(1)

print("🔍 한국투자증권 API 테스트")
print("=" * 60)
print(f"환경: {'실전투자' if IS_PRODUCTION else '모의투자'}")
print(f"APP_KEY: {APP_KEY[:10]}...")
print()

# 1. 토큰 발급
print("1️⃣ 토큰 발급 테스트...")
token_url = "https://openapi.koreainvestment.com:9443/oauth2/tokenP"
token_data = {
    "grant_type": "client_credentials",
    "appkey": APP_KEY,
    "appsecret": APP_SECRET
}

try:
    resp = requests.post(token_url, json=token_data)
    token_result = resp.json()
    
    if 'access_token' in token_result:
        print("✅ 토큰 발급 성공!")
        access_token = token_result['access_token']
        print(f"   토큰: {access_token[:20]}...")
    else:
        print(f"❌ 토큰 발급 실패: {token_result}")
        exit(1)
except Exception as e:
    print(f"❌ 에러: {e}")
    exit(1)

print()

# 2. 기간별 시세 API 테스트
print("2️⃣ 기간별 시세 API 테스트...")

base_url = "https://openapi.koreainvestment.com:9443"
endpoint = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"

# 날짜 범위 (최근 100일)
end_date = datetime.now()
start_date = end_date - timedelta(days=99)

headers = {
    "authorization": f"Bearer {access_token}",
    "appkey": APP_KEY,
    "appsecret": APP_SECRET,
    "tr_id": "FHKST03010100"
}

params = {
    "FID_COND_MRKT_DIV_CODE": "J",
    "FID_INPUT_ISCD": "005930",  # 삼성전자
    "FID_INPUT_DATE_1": start_date.strftime("%Y%m%d"),
    "FID_INPUT_DATE_2": end_date.strftime("%Y%m%d"),
    "FID_PERIOD_DIV_CODE": "D",
    "FID_ORG_ADJ_PRC": "0"
}

print(f"종목: 005930 (삼성전자)")
print(f"기간: {params['FID_INPUT_DATE_1']} ~ {params['FID_INPUT_DATE_2']}")
print()

try:
    resp = requests.get(base_url + endpoint, headers=headers, params=params)
    result = resp.json()
    
    print(f"응답 코드: {result.get('rt_cd')}")
    print(f"메시지: {result.get('msg1')}")
    print(f"데이터 개수: {len(result.get('output2', []))}")
    
    if result.get('output2'):
        print()
        print("✅ 데이터 수신 성공!")
        print(f"   총 {len(result['output2'])}개 레코드")
        print()
        print("최근 5일 데이터:")
        for i, data in enumerate(result['output2'][:5]):
            print(f"   {i+1}. {data.get('stck_bsop_date')} - 종가: {data.get('stck_clpr')}원")
    else:
        print()
        print("❌ 데이터가 비어있습니다!")
        print(f"   전체 응답: {json.dumps(result, indent=2, ensure_ascii=False)}")
        
except Exception as e:
    print(f"❌ 에러: {e}")
    import traceback
    traceback.print_exc()

print()
print("=" * 60)
print("테스트 완료!")
