tags: [auth, sessions, flyway, postgres]
# Sessions use Spring Session JDBC, with its schema owned by Flyway
Decision: server-side sessions live in Postgres via Spring Session JDBC. `V2__spring_session.sql` is the verbatim framework schema and `spring.session.jdbc.initialize-schema` is `never`, so Flyway stays the only schema owner. The security context holds only an `AccountPrincipal` with the user id.
Why: less hand-written security code than a custom sessions table and cookie filter; sign-out is `invalidate()`, which deletes the row.
Cost: its tables use BIGINT epoch-millis instead of `timestamptz` (the accounts tables use `timestamptz`). Framework upgrades may change the schema, so diff it then.
