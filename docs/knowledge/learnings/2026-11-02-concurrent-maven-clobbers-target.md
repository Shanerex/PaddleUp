tags: [build, maven, testing, rx]
Two `./mvnw verify` runs on the same server/ dir clobber target/: the rx stop hook ran `make test` while rx:dev was still building and failed with "zip END header not found" in spring-boot:repackage.
A failure like that during a background build is a race, not a code bug; re-run once the other build exits before debugging.
A second Spring context in an IT (PacksStartupIT) exhausted the shared Postgres connections; cap it with spring.datasource.hikari.maximum-pool-size=2.
