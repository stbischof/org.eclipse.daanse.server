# Daanse Pivot Server — DuckDB

Production Daanse OLAP (XMLA) server image for **DuckDB**. The image ships the
Daanse ROLAP engine, the XMLA endpoint, the DuckDB JDBC driver, the DataSource
and the matching SQL dialect. It is configured entirely through environment
variables. The DuckDB database is a file mounted into the container and is
opened read-only by default (DuckDB `access_mode=READ_ONLY`; an in-memory DuckDB would be private to each
connection and is therefore not useful here).

Note: this image is based on Debian (`eclipse-temurin:25-jre`) instead of
Alpine because the DuckDB native library requires glibc.

Images: `docker.io/eclipsedaanse/daanse-pivot-duckdb:snapshot` and
`ghcr.io/eclipse-daanse/daanse-pivot-duckdb:snapshot` (linux/amd64, linux/arm64).

## Quick start

```bash
docker run --name daanse-pivot \
  -v ./catalog:/app/catalog:ro \
  -v ./database.duckdb:/app/data/database.duckdb:ro \
  -p 8080:8080 \
  eclipsedaanse/daanse-pivot-duckdb:snapshot
```

The XMLA endpoint is then available at `http://localhost:8080/xmla`.

## DuckDB environment variables

There are no required variables — mount the database file at the default
location and the server starts.

| Variable | Default | Description |
|---|---|---|
| `DAANSE_JDBC_DATABASE_NAME` | `/app/data/database.duckdb` | Path of the DuckDB database file inside the container; must not contain `;` |
| `DAANSE_JDBC_READ_ONLY` | `true` | Open the database read-only (allows other processes to read the same file); the connection pool inherits this mode. Shorthand for the DuckDB setting `access_mode=READ_ONLY` |
| `DAANSE_JDBC_ACCESS_MODE` | *(unset)* | DuckDB `access_mode`: `AUTOMATIC`, `READ_ONLY` or `READ_WRITE`; replaces the read-only default |
| `DAANSE_JDBC_STREAM_RESULTS` | `false` | Stream result sets instead of materializing them (`jdbc_stream_results`) |
| `DAANSE_JDBC_AUTO_COMMIT` | `true` | Default auto-commit mode of the connections (`jdbc_auto_commit`) |
| `DAANSE_JDBC_CUSTOM_USER_AGENT` | *(unset)* | String appended to the user agent reported to DuckDB (`custom_user_agent`) |
| `DAANSE_JDBC_INSTANCE_CACHE` | `true` | Reuse the process-wide database instance for the same file (`jdbc_instance_cache`) |
| `DAANSE_JDBC_PIN_DB` | `false` | Keep the database instance alive after its last connection closes (`jdbc_pin_db`); the server holds the database open anyway |
| `DAANSE_JDBC_IGNORE_UNSUPPORTED_OPTIONS` | `false` | Drop settings DuckDB does not know instead of refusing to start (`jdbc_ignore_unsupported_options`) |
| `DAANSE_JDBC_JFR_MEMORY_MONITOR` | *(unset)* | Name under which the database is tracked in the `duckdb.MemoryUsage` JFR event (`jdbc_jfr_memory_monitor`) |
| `DAANSE_JDBC_SESSION_INIT_SQL_FILE` | *(unset)* | SQL file inside the container that is run before connections are used, see below |
| `DAANSE_JDBC_SESSION_INIT_SQL_FILE_SHA256` | *(unset)* | Expected SHA-256 digest (hex) of that file; the server refuses to connect on a mismatch |
| `DAANSE_JDBC_SETTINGS` | *(unset)* | Comma separated DuckDB settings, each as `name=value`, e.g. `threads=4,memory_limit=4GB` |

### Access mode

The database is opened with DuckDB's `access_mode=READ_ONLY` unless told
otherwise. The mode can be set either way:

| Configuration | Result |
|---|---|
| *(nothing)* | read-only |
| `DAANSE_JDBC_READ_ONLY=true` or `DAANSE_JDBC_ACCESS_MODE=READ_ONLY` | read-only |
| `DAANSE_JDBC_READ_ONLY=false` or `DAANSE_JDBC_ACCESS_MODE=READ_WRITE` | read-write (mount the file without `:ro`; DuckDB then locks it exclusively) |
| `DAANSE_JDBC_ACCESS_MODE=AUTOMATIC` | DuckDB decides (read-write for a file) |

`DAANSE_JDBC_ACCESS_MODE` replaces the read-only default, and the connection
pool follows the resulting mode. An `access_mode=...` entry in
`DAANSE_JDBC_SETTINGS` works the same way; `DAANSE_JDBC_ACCESS_MODE` wins over it. Do not set
`DAANSE_JDBC_READ_ONLY=true` together with a different `access_mode` — DuckDB
refuses to open the database.

### Session init SQL file

`DAANSE_JDBC_SESSION_INIT_SQL_FILE` names a SQL file (at most 1 MB, path without
`;` and `=`) mounted into the container. The part above the marker comment runs
once when the database is opened, the part below it for every connection:

```sql
CREATE OR REPLACE VIEW "Fact" AS SELECT * FROM "FactData";

/* DUCKDB_CONNECTION_INIT_BELOW_MARKER */

SET search_path = 'analytics';
```

The file runs with the full privileges of the connection — statements that
write need a read-write access mode. Pin its content with
`DAANSE_JDBC_SESSION_INIT_SQL_FILE_SHA256`.

`DAANSE_JDBC_SETTINGS` configures the database, not a single connection;
settings of session scope (`search_path`, `schema`, profiling) belong below the
marker of the session init SQL file instead.

## Source

`https://github.com/eclipse-daanse/org.eclipse.daanse.server/tree/main/application/pivot/duckdb`
