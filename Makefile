.PHONY: help build up down logs clean test

help: ## 도움말 표시
	@echo "Stock Hunter - 사용 가능한 명령어:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

build: ## Docker 이미지 빌드
	docker-compose build

up: ## 서비스 시작
	docker-compose up -d
	@echo "✅ Stock Hunter 서비스가 시작되었습니다!"
	@echo "   - Kotlin Screener: http://localhost:8080"
	@echo "   - FastAPI Gateway: http://localhost:3000"

down: ## 서비스 중지
	docker-compose down

restart: down up ## 서비스 재시작

logs: ## 로그 확인
	docker-compose logs -f

logs-kotlin: ## Kotlin 서비스 로그
	docker-compose logs -f kotlin-screener

logs-fastapi: ## FastAPI 서비스 로그
	docker-compose logs -f fastapi-gateway

ps: ## 실행 중인 컨테이너 확인
	docker-compose ps

clean: ## 컨테이너, 이미지, 볼륨 삭제
	docker-compose down -v --rmi all
	@echo "🧹 정리 완료"

test-kotlin: ## Kotlin 서비스 테스트
	cd kotlin-screener && ./gradlew test

dev-kotlin: ## Kotlin 서비스 개발 모드 실행
	cd kotlin-screener && ./gradlew run

dev-fastapi: ## FastAPI 서비스 개발 모드 실행
	cd fastapi-gateway && python main.py

health: ## 헬스 체크
	@echo "Checking Kotlin Screener..."
	@curl -s http://localhost:8080/health | jq '.'
	@echo "\nChecking FastAPI Gateway..."
	@curl -s http://localhost:3000/health | jq '.'

install: ## 개발 환경 설정
	@echo "Kotlin 의존성 설치..."
	cd kotlin-screener && ./gradlew dependencies
	@echo "Python 의존성 설치..."
	cd fastapi-gateway && pip install -r requirements.txt
	@echo "✅ 설치 완료"
