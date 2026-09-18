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

import java.io.IOException;
import java.util.Dictionary;
import java.util.List;

import org.eclipse.daanse.jdbc.datasource.duckdb.api.Constants;
import org.eclipse.daanse.jdbc.datasource.duckdb.api.ocd.DsConfig;
import org.eclipse.daanse.server.application.pivot.common.BasicContextConfigs;
import org.eclipse.daanse.server.application.pivot.common.EnvConfigMapper;
import org.eclipse.daanse.server.application.pivot.common.ServerConstants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.annotations.RequireConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.RequireServiceComponentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the DuckDB DataSource from the {@code DAANSE_JDBC_*} environment
 * variables and creates the BasicContext against it. Every attribute of
 * {@link DsConfig} is supported. The database file defaults to
 * {@code /app/data/database.duckdb} (mount it into the container) and is
 * opened read-only unless {@code DAANSE_JDBC_READ_ONLY=false} or
 * {@code DAANSE_JDBC_ACCESS_MODE} (or an {@code access_mode} entry of
 * {@code DAANSE_JDBC_SETTINGS}) names another mode.
 */
@Component(immediate = true)
@RequireConfigurationAdmin
@RequireServiceComponentRuntime
public class DuckdbEnvConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(DuckdbEnvConfigurator.class);

    private static final String DIALECT_NAME = "DUCKDB";

    private static final String DEFAULT_DATABASE_FILE = "/app/data/database.duckdb";

    @Reference
    ConfigurationAdmin ca;

    private Configuration confDataSource;
    private List<Configuration> confPoolAndContext = List.of();

    @Activate
    public void activate() throws IOException {
        Dictionary<String, Object> props = EnvConfigMapper.propsFromEnv(DsConfig.class);
        if (props.get(Constants.DATASOURCE_PROPERTY_DATABASENAME) == null) {
            props.put(Constants.DATASOURCE_PROPERTY_DATABASENAME, DEFAULT_DATABASE_FILE);
        }
        // A given access mode decides; read-only is only the default where it
        // is absent. DuckDB refuses the two contradicting.
        String accessMode = accessMode(props);
        boolean accessModeReadOnly = Constants.ACCESS_MODE_READ_ONLY.equalsIgnoreCase(accessMode);
        if (props.get(Constants.DATASOURCE_PROPERTY_READ_ONLY) == null) {
            props.put(Constants.DATASOURCE_PROPERTY_READ_ONLY, accessMode == null || accessModeReadOnly);
        }
        props.put(ServerConstants.PROP_IDENT, ServerConstants.IDENT_DATASOURCE);

        confDataSource = ca.getFactoryConfiguration(Constants.PID_DATASOURCE, ServerConstants.CONFIG_IDENT, "?");
        confDataSource.update(props);

        // DuckDB connections are fixed to the read-only mode of the DataSource;
        // the pool must mark its connections the same way or the driver refuses.
        confPoolAndContext = BasicContextConfigs.createEnvPoolAndContext(ca, DIALECT_NAME,
                accessModeReadOnly || (Boolean) props.get(Constants.DATASOURCE_PROPERTY_READ_ONLY));

        logger.info("DuckDB DataSource, connection pool and context configured from environment");
    }

    /**
     * The configured access mode - the attribute, else the {@code access_mode}
     * entry of the settings - or {@code null}.
     */
    static String accessMode(Dictionary<String, Object> props) {
        if (props.get(Constants.DATASOURCE_PROPERTY_ACCESS_MODE) instanceof String attribute) {
            return attribute.trim();
        }
        if (props.get(Constants.DATASOURCE_PROPERTY_SETTINGS) instanceof String[] entries) {
            for (String entry : entries) {
                int equals = entry.indexOf('=');
                if (equals > 0 && Constants.SETTING_ACCESS_MODE.equalsIgnoreCase(entry.substring(0, equals).trim())) {
                    return entry.substring(equals + 1).trim();
                }
            }
        }
        return null;
    }

    @Deactivate
    public void deactivate() throws IOException {
        if (confDataSource != null) {
            confDataSource.delete();
        }
        for (Configuration configuration : confPoolAndContext) {
            configuration.delete();
        }
        confPoolAndContext = List.of();
    }
}
