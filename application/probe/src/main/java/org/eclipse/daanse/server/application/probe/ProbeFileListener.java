/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.server.application.probe;

import static org.eclipse.daanse.jdbc.datasource.duckdb.api.Constants.DATASOURCE_PROPERTY_DATABASENAME;
import static org.eclipse.daanse.jdbc.datasource.duckdb.api.Constants.DATASOURCE_PROPERTY_SETTINGS;
import static org.eclipse.daanse.jdbc.datasource.duckdb.api.Constants.PID_DATASOURCE;
import static org.eclipse.daanse.jdbc.datasource.pools.api.Constants.POOL_PROPERTY_DATASOURCE_TARGET;
import static org.eclipse.daanse.jdbc.datasource.pools.hikari.api.Constants.PID_CONNECTION_POOL;
import static org.eclipse.daanse.sql.jdbc.importer.csv.api.Constants.PID_CSV_DATA_IMPORTER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_PID;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CONNECTION_POOL;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.daanse.io.fs.watcher.api.FileSystemWatcherListener;
import org.eclipse.daanse.io.fs.watcher.api.FileSystemWatcherWhiteboardConstants;
import org.eclipse.daanse.io.fs.watcher.api.propertytypes.FileSystemWatcherListenerProperties;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.annotations.RequireConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.RequireServiceComponentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(configurationPid = ProbeFileListener.PID, configurationPolicy = ConfigurationPolicy.REQUIRE)
@RequireConfigurationAdmin
@RequireServiceComponentRuntime
@FileSystemWatcherListenerProperties(recursive = false)
public class ProbeFileListener implements FileSystemWatcherListener {

    static final String MATCHER_KEY = "matcherKey";

    private static final Logger logger = LoggerFactory.getLogger(ProbeFileListener.class);

    static final String PID = "daanse.server.application.probe.ProbeFileListener";

    static final String KEY_FILE_CONTEXT_MATCHER = "file.context.matcher";

    private static final String TARGET_EXT = ".target";

    @Reference
    ConfigurationAdmin ca;

    private Map<Path, Configuration> catalogFolderConfigsDS = new ConcurrentHashMap<>();
    private Map<Path, Configuration> catalogFolderConfigsPool = new ConcurrentHashMap<>();
    private Map<Path, Configuration> catalogFolderConfigsCSV = new ConcurrentHashMap<>();
    private Map<Path, Configuration> catalogFolderConfigsContext = new ConcurrentHashMap<>();
    private Map<Path, Configuration> catalogFolderConfigsMapping = new ConcurrentHashMap<>();
    private Map<Path, Configuration> catalogFolderConfigsCheckSuite = new ConcurrentHashMap<>();

    @Override
    public void handleBasePath(Path basePath) {
        // not relevant
    }

    @Override
    public void handleInitialPaths(List<Path> paths) {
        paths.forEach(this::addPath);
    }

    @Override
    public void handlePathEvent(Path path, Kind<Path> kind) {

        if (StandardWatchEventKinds.ENTRY_MODIFY.equals(kind)) {
            removePath(path);
            addPath(path);
        } else if (StandardWatchEventKinds.ENTRY_CREATE.equals(kind)) {
            addPath(path);
        } else if (StandardWatchEventKinds.ENTRY_DELETE.equals(kind)) {
            removePath(path);
        }
    }

    private void removePath(Path path) {
        if (!Files.isDirectory(path)) {
            return;
        }

        try {
            Configuration c = catalogFolderConfigsDS.remove(path);
            c.delete();
        } catch (IOException e) {
            logger.error("Failed to delete DS configuration for path: {}", path, e);
        }

        try {
            Configuration c = catalogFolderConfigsCSV.remove(path);
            c.delete();
        } catch (IOException e) {
            logger.error("Failed to delete CSV configuration for path: {}", path, e);
        }

        try {
            Configuration c = catalogFolderConfigsContext.remove(path);
            c.delete();
        } catch (IOException e) {
            logger.error("Failed to delete context configuration for path: {}", path, e);
        }

        try {
            Configuration c = catalogFolderConfigsPool.remove(path);
            if (c != null) {
                c.delete();
            }
        } catch (IOException e) {
            logger.error("Failed to delete connection pool configuration for path: {}", path, e);
        }

        try {
            Configuration c = catalogFolderConfigsMapping.remove(path);
            c.delete();
        } catch (IOException e) {
            logger.error("Failed to delete mapping configuration for path: {}", path, e);
        }

        try {
            Configuration c = catalogFolderConfigsCheckSuite.remove(path);
            if (c != null) {
                c.delete();
            }
        } catch (IOException e) {
            logger.error("Failed to delete check suite configuration for path: {}", path, e);
        }
    }

    private void addPath(Path path) {
        if (!Files.isDirectory(path)) {
            return;
        }
        logger.info("Adding catalog path: {}", path);
        String pathString = path.toString();

        String matcherKey = pathString.replace("\\", "-.-");
        matcherKey = UUID.randomUUID().toString();

        try {
            createDataSource(path, matcherKey);
            createConnectionPool(path, matcherKey);
            createCsvDatabaseImporter(path, matcherKey);
            createMapping(path, matcherKey);
            createCheckSuite(path, matcherKey);
            createContext(path, matcherKey);

        } catch (IOException e) {
            logger.error("Failed to setup configurations for path: {}", path, e);
        }

    }

