tags: [build, java, spring-boot]
# Toolchain surprises from the scaffold
- start.spring.io no longer offers Boot 3.5; the pom pins 3.5.16 and the wrapper scripts came from a generated project.
- Dev machine has only JDK 17 and 25. The build uses `maven.compiler.release=21`, so JDK 21 itself was never run; run it once.
- A stale `server/target` jar once failed repackage with "Unable to find main class"; it did not reproduce. Run `./mvnw clean` first if it returns.
