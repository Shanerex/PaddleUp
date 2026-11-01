tags: [dev-env, makefile, docker]
# make dev runs Postgres in compose, server and web on the host
Decision: `make dev` starts Postgres via `docker compose up -d --wait`, then runs `./mvnw spring-boot:run` and `npm run dev` on the host with `make -j2`.
Why: fast reload and no Dockerfiles. All-in-compose gives slow rebuilds and is closer to deployment, which is out of scope. A root package.json with concurrently adds a third Node project.
Cost: the host needs JDK 21 and Node.
