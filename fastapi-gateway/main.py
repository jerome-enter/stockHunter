"""
Stock Hunter FastAPI Gateway

클라이언트와 Kotlin 스크리닝 엔진 사이의 게이트웨이 역할
"""
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
import httpx
import logging
import os
from datetime import datetime
from pathlib import Path

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# FastAPI 앱 생성
app = FastAPI(
    title="Stock Hunter API Gateway",
    description="주식 스크리닝 서비스 API Gateway",
    version="1.0.0"
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 프로덕션에서는 특정 도메인만 허용
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Kotlin 서버 URL (Docker 환경에서는 서비스명 사용)
KOTLIN_SERVICE_URL = os.getenv("KOTLIN_SERVICE_URL", "http://kotlin-screener:8080")

# ==================== Models ====================

class ScreeningRequest(BaseModel):
    """스크리닝 요청 모델"""
    appKey: str = Field(..., description="한국투자증권 APP KEY")
    appSecret: str = Field(..., description="한국투자증권 APP SECRET")
    isProduction: bool = Field(False, description="실전투자 여부 (True: 실전, False: 모의)")
    
    # 이동평균선 조건
    ma60Enabled: bool = False
    ma60Min: int = Field(95, ge=0, le=200)
    ma60Max: int = Field(105, ge=0, le=200)
    
    ma112Enabled: bool = True
    ma112Min: int = Field(95, ge=0, le=200)
    ma112Max: int = Field(105, ge=0, le=200)
    
    ma224Enabled: bool = False
    ma224Min: int = Field(95, ge=0, le=200)
    ma224Max: int = Field(105, ge=0, le=200)
    
    # 볼린저 밴드
    bbEnabled: bool = False
    bbPeriod: int = Field(20, ge=5, le=100)
    bbMultiplier: float = Field(2.0, ge=0.5, le=5.0)
    bbPosition: str = Field("all", pattern="^(all|upper|middle|lower)$")
    bbUpperBreak: bool = False
    bbLowerBreak: bool = False
    
    # 거래량
    volumeEnabled: bool = False
    volumeMultiple: float = Field(1.5, ge=0.1, le=10.0)
    
    # 가격 변동
    priceChangeEnabled: bool = False
    priceChangeMin: float = Field(-100.0, ge=-100.0, le=100.0)
    priceChangeMax: float = Field(100.0, ge=-100.0, le=100.0)
    
    # 제외 조건
    excludeETF: bool = True
    excludeETN: bool = True
    excludeManagement: bool = False
    
    # 시가총액
    marketCapEnabled: bool = False
    marketCapMin: int = Field(0, ge=0)
    marketCapMax: int = Field(1000000000000, ge=0)
    
    # 재무 비율
    perEnabled: bool = False
    perMin: float = Field(0.0, ge=0)
    perMax: float = Field(30.0, ge=0)
    
    # 이평선 정배열
    maAlignment: bool = False
    
    # 타겟 종목 코드 (비어있으면 전체)
    targetCodes: List[str] = []


class CredentialsRequest(BaseModel):
    """API 키 검증 요청"""
    appKey: str
    appSecret: str


# ==================== Routes ====================

@app.get("/", response_class=HTMLResponse)
async def root():
    """웹 UI 제공"""
    # HTML 파일 경로 (로컬 개발: 부모 디렉토리, Docker: 같은 디렉토리)
    html_paths = [
        Path(__file__).parent / "stock_screener.html",  # Docker
        Path(__file__).parent.parent / "stock_screener.html",  # 로컬 개발
    ]
    
    for html_path in html_paths:
        if html_path.exists():
            with open(html_path, "r", encoding="utf-8") as f:
                html_content = f.read()
            
            logger.info(f"Serving HTML from: {html_path}")
            return HTMLResponse(content=html_content)
    
    return HTMLResponse(
        content="""
        <html>
            <head><title>Stock Hunter</title></head>
            <body style="font-family: sans-serif; padding: 50px; text-align: center;">
                <h1>🚨 Stock Hunter API</h1>
                <p>HTML 파일을 찾을 수 없습니다.</p>
                <p>stock_screener.html 파일이 올바른 위치에 있는지 확인하세요.</p>
                <hr>
                <p><a href="/health">Health Check</a> | <a href="/docs">API Docs</a></p>
            </body>
        </html>
        """
    )


@app.get("/api")
async def api_info():
    """API 정보"""
    return {
        "service": "Stock Hunter API Gateway",
        "version": "1.0.0",
        "status": "running",
        "timestamp": datetime.now().isoformat()
    }


@app.get("/health")
async def health_check():
    """헬스 체크"""
    try:
        # Kotlin 서버 상태 확인
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{KOTLIN_SERVICE_URL}/health")
            kotlin_healthy = response.status_code == 200
    except Exception as e:
        logger.warning(f"Kotlin service health check failed: {e}")
        kotlin_healthy = False
    
    return {
        "status": "healthy" if kotlin_healthy else "degraded",
        "services": {
            "gateway": "healthy",
            "kotlin_screener": "healthy" if kotlin_healthy else "unhealthy"
        },
        "timestamp": datetime.now().isoformat()
    }


@app.post("/api/v1/screen")
async def screen_stocks(request: ScreeningRequest):
    """
    주식 스크리닝 실행
    
    Kotlin 스크리닝 엔진으로 요청을 전달하고 결과를 반환합니다.
    """
    try:
        logger.info(f"Received screening request - MA112: {request.ma112Enabled}, BB: {request.bbEnabled}")
        
        # Kotlin 서버로 요청 전달
        async with httpx.AsyncClient(timeout=300.0) as client:
            response = await client.post(
                f"{KOTLIN_SERVICE_URL}/api/v1/screen",
                json=request.model_dump(),
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code != 200:
                error_detail = response.json() if response.text else {"error": "Unknown error"}
                logger.error(f"Kotlin service error: {error_detail}")
                raise HTTPException(
                    status_code=response.status_code,
                    detail=error_detail
                )
            
            result = response.json()
            logger.info(f"Screening completed: {result.get('matchedCount', 0)} matches")
            
            return result
            
    except httpx.TimeoutException:
        logger.error("Request to Kotlin service timed out")
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail="스크리닝 요청 시간 초과. 잠시 후 다시 시도해주세요."
        )
    except httpx.ConnectError:
        logger.error("Cannot connect to Kotlin service")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="스크리닝 서비스에 연결할 수 없습니다."
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error during screening: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"스크리닝 중 오류 발생: {str(e)}"
        )


