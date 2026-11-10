# MySQL init order

1. **01-schema-and-users.sql** (this directory, Docker entrypoint): creates `notification_lab`, users `lab_consumer` and `lab_producer`, and grants `lab_consumer` full access on the schema. `lab_producer` receives only `USAGE` until tables exist.

2. **Flyway V1** (consumer-service): creates tables.

3. **Flyway V2** in `db/migration-docker/` (runs on consumer startup against Docker MySQL): tightens `lab_producer` grants per `implementation.md` section 8.1. Testcontainers use V1 only.
