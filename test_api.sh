#!/bin/bash

# 한투 API 테스트 스크립트
# 기간별 시세 API가 얼마나 반환하는지 확인

echo "=== 한국투자증권 API 테스트 ==="
echo ""
echo "테스트 1: 기본 일별 시세 API (inquire-daily-price)"
echo "→ 예상: 30개만 반환"
echo ""
echo "테스트 2: 기간별 시세 API (inquire-daily-itemchartprice)"
echo "→ 예상: 날짜 범위만큼 반환 (최대 100개?)"
echo ""
echo "Docker 로그에서 확인:"
echo "docker logs -f stock-hunter-kotlin | grep 'API returned'"
