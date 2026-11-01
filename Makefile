.PHONY: dev test lint db-up server-dev web-dev web-install

dev: db-up
	$(MAKE) -j2 server-dev web-dev

test: web-install
	cd server && ./mvnw -q verify
	cd web && npm test

lint: web-install
	cd server && ./mvnw -q spotless:check
	cd web && npm run lint
	./scripts/check-agnostic.sh

db-up:
	docker compose up -d --wait

server-dev:
	cd server && ./mvnw spring-boot:run

web-dev: web-install
	cd web && npm run dev

web-install:
	cd web && npm ci