@app.post("/api/v1/validate-credentials")
async def validate_credentials(request: CredentialsRequest):
    """
    한국투자증권 API 키 검증
    """
    try:
        logger.info("Validating API credentials")
        
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(
                f"{KOTLIN_SERVICE_URL}/api/v1/validate-credentials",
                json={
                    "appKey": request.appKey,
                    "appSecret": request.appSecret
                }
            )
            
            result = response.json()
            
            if response.status_code == 200:
                logger.info("Credentials validated successfully")
                return {"valid": True, "message": "인증 성공"}
            else:
                logger.warning("Invalid credentials")
                return {"valid": False, "message": result.get("message", "인증 실패")}
                
    except Exception as e:
        logger.error(f"Credential validation error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"인증 검증 중 오류 발생: {str(e)}"
        )


@app.get("/api/v1/stock-codes")
async def get_stock_codes():
    """
    지원하는 종목 코드 목록 조회
    """
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{KOTLIN_SERVICE_URL}/api/v1/stock-codes")
            
            if response.status_code != 200:
                raise HTTPException(
                    status_code=response.status_code,
                    detail="종목 코드 조회 실패"
                )
            
            return response.json()
            
    except Exception as e:
        logger.error(f"Error fetching stock codes: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"종목 코드 조회 중 오류 발생: {str(e)}"
        )


# ==================== Error Handlers ====================

@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException):
    """HTTP 예외 처리"""
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": exc.detail,
            "timestamp": datetime.now().isoformat()
        }
    )


@app.exception_handler(Exception)
async def general_exception_handler(request, exc: Exception):
    """일반 예외 처리"""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": "Internal server error",
            "detail": str(exc),
            "timestamp": datetime.now().isoformat()
        }
    )


if __name__ == "__main__":
    import uvicorn
    
    logger.info("🚀 Starting FastAPI Gateway Server...")
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=3000,
        reload=True,
        log_level="info"
    )
