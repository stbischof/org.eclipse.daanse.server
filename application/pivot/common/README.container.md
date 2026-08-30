## The pivot container

The Daanse pivot server ships the Daanse ROLAP engine and the XMLA endpoint
together with exactly one JDBC DataSource implementation, its driver and the
matching SQL dialect. It is configured entirely through environment variables;
unset variables keep the defaults of the underlying Daanse bundles.

The HTTP port inside the container is `8080`. The XMLA endpoint is available
at the path configured by `DAANSE_XMLA_PATH` (default `/xmla`).

If a required variable is missing, the configurator logs an error and the
server starts **without** an OLAP context: the endpoint answers, but knows no
catalogs and returns empty results. Check the container log first when a
query comes back empty.

## Try it — complete demos

The repository ships self contained demos under
[`application/pivot/common/example`](https://github.com/eclipse-daanse/org.eclipse.daanse.server/tree/main/application/pivot/common/example) —
each is one file embedding a seeded database and a minimal OLAP catalog, and
answers an MDX query right after start:

| Example | Run with |
|---|---|
| [`compose/docker-compose.yml`](https://github.com/eclipse-daanse/org.eclipse.daanse.server/blob/main/application/pivot/common/example/compose/docker-compose.yml) | `docker compose up` |
| [`kube/pivot-pod.yaml`](https://github.com/eclipse-daanse/org.eclipse.daanse.server/blob/main/application/pivot/common/example/kube/pivot-pod.yaml) | `podman kube play pivot-pod.yaml` or `kubectl apply -f pivot-pod.yaml` |
| [`ldap/docker-compose.yml`](https://github.com/eclipse-daanse/org.eclipse.daanse.server/blob/main/application/pivot/common/example/ldap/docker-compose.yml) | `docker compose up` — adds LDAP authentication (see below) |

The Kubernetes pod, abbreviated — the same YAML runs unchanged with rootless
Podman and on a cluster (there, replace the `hostPort` with a Service):

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: daanse-pivot-demo
data:
  init.sql: |
    CREATE TABLE "Fact" ("KEY" VARCHAR(100), "VALUE" INTEGER);
    INSERT INTO "Fact" VALUES ('A', 1), ('B', 2), ('C', 3);
  catalog.xmi: |
    ...your OLAP catalog mapping...
---
apiVersion: v1
kind: Pod
metadata:
  name: daanse-pivot-demo
spec:
  containers:
    - name: db
      image: docker.io/library/postgres:17-alpine
      env:
        - { name: POSTGRES_DB, value: demo }
        - { name: POSTGRES_USER, value: daanse }
        - { name: POSTGRES_PASSWORD, value: secret }
      volumeMounts:
        - { name: demo, mountPath: /docker-entrypoint-initdb.d/init.sql, subPath: init.sql }
    - name: pivot
      image: docker.io/eclipsedaanse/daanse-pivot-postgres:snapshot
      ports:
        - { containerPort: 8080, hostPort: 8080 }
      env:
        # containers in a pod share the network namespace
        - { name: DAANSE_JDBC_HOST, value: localhost }
        - { name: DAANSE_JDBC_DBNAME, value: demo }
        - { name: DAANSE_JDBC_USER, value: daanse }
        - { name: DAANSE_JDBC_PASSWORD, value: secret }
      volumeMounts:
        - { name: demo, mountPath: /app/catalog/catalog.xmi, subPath: catalog.xmi }
  volumes:
    - name: demo
      configMap:
        name: daanse-pivot-demo
```

## Catalog

The OLAP catalog (Daanse ROLAP mapping, `.xmi`) is mounted at `/app/catalog`.
By default the file `/app/catalog/catalog.xmi` is loaded. If the mapping is
split over several cross-referencing files, point
`DAANSE_CATALOG_ADDITIONAL_GLOBS` at them, e.g.
`DAANSE_CATALOG_ADDITIONAL_GLOBS=/app/catalog/**/*.xmi`.

## Common environment variables

| Variable | Default | Description |
|---|---|---|
| `DAANSE_CATALOG_RESOURCE` | `/app/catalog/catalog.xmi` | Primary catalog mapping file (EMF XMI with a Catalog root element) |
| `DAANSE_CATALOG_ADDITIONAL_GLOBS` | *(unset)* | Comma separated glob patterns for additional mapping resources |
| `DAANSE_USE_AGGREGATES` | `false` | Use aggregate tables |
| `DAANSE_XMLA_PATH` | `/xmla` | Servlet pattern of the XMLA endpoint |
| `DAANSE_CORS_ENABLED` | `true` | Register the CORS filter |
| `DAANSE_CORS_ALLOWED_ORIGINS` | `*` | Comma separated allowed origins |
| `DAANSE_CORS_ALLOWED_HEADERS` | `*` | Comma separated allowed headers |
| `DAANSE_CORS_ALLOW_CREDENTIALS` | `true` | Allow credentials in CORS requests |

## Authentication

By default the endpoint is **anonymous**: every request is served, carrying no
roles — so only catalogs without access control answer. Real authentication in
front of the container (reverse proxy) is always an option; the image itself
offers HTTP Basic authentication against an LDAP directory.

Setting `DAANSE_LDAP_URL` switches it on: credentials are verified by an LDAP
bind, and the caller's roles come from LDAP groups when
`DAANSE_LDAP_GROUP_SEARCH_BASE` is set (roles are intersected with the roles
the catalog declares — name the groups after the catalog's roles). Anonymous
requests are still served until `DAANSE_AUTH_ANONYMOUS=false`, which makes the
endpoint challenge every request.

| Variable | Default | Description |
|---|---|---|
| `DAANSE_AUTH_ANONYMOUS` | `true` | Serve requests without credentials (with no roles) |
| `DAANSE_AUTH_REALM` | `Daanse XMLA` | Realm of the HTTP Basic challenge |
| `DAANSE_LDAP_URL` | *(unset)* | LDAP server, e.g. `ldaps://ldap.example.org:636`; setting it enables authentication |
| `DAANSE_LDAP_USER_DN_PATTERN` | *(unset)* | Direct bind DN pattern, e.g. `uid={0},ou=people,dc=example,dc=org` |
| `DAANSE_LDAP_USER_SEARCH_BASE` | *(unset)* | Search base for users (alternative to the DN pattern) |
| `DAANSE_LDAP_USER_SEARCH_FILTER` | `(uid={0})` | Search filter for users |
| `DAANSE_LDAP_SERVICE_BIND_DN` | *(unset)* | Service account for searches |
| `DAANSE_LDAP_SERVICE_BIND_PASSWORD` | *(unset)* | Password of the service account |
| `DAANSE_LDAP_TRANSPORT_SECURITY` | `LDAPS` | `LDAPS`, `STARTTLS` or `NONE` |
| `DAANSE_LDAP_ALLOW_UNENCRYPTED` | `false` | Must be `true` for `NONE` — passwords then cross the wire in the clear |
| `DAANSE_LDAP_CONNECT_TIMEOUT_MILLIS` | `5000` | Connect timeout |
| `DAANSE_LDAP_READ_TIMEOUT_MILLIS` | `10000` | Read timeout |
| `DAANSE_LDAP_REFERRAL` | `follow` | Referral handling |
| `DAANSE_LDAP_GROUP_SEARCH_BASE` | *(unset)* | Search base for the caller's groups; setting it enables role resolution |
| `DAANSE_LDAP_GROUP_SEARCH_FILTER` | `(member={0})` | Search filter for groups |
| `DAANSE_LDAP_GROUP_NAME_ATTRIBUTE` | `cn` | Attribute holding the group (= role) name |
| `DAANSE_LDAP_MEMBER_OF_ATTRIBUTE` | *(unset)* | Read groups from this user attribute instead of searching |

### No LDAP at hand? Use a side container

The repository ships a complete example under
[`application/pivot/common/example/ldap`](https://github.com/eclipse-daanse/org.eclipse.daanse.server/tree/main/application/pivot/common/example/ldap):
one self contained `docker-compose.yml` that runs an [LLDAP](https://github.com/lldap/lldap)
side container next to the pivot — a lightweight LDAP server whose image comes
from the LLDAP project itself. The users (`admin`/`admin123`,
`analyst`/`analyst123`) and the group `Administrator` are plain text JSON
configs embedded in the compose file and loaded by LLDAP's official bootstrap
script; no extra files needed. LLDAP also serves a web UI on port 17170 to
manage users and groups later.

```yaml
configs:
  groups.json:
    content: |
      { "name": "Administrator" }
  admin.json:
    content: |
      { "id": "admin", "email": "admin@example.org", "groups": ["lldap_admin", "Administrator"] }
  analyst.json:
    content: |
      { "id": "analyst", "email": "analyst@example.org", "password": "analyst123" }

services:
  lldap:
    image: lldap/lldap:stable
    environment:
      LLDAP_LDAP_BASE_DN: dc=example,dc=org
      LLDAP_LDAP_USER_PASS: admin123
      LLDAP_KEY_SEED: change-me-for-anything-real
      LLDAP_JWT_SECRET: change-me-too-at-least-32-characters-long
    ports: ["17170:17170"]   # web UI, optional

  lldap-bootstrap:           # one-shot job loading the users and groups above
    image: lldap/lldap:stable
    depends_on: [lldap]
    entrypoint: ["/bin/sh", "-c", "sleep 2 && /app/bootstrap.sh"]
    restart: on-failure
    environment:
      LLDAP_URL: http://lldap:17170
      LLDAP_ADMIN_USERNAME: admin
      LLDAP_ADMIN_PASSWORD: admin123
      DO_CLEANUP: "false"
    configs:
      - { source: groups.json, target: /bootstrap/group-configs/groups.json }
      - { source: admin.json, target: /bootstrap/user-configs/admin.json }
      - { source: analyst.json, target: /bootstrap/user-configs/analyst.json }

  pivot:
    image: eclipsedaanse/daanse-pivot-postgres:snapshot   # any daanse-pivot-<db>
    depends_on: [lldap]
    ports: ["8080:8080"]
    volumes: ["./catalog:/app/catalog:ro"]
    environment:
      # ... DAANSE_JDBC_* of your database ...
      DAANSE_LDAP_URL: ldap://lldap:3890
      DAANSE_LDAP_TRANSPORT_SECURITY: NONE
      DAANSE_LDAP_ALLOW_UNENCRYPTED: "true"
      DAANSE_LDAP_USER_DN_PATTERN: uid={0},ou=people,dc=example,dc=org
      DAANSE_LDAP_SERVICE_BIND_DN: uid=admin,ou=people,dc=example,dc=org
      DAANSE_LDAP_SERVICE_BIND_PASSWORD: admin123
      DAANSE_LDAP_GROUP_SEARCH_BASE: ou=groups,dc=example,dc=org
```

Then query with `curl -u admin:admin123 ...` — or edit the inline JSON to your
own users and groups; the group names are the roles.

## Connection pool

The engine never talks to the DataSource directly. A single MDX query fans out
to as many statements as the segment cache has threads, so all of them go
through a pool. The defaults are sized for that fan-out; lower
`DAANSE_POOL_MAXIMUM_POOL_SIZE` if the database has a stricter connection limit
than the server does — Oracle refuses rather than queues once `processes` is
reached.

| Variable | Default | Description |
|---|---|---|
| `DAANSE_POOL_POOL_NAME` | *(unset)* | Pool name; shows up in thread names and metrics |
| `DAANSE_POOL_MAXIMUM_POOL_SIZE` | `100` | Upper bound on physical connections |
| `DAANSE_POOL_MINIMUM_IDLE` | `5` | Connections kept open while idle |
| `DAANSE_POOL_CONNECTION_TIMEOUT` | `30000` | How long a caller waits for a free connection (ms) |
| `DAANSE_POOL_IDLE_TIMEOUT` | `600000` | Idle connections are evicted after this (ms) |
| `DAANSE_POOL_MAX_LIFETIME` | `1800000` | A connection is retired this long after it was opened (ms) |
| `DAANSE_POOL_LEAK_THRESHOLD` | `300000` | A connection held longer than this is reported (ms, 0 disables) |
| `DAANSE_POOL_READ_ONLY` | *(matches the DataSource)* | Mode the pool hands connections out in. The image sets it from its own DataSource — DuckDB opens read-only and rejects a change on the connection. Override only if you know the driver allows it. |

## Mount points

| Path | Purpose |
|---|---|
| `/app/catalog` | OLAP catalog mapping (`.xmi`); declared as a volume |
| `/app/data` | Database file for file based databases (DuckDB, H2); unused otherwise |
