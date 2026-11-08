tags: [build, maven, rx]
The rx stop hook runs make test while a dev agent is still building; two Maven runs on server/target gave "zip END header not found" in spring-boot repackage.
Do not start a second build while an agent builds; if it appears, clear server/target and rerun once.
spotless:apply also reformats files outside the task diff (3 here); run spotless:check early so format-only churn lands in its own commit.
