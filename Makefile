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
	./scripts/test-check-agnostic.sh

db-up:
	docker compose up -d --wait

# Local settings and secrets come from server/.env.local; the profile falls back to dev
# (console email, non-Secure cookie) when neither that file nor the shell sets one.
server-dev:
	cd server && set -a && if [ -f .env.local ]; then . ./.env.local; fi && set +a && \
		SPRING_PROFILES_ACTIVE=$${SPRING_PROFILES_ACTIVE:-dev} ./mvnw spring-boot:run

web-dev: web-install
	cd web && npm run dev

# npm ci only when the lockfile is newer than the last install. npm writes the marker itself;
# the touch covers an install that leaves it untouched.
web/node_modules/.package-lock.json: web/package-lock.json
	cd web && npm ci && touch node_modules/.package-lock.json

web-install: web/node_modules/.package-lock.json
