# Room Index Audit

Recorded: 2026-10-31
Schema: `app/schemas/com.tamerslab.database.AppDatabase/55.json`
Database: `AppDatabase` version 55
Scope: Phase 1.5 static audit

## Findings

- 61 source files contain `@Entity`; 37 of those files declare Room `indices`.
- Schema v55 contains 106 entities.
- 61 tables have one or more declared indices.
- 45 tables have no declared index.
- 21 no-index tables declare foreign keys, but every foreign-key child column is
  already part of that table's primary key. No missing foreign-key index was
  found in the static audit.
- No duplicate index definitions were found in schema v55.

## Decision

No Room index or migration change is justified from static evidence alone.

- `AppDatabase` stays at version 55.
- No schema JSON, entity, DAO, or migration change is included in Phase 1.
- The runtime query-plan audit remains required before changing indices:
  capture `EXPLAIN QUERY PLAN` for the top 20 queries on a representative
  database during Phase 5.0, then add only indices that show a measured `SCAN`
  or write-path benefit.

## Follow-up

- Phase 5.0 records the query-plan baseline and query frequency.
- Re-run this audit if a new entity, DAO query, or foreign key is added.
- Any later index change needs an `AppDatabase` version bump, a
  `MigrationTestHelper` test, and a write-path regression measurement.
