/*
 * Copyright (c) 2018-2021 NetFoundry, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openziti.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashSet;
import java.util.Properties;
import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;
import org.openziti.Ziti;

public class ZDBCDriverJava implements java.sql.Driver {

    private static @Nullable
    ZDBCDriverJava registeredDriver;
    private static HashSet<String> initializedIdentities = new HashSet<>();

    static {
        try {
            register();
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Register the driver against {@link DriverManager}. This is done automatically
     * when the class is loaded. Dropping the driver from DriverManager's list is
     * possible using {@link #deregister()} method.
     *
     * @throws IllegalStateException if the driver is already registered
     * @throws SQLException          if registering the driver fails
     */
    public static void register() throws SQLException {
        if (isRegistered()) {
            throw new IllegalStateException("Driver is already registered. It can only be registered once.");
        }
        ZDBCDriverJava registeredDriver = new ZDBCDriverJava();
        DriverManager.registerDriver(registeredDriver);
        ZDBCDriverJava.registeredDriver = registeredDriver;
    }

    /**
     * @return {@code true} if the driver is registered against
     *         {@link DriverManager}
     */
    public static boolean isRegistered() {
        return registeredDriver != null;
    }

    /**
     * According to JDBC specification, this driver is registered against
     * {@link DriverManager} when the class is loaded. To avoid leaks, this method
     * allow unregistering the driver so that the class can be gc'ed if necessary.
     *
     * @throws IllegalStateException if the driver is not registered
     * @throws SQLException          if deregistering the driver fails
     */
    public static void deregister() throws SQLException {
        if (registeredDriver == null) {
            throw new IllegalStateException(
                    "Driver is not registered (or it has not been registered using Driver.register() method)");
        }
        DriverManager.deregisterDriver(registeredDriver);
        registeredDriver = null;
    }

    public Connection connect(String url, Properties info) throws SQLException {
        if (url.startsWith("zdbc")) {
            if (url.startsWith("zdbc:postgresql")) {
                String id = info.getProperty("ziti-identity");
                if (id != null) {
                    if(! initializedIdentities.contains(id)) {
                        Ziti.init(id, "".toCharArray(), false);
                    }
                    url = url.replaceAll("zdbc", "jdbc");
                    info.setProperty("socketFactory", "org.openziti.net.ZitiSocketFactory");
                    return DriverManager.getConnection(url, info);
                }
                throw new UnsupportedOperationException("ziti-identity not provided");
            }
        }
        return null;
    }

    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith("zdbc");
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        throw new UnsupportedOperationException("aceptsURL");
    }

    public int getMajorVersion() {
        return 0;
    }

    public int getMinorVersion() {
        return 1;
    }

    public boolean jdbcCompliant() {
        return false;
    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new UnsupportedOperationException("aceptsURL");
    }
}
