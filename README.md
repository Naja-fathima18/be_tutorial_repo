# be_tutorial_repo

Backend interview prep assignment: five Spring Boot features, each delivered as its own pull request.

Stack: Java 21, Spring Boot 3.5, Maven (wrapper), H2 in-memory database, Flyway, JUnit 5 + AssertJ + Mockito.

## Run

```bash
export JWT_SECRET=$(openssl rand -base64 48)
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD=change-me-please
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. `JWT_SECRET` (at least 32 bytes) is required; the app fails fast without it. `ADMIN_EMAIL` / `ADMIN_PASSWORD` are optional and create an admin user at startup. Tests use a test-only secret and need no environment variables.

## Test

```bash
./mvnw test
./mvnw verify
```

## Questions

| # | Question | PR link |
|---|---|---|
| 1 | Task Manager API | [#1](https://github.com/Naja-fathima18/be_tutorial_repo/pull/1) |
| 2 | URL Shortener | [#2](https://github.com/Naja-fathima18/be_tutorial_repo/pull/2) |
| 3 | Authentication & Roles | [#4](https://github.com/Naja-fathima18/be_tutorial_repo/pull/4) |
| 4 | Product Catalog | [#3](https://github.com/Naja-fathima18/be_tutorial_repo/pull/3) |
| 5 | Order Service | |

Video:
