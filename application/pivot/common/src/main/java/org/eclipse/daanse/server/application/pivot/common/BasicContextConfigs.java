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
package org.eclipse.daanse.server.application.pivot.common;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.daanse.rolap.core.api.Constants;
import org.eclipse.daanse.sql.dialect.api.DaanseDialectConstants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Creates the BasicContext that joins the connection pool over the environment
 * configured DataSource, the catalog mapping provider and the database specific
 * dialect.
 */
public final class BasicContextConfigs {

    private BasicContextConfigs() {
    }

    /**
     * The pool and the context that draws from it - one lifecycle, since the
     * context cannot activate without the pool. Returned in deletion order:
     * context first, then the pool it depends on.
     */
    public static List<Configuration> createEnvPoolAndContext(ConfigurationAdmin ca, String dialectName)
            throws IOException {
        return createEnvPoolAndContext(ca, dialectName, null);
    }

    /**
     * As {@link #createEnvPoolAndContext(ConfigurationAdmin, String)}, with a
     * default for the pool's read-only mode - see
     * {@link ConnectionPoolConfigs#createEnvPool(ConfigurationAdmin, Boolean)}.
     */
    public static List<Configuration> createEnvPoolAndContext(ConfigurationAdmin ca, String dialectName,
            Boolean poolReadOnlyDefault) throws IOException {
        Configuration pool = ConnectionPoolConfigs.createEnvPool(ca, poolReadOnlyDefault);
        return List.of(createEnvBasicContext(ca, dialectName), pool);
    }

    public static Configuration createEnvBasicContext(ConfigurationAdmin ca, String dialectName) throws IOException {
        Configuration configuration = ca.getFactoryConfiguration(Constants.BASIC_CONTEXT_PID,
                ServerConstants.CONFIG_IDENT, "?");

        Dictionary<String, Object> props = new Hashtable<>();
        props.put(ServerConstants.PROP_IDENT, ServerConstants.IDENT_CONTEXT);
        props.put(Constants.BASIC_CONTEXT_REF_NAME_CONNECTION_POOL + ServerConstants.TARGET_EXT,
                filter(ServerConstants.PROP_IDENT, ServerConstants.IDENT_POOL));
        props.put(Constants.BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER + ServerConstants.TARGET_EXT,
                filter(ServerConstants.PROP_IDENT, ServerConstants.IDENT_MAPPING));
        props.put(Constants.BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY + ServerConstants.TARGET_EXT,
                filter(DaanseDialectConstants.DIALECT_NAME_PROPERTY, dialectName));
        props.put("useAggregates", Env.get(ServerConstants.ENV_USE_AGGREGATES, false));

        configuration.update(props);
        return configuration;
    }

    private static String filter(String key, String value) {
        return "(" + key + "=" + value + ")";
    }
}