    private void createMapping(Path path, String matcherKey) {

        try {
            Configuration configXmiFileListener = ca.getFactoryConfiguration(CatalogXmiFileListener.PID,
                    UUID.randomUUID().toString(), "?");

            Dictionary<String, Object> props = new Hashtable<>();
            String pathMapping = path.resolve("mapping").toAbsolutePath().toString();
            props.put(FileSystemWatcherWhiteboardConstants.FILESYSTEM_WATCHER_PATH, pathMapping);
            props.put(MATCHER_KEY, matcherKey);

            configXmiFileListener.update(props);

            catalogFolderConfigsMapping.put(path, configXmiFileListener);
        } catch (IOException e) {
            logger.error("Failed to create mapping configuration for path: {}", path, e);
        }
    }

    private void createCheckSuite(Path path, String matcherKey) {
        Path checkSuitePath = path.resolve("check");
        if (!Files.isDirectory(checkSuitePath)) {
            logger.debug("No check directory found at: {}, skipping", checkSuitePath);
            return;
        }
        try {
            Configuration configCheckSuiteFileListener = ca.getFactoryConfiguration(CheckSuiteXmiFileListener.PID,
                    UUID.randomUUID().toString(), "?");

            Dictionary<String, Object> props = new Hashtable<>();
            props.put(FileSystemWatcherWhiteboardConstants.FILESYSTEM_WATCHER_PATH,
                    checkSuitePath.toAbsolutePath().toString());
            props.put(MATCHER_KEY, matcherKey);

            configCheckSuiteFileListener.update(props);

            catalogFolderConfigsCheckSuite.put(path, configCheckSuiteFileListener);
        } catch (IOException e) {
            logger.error("Failed to create check suite configuration for path: {}", path, e);
        }
    }

    private void createContext(Path path, String matcherKey) throws IOException {

        Configuration configContext = ca.getFactoryConfiguration(BASIC_CONTEXT_PID, UUID.randomUUID().toString(), "?");

        Dictionary<String, Object> props = new Hashtable<>();
        props.put(BASIC_CONTEXT_REF_NAME_CONNECTION_POOL + TARGET_EXT, filterOfMatcherKey(matcherKey));
        // Name the dialect explicitly: the context binds whichever factory answers
        // first, so a dialect bundle added later would silently take over.
        props.put(BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY + TARGET_EXT, "(org.eclipse.daanse.dialect.name=DUCKDB)");
        props.put(BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER + TARGET_EXT, filterOfMatcherKey(matcherKey));

        props.put("useAggregates", true);
        props.put(KEY_FILE_CONTEXT_MATCHER, matcherKey);
        configContext.update(props);

        catalogFolderConfigsContext.put(path, configContext);

    }

    private void createCsvDatabaseImporter(Path path, String matcherKey) throws IOException {
        String pathStringData = path.resolve("data").toString();
        Configuration configCsv = ca.getFactoryConfiguration(PID_CSV_DATA_IMPORTER, UUID.randomUUID().toString(), "?");

        Dictionary<String, Object> propsCSV = new Hashtable<>();
        propsCSV.put(FileSystemWatcherWhiteboardConstants.FILESYSTEM_WATCHER_PATH, pathStringData);
        propsCSV.put("dataSource.target", filterOfMatcherKey(matcherKey));
        configCsv.update(propsCSV);

        catalogFolderConfigsCSV.put(path, configCsv);
    }

    /** The in-memory DuckDB database this catalog folder is loaded into. */
    private void createDataSource(Path path, String matcherKey) throws IOException {
        Configuration config = ca.getFactoryConfiguration(PID_DATASOURCE, UUID.randomUUID().toString(), "?");
        Dictionary<String, Object> props = new Hashtable<>();
        // The DataSource keeps the owning connection and hands out duplicates,
        // which makes one in-memory database reachable from importer and pool alike.
        props.put(DATASOURCE_PROPERTY_DATABASENAME, ":memory:");
        props.put(DATASOURCE_PROPERTY_SETTINGS, new String[] { "threads=4" });
        props.put(KEY_FILE_CONTEXT_MATCHER, matcherKey);
        config.update(props);

        catalogFolderConfigsDS.put(path, config);
    }

    /**
     * The pool between the folder's DataSource and its context. The context binds
     * a pool, never a DataSource, so every catalog folder needs one of its own.
     */
    private void createConnectionPool(Path path, String matcherKey) throws IOException {
        Configuration configPool = ca.getFactoryConfiguration(PID_CONNECTION_POOL, UUID.randomUUID().toString(), "?");

        Dictionary<String, Object> props = new Hashtable<>();
        props.put(POOL_PROPERTY_DATASOURCE_TARGET, filterOfMatcherKey(matcherKey));
        props.put(KEY_FILE_CONTEXT_MATCHER, matcherKey);
        configPool.update(props);

        catalogFolderConfigsPool.put(path, configPool);
    }

    private static final String filterOfMatcherKey(String matcherKey) {
        return "(" + KEY_FILE_CONTEXT_MATCHER + "=" + matcherKey + ")";
    }

}
