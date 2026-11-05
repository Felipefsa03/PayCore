.PHONY: up down test run

up:
	docker compose -f infrastructure/docker/docker-compose.yml up -d

down:
	docker compose -f infrastructure/docker/docker-compose.yml down

test:
	cd services/financial-core && ./mvnw -B verify

run: up
	cd services/financial-core && ./mvnw -pl app -am spring-boot:run
