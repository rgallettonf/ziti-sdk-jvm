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

package org.openziti.jdbc

import java.sql.Connection
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.SQLException
import java.util.*
import java.util.logging.Logger

class ZDBCDriver : java.sql.Driver {
    val logger = PARENT_LOGGER
    companion object {
        var registeredDriver = ZDBCDriver()
        val PARENT_LOGGER = Logger.getLogger("org.openziti.jdbc")

        init {
            try {
                check(!isRegistered()) { "Driver is already registered. It can only be registered once." }
                val registeredDriver = ZDBCDriver()
                DriverManager.registerDriver(registeredDriver)
                ZDBCDriver.registeredDriver = registeredDriver
            } catch (e: SQLException) {
                throw ExceptionInInitializerError(e)
            }
        }

        fun isRegistered(): Boolean {
            return registeredDriver != null
        }
    }

    override fun connect(url: String, info: Properties?): Connection? {
        if (url.startsWith("zdbc")) {
            if (url.startsWith("zdbc:postgresql")) {
                val u = url.replace("zdbc", "jdbc");
                info?.setProperty("socketFactory", "org.openziti.net.ZitiSocketFactory");
                return DriverManager.getConnection(u, info);
            }
        }
        return null;
    }

    override fun acceptsURL(url: String?): Boolean {
        if(url != null){
            return url.startsWith("zdbc");
        } else {
            return false;
        }
    }

    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> {
        TODO("Not yet implemented")
    }

    override fun getMajorVersion(): Int {
        return 0;
    }

    override fun getMinorVersion(): Int {
        return 1;
    }

    override fun jdbcCompliant(): Boolean {
        return false;
    }

    override fun getParentLogger(): Logger {
        return PARENT_LOGGER;
    }
}