/*
 * Copyright (c) 2018-2020 NetFoundry, Inc.
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

package org.openziti.zeds;

import org.openziti.Ziti;
import org.openziti.ZitiConnection;
import org.openziti.ZitiContext;
import org.openziti.ZitiContext.Status;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class HelloZiggyDial {

	static final String IDENTITY_EXT = ".ident";

	public static void run() throws Exception {
    	Properties props = readProperties();
    	String identityDirectory = props.getProperty("zeds.identity.dir");
    	String service = props.getProperty("zeds.service.dial.name");
    	String identityName = props.getProperty("zeds.identity.name");
        String config = identityDirectory + identityName + IDENTITY_EXT;
        String dialHost = props.getProperty("zeds.service.dial.host");
        
        if (service == null || service == "") {
        	System.out.println("No service defined in properties file. Check you settings and try again");
        	System.exit(0);
        }

        if (identityName == null || identityName == "") {
        	System.out.println("No identity defined in properties file. Check you settings and try again");
        	System.exit(0);
        }

        if (dialHost == null || dialHost == "") {
        	System.out.println("No dial host defined in properties file. Check you settings and try again");
        	System.exit(0);
        }

        ZitiContext ziti = Ziti.newContext(config, "".toCharArray());
        Status zitiStatus = ziti.statusUpdates().getValue();
        while (zitiStatus != null) {
        	if (zitiStatus instanceof Status.Active) {
        		break;
        	}
        	zitiStatus = ziti.statusUpdates().getValue();
        }
        Thread.sleep(3000);
        ZitiConnection conn = ziti.dial(service);

        String req = "GET / HTTP/1.1\n" +
                "Accept: */*\n" +
                "Accept-Encoding: gzip, deflate\n" +
                "Connection: close\n" +
                "User-Agent: Hello Ziggy Client\n" +
                dialHost +
                "\n";
        conn.write(req.getBytes());

        byte[] resp = new byte[1024];

        int rc = 0;
        ByteArrayOutputStream r = new ByteArrayOutputStream();
        do {
            rc = conn.read(resp, 0, resp.length);
            if (rc > 0) {
                r.write(resp, 0, rc);
            }
        } while (rc > 0);

        System.out.println(new String(r.toByteArray()));     	

        ziti.destroy();
	}

    public static Properties readProperties() throws Exception {
    	Properties prop = new Properties();
    	String fileName = "../resources/main/hello-ziggy.properties";
    	FileInputStream fis = new FileInputStream(fileName);
	    prop.load(fis);
    	return prop;
    }
}
