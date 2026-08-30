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

/**
 * Constants shared between the common pivot setup and the database specific
 * configurator components.
 */
public final class ServerConstants {

    private ServerConstants() {
    }

    /** Ident used as 2nd argument of {@code getFactoryConfiguration(pid, ident, "?")}. */
    public static final String CONFIG_IDENT = "pivot";

    /** Service property used to wire the environment configured services together. */
    public static final String PROP_IDENT = "daanse.ident";

    public static final String IDENT_DATASOURCE = "env-ds";
    public static final String IDENT_POOL = "env-pool";
    public static final String IDENT_MAPPING = "env-cms";
    public static final String IDENT_CONTEXT = "env-ctx";

    public static final String TARGET_EXT = ".target";

    /**
     * Prefix of the environment variables that configure the DataSource. The
     * variable name is derived from the config attribute, e.g.
     * {@code currentSchema} is set via {@code DAANSE_JDBC_CURRENT_SCHEMA}.
     */
    public static final String ENV_JDBC_PREFIX = "DAANSE_JDBC_";

    /**
     * Prefix of the environment variables that configure the connection pool
     * between the DataSource and the context, e.g. {@code maximumPoolSize} is set
     * via {@code DAANSE_POOL_MAXIMUM_POOL_SIZE}.
     */
    public static final String ENV_POOL_PREFIX = "DAANSE_POOL_";

    /**
     * Whether requests without credentials are served (with no roles). Default
     * true; false makes the XMLA servlet challenge every request.
     */
    public static final String ENV_AUTH_ANONYMOUS = "DAANSE_AUTH_ANONYMOUS";
    /** Realm of the HTTP Basic challenge. */
    public static final String ENV_AUTH_REALM = "DAANSE_AUTH_REALM";

    /**
     * Environment variables of the LDAP backed HTTP Basic authentication.
     * Setting {@link #ENV_LDAP_URL} switches it on: credentials are verified by
     * an LDAP bind, roles come from LDAP groups when
     * {@link #ENV_LDAP_GROUP_SEARCH_BASE} is set. Unset means anonymous only.
     */
    public static final String ENV_LDAP_URL = "DAANSE_LDAP_URL";
    public static final String ENV_LDAP_USER_DN_PATTERN = "DAANSE_LDAP_USER_DN_PATTERN";
    public static final String ENV_LDAP_USER_SEARCH_BASE = "DAANSE_LDAP_USER_SEARCH_BASE";
    public static final String ENV_LDAP_USER_SEARCH_FILTER = "DAANSE_LDAP_USER_SEARCH_FILTER";
    public static final String ENV_LDAP_SERVICE_BIND_DN = "DAANSE_LDAP_SERVICE_BIND_DN";
    public static final String ENV_LDAP_SERVICE_BIND_PASSWORD = "DAANSE_LDAP_SERVICE_BIND_PASSWORD";
    public static final String ENV_LDAP_TRANSPORT_SECURITY = "DAANSE_LDAP_TRANSPORT_SECURITY";
    public static final String ENV_LDAP_ALLOW_UNENCRYPTED = "DAANSE_LDAP_ALLOW_UNENCRYPTED";
    public static final String ENV_LDAP_CONNECT_TIMEOUT_MILLIS = "DAANSE_LDAP_CONNECT_TIMEOUT_MILLIS";
    public static final String ENV_LDAP_READ_TIMEOUT_MILLIS = "DAANSE_LDAP_READ_TIMEOUT_MILLIS";
    public static final String ENV_LDAP_REFERRAL = "DAANSE_LDAP_REFERRAL";
    public static final String ENV_LDAP_GROUP_SEARCH_BASE = "DAANSE_LDAP_GROUP_SEARCH_BASE";
    public static final String ENV_LDAP_GROUP_SEARCH_FILTER = "DAANSE_LDAP_GROUP_SEARCH_FILTER";
    public static final String ENV_LDAP_GROUP_NAME_ATTRIBUTE = "DAANSE_LDAP_GROUP_NAME_ATTRIBUTE";
    public static final String ENV_LDAP_MEMBER_OF_ATTRIBUTE = "DAANSE_LDAP_MEMBER_OF_ATTRIBUTE";

    public static final String ENV_CATALOG_RESOURCE = "DAANSE_CATALOG_RESOURCE";
    public static final String ENV_CATALOG_ADDITIONAL_GLOBS = "DAANSE_CATALOG_ADDITIONAL_GLOBS";
    public static final String ENV_USE_AGGREGATES = "DAANSE_USE_AGGREGATES";
    public static final String ENV_XMLA_PATH = "DAANSE_XMLA_PATH";
    public static final String ENV_CORS_ENABLED = "DAANSE_CORS_ENABLED";
    public static final String ENV_CORS_ALLOWED_ORIGINS = "DAANSE_CORS_ALLOWED_ORIGINS";
    public static final String ENV_CORS_ALLOWED_HEADERS = "DAANSE_CORS_ALLOWED_HEADERS";
    public static final String ENV_CORS_ALLOW_CREDENTIALS = "DAANSE_CORS_ALLOW_CREDENTIALS";

    public static final String DEFAULT_CATALOG_RESOURCE = "/app/catalog/catalog.xmi";
    public static final String DEFAULT_XMLA_PATH = "/xmla";
}
