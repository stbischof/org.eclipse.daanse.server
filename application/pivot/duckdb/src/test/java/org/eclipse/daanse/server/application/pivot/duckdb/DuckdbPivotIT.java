/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*   Stefan Bischof (bipolis.org) - initial
*/
package org.eclipse.daanse.server.application.pivot.duckdb;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Future;

import org.eclipse.daanse.server.application.pivot.common.test.PivotContainers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * End-to-end test: creates a DuckDB database file with the Fact table of the
 * static test catalog, builds the daanse-pivot-duckdb image from its real
 * Dockerfile, mounts database file and catalog and verifies via XMLA that the
 * MDX sum over the VALUE column is queryable - in the read-only and read-write
 * access modes the image can be configured to.
 */
@Testcontainers(disabledWithoutDocker = true)
class DuckdbPivotIT {

    /** Not writable for the container user: only a read-only open succeeds. */
    private static final int MODE_NOT_WRITABLE = 0444;
    private static final int MODE_WRITABLE = 0666;

    @TempDir
    static Path tempDir;

    private static Path databaseFile;
    private static Future<String> image;

    @BeforeAll
    static void createDatabase() throws Exception {
        databaseFile = tempDir.resolve("database.duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + databaseFile);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE \"Fact\" (\"KEY\" VARCHAR, \"VALUE\" INTEGER)");
            statement.executeUpdate("INSERT INTO \"Fact\" VALUES ('A', 1), ('B', 2), ('C', 3)");
        }
        assertTrue(Files.exists(databaseFile));
        image = PivotContainers.pivotImage("duckdb");
    }

    @Test
    void sumMeasureIsQueryableViaXmla() throws Exception {
        assertSumIsQueryable(Map.of(), MODE_WRITABLE);
    }

    @Test
    void opensReadOnlyByDefault() throws Exception {
        assertSumIsQueryable(Map.of(), MODE_NOT_WRITABLE);
    }

    @Test
    void accessModeReadOnlyOpensReadOnly() throws Exception {
        assertSumIsQueryable(Map.of("DAANSE_JDBC_ACCESS_MODE", "READ_ONLY"), MODE_NOT_WRITABLE);
    }

    /** The access mode decides, and the connection pool has to follow it. */
    @Test
    void accessModeReadOnlyWinsOverReadOnlyFalse() throws Exception {
        assertSumIsQueryable(Map.of("DAANSE_JDBC_ACCESS_MODE", "READ_ONLY", "DAANSE_JDBC_READ_ONLY", "false"),
                MODE_NOT_WRITABLE);
    }

    @Test
    void accessModeSettingOpensReadOnly() throws Exception {
        assertSumIsQueryable(Map.of("DAANSE_JDBC_SETTINGS", "access_mode=READ_ONLY,threads=2"), MODE_NOT_WRITABLE);
    }

    /** Replaces the read-only default instead of contradicting it. */
    @Test
    void accessModeReadWriteIsQueryable() throws Exception {
        assertSumIsQueryable(Map.of("DAANSE_JDBC_ACCESS_MODE", "READ_WRITE"), MODE_WRITABLE);
    }

    @Test
    void readOnlyFalseIsQueryable() throws Exception {
        assertSumIsQueryable(Map.of("DAANSE_JDBC_READ_ONLY", "false"), MODE_WRITABLE);
    }

    /** The Fact table of the catalog only exists as the view the init file creates. */
    @Test
    void sessionInitSqlFileIsRun() throws Exception {
        Path viewless = tempDir.resolve("viewless.duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + viewless);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE \"FactData\" (\"KEY\" VARCHAR, \"VALUE\" INTEGER)");
            statement.executeUpdate("INSERT INTO \"FactData\" VALUES ('A', 1), ('B', 2), ('C', 3)");
        }
        Path initSql = tempDir.resolve("init.sql");
        Files.writeString(initSql, "CREATE OR REPLACE VIEW \"Fact\" AS SELECT * FROM \"FactData\";");

        assertSumIsQueryable(Map.of("DAANSE_JDBC_ACCESS_MODE", "READ_WRITE", "DAANSE_JDBC_SESSION_INIT_SQL_FILE",
                "/app/init/init.sql"), viewless, MODE_WRITABLE, initSql);
    }

    private static void assertSumIsQueryable(Map<String, String> env, int databaseFileMode) throws Exception {
        assertSumIsQueryable(env, databaseFile, databaseFileMode, null);
    }

    private static void assertSumIsQueryable(Map<String, String> env, Path database, int databaseFileMode,
            Path initSql) throws Exception {
        try (GenericContainer<?> pivot = new GenericContainer<>(image)
                .withCopyFileToContainer(MountableFile.forClasspathResource("catalog.xmi"),
                        "/app/catalog/catalog.xmi")
                .withCopyFileToContainer(MountableFile.forHostPath(database, databaseFileMode),
                        "/app/data/database.duckdb")
                .withEnv(env)
                .withExposedPorts(8080)
                .waitingFor(Wait.forListeningPort())) {
            if (initSql != null) {
                pivot.withCopyFileToContainer(MountableFile.forHostPath(initSql), "/app/init/init.sql");
            }
            pivot.start();

            String response = PivotContainers.awaitMdxCell(pivot.getHost(), pivot.getMappedPort(8080),
                    PivotContainers.TEST_CATALOG, PivotContainers.TEST_MDX, "6");

            assertTrue(response.contains("6</Value>"), response);
        }
    }
}
