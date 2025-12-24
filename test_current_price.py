#!/usr/bin/env python3
"""
한국투자증권 주식현재가 시세 API 테스트
"""
import requests
import json
import os
from datetime import datetime

# API 설정 (환경변수 또는 직접 입력)
APP_KEY = os.getenv('KIS_APP_KEY', '')
APP_SECRET = os.getenv('KIS_APP_SECRET', '')
IS_PRODUCTION = False  # False: 모의투자, True: 실전투자

# 테스트할 종목코드
STOCK_CODE = "005930"  # 삼성전자

def get_access_token():
    """접근 토큰 발급"""
    url = "https://openapi.koreainvestment.com:9443/oauth2/tokenP"
    
    headers = {
        "content-type": "application/json"
    }
    
    data = {
        "grant_type": "client_credentials",
        "appkey": APP_KEY,
        "appsecret": APP_SECRET
    }
    
    print(f"🔑 토큰 발급 요청...")
    response = requests.post(url, headers=headers, json=data)
    
    if response.status_code == 200:
        result = response.json()
        token = result.get('access_token')
        print(f"✅ 토큰 발급 성공: {token[:20]}...")
        return token
    else:
        print(f"❌ 토큰 발급 실패: {response.status_code}")
        print(response.text)
        return None

def get_current_price(access_token, stock_code):
    """주식현재가 시세 조회"""
    url = "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-price"
    
    # TR_ID (실전/모의 동일)
    tr_id = "FHKST01010100"
    
    headers = {
        "content-type": "application/json",
        "authorization": f"Bearer {access_token}",
        "appkey": APP_KEY,
        "appsecret": APP_SECRET,
        "tr_id": tr_id
    }
    
    params = {
        "fid_cond_mrkt_div_code": "J",  # 주식시장구분코드 (J: 주식)
        "fid_input_iscd": stock_code     # 종목코드
    }
    
    print(f"\n📊 현재가 조회 요청...")
    print(f"  - 종목코드: {stock_code}")
    print(f"  - TR_ID: {tr_id}")
    print(f"  - URL: {url}")
    print(f"  - Params: {params}")
    
    response = requests.get(url, headers=headers, params=params)
    
    print(f"\n📥 응답 상태: {response.status_code}")
    
    if response.status_code == 200:
        result = response.json()
        print(f"✅ API 호출 성공!")
        print(f"\n응답 데이터:")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
        # 응답 코드 확인
        rt_cd = result.get('rt_cd', '')
        msg1 = result.get('msg1', '')
        
        if rt_cd == '0':
            print(f"\n✅ 정상 응답!")
            output = result.get('output', {})
            if output:
                print(f"\n주식 정보:")
                print(f"  - 종목명: {output.get('prdt_name', 'N/A')}")
                print(f"  - 현재가: {output.get('stck_prpr', 'N/A')}원")
                print(f"  - 전일대비: {output.get('prdy_vrss', 'N/A')}원")
                print(f"  - 등락률: {output.get('prdy_ctrt', 'N/A')}%")
                print(f"  - 거래량: {output.get('acml_vol', 'N/A')}")
            else:
                print(f"⚠️ output 필드가 비어있음")
        else:
            print(f"\n❌ API 에러!")
            print(f"  - rt_cd: {rt_cd}")
            print(f"  - msg1: {msg1}")
            print(f"  - msg_cd: {result.get('msg_cd', '')}")
    else:
        print(f"❌ API 호출 실패: {response.status_code}")
        print(response.text)

def main():
    print("=" * 60)
    print("한국투자증권 주식현재가 시세 API 테스트")
    print("=" * 60)
    print(f"현재 시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"계정 구분: {'실전투자' if IS_PRODUCTION else '모의투자'}")
    print(f"테스트 종목: {STOCK_CODE}")
    print("=" * 60)
    
    if not APP_KEY or not APP_SECRET:
        print("\n❌ API 키가 설정되지 않았습니다!")
        print("환경변수를 설정하거나 코드에서 직접 입력하세요:")
        print("  export KIS_APP_KEY='your_app_key'")
        print("  export KIS_APP_SECRET='your_app_secret'")
        return
    
    print(f"\n🔐 APP_KEY: {APP_KEY[:10]}...")
    print(f"🔐 APP_SECRET: {APP_SECRET[:10]}...")
    
    # 1. 토큰 발급
    token = get_access_token()
    if not token:
        return
    
    # 2. 현재가 조회
    get_current_price(token, STOCK_CODE)
    
    print("\n" + "=" * 60)
    print("테스트 완료")
    print("=" * 60)

if __name__ == "__main__":
    main()
