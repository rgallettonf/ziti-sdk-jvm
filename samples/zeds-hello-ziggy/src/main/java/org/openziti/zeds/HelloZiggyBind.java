package org.openziti.zeds;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.openziti.Ziti;
import org.openziti.ZitiAddress;
import org.openziti.ZitiContext;
import org.openziti.ZitiContext.Status;
import org.openziti.api.Service;

import kotlin.text.Charsets;

public class HelloZiggyBind {

	static final String IDENTITY_EXT = ".ident";

	public static void run() throws Exception {

    	Properties props = readProperties();
    	String identityDirectory = props.getProperty("zeds.identity.dir");
    	String service = props.getProperty("zeds.service.bind.name");
    	String identityName = props.getProperty("zeds.identity.name");
        String config = identityDirectory + identityName + IDENTITY_EXT;

        if (service == null || service == "") {
        	System.out.println("No service defined in properties file. Check you settings and try again");
        	System.exit(0);
        }

        if (identityName == null || identityName == "") {
        	System.out.println("No identity defined in properties file. Check you settings and try again");
        	System.exit(0);
        }

        Ziti.setApplicationInfo("org.openziti.sample.NetCatHost", "v1.0");
        ZitiContext ziti = Ziti.newContext(config, "".toCharArray());
        Service svc = ziti.getService(service, 10000);
        if (svc == null) {
        	throw new RuntimeException("Service not found");
        }
    	AsynchronousServerSocketChannel server = ziti.openServer();
        server.bind(new ZitiAddress.Bind(service));
        processClients(server);
	}

    public static Properties readProperties() throws Exception {
    	Properties prop = new Properties();
    	String fileName = "../resources/main/hello-ziggy.properties";
    	FileInputStream fis = new FileInputStream(fileName);
    	prop.load(fis);
    	return prop;
    }

    public static BufferedReader readZiggyAscii() throws Exception {
    	InputStream in = HelloZiggyBind.class.getResourceAsStream("/hello-ziggy.txt");
    	BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    	return reader;
    }

    public static void processClients(AsynchronousServerSocketChannel server) throws Exception {
        while (true) {
            System.out.println("waiting for clients");
            AsynchronousSocketChannel clt = server.accept().get();
            System.out.println("client connected");

            ByteBuffer readBuf = ByteBuffer.allocate(1024);
            boolean isCommandLine = false;
            while (true) {
            	int bytesRead = clt.read(readBuf).get();
                if (bytesRead == -1) {
                	System.out.println("Closing Connection");
                    clt.close();
                    break;
                } else {
                    readBuf.flip();
                    String text = Charsets.UTF_8.decode(readBuf).toString();
                    System.out.print(text);
                    if (text != null && text.indexOf("User-Agent: Hello Ziggy Client") >= 0) {
                    	isCommandLine = true;
                    }
                    readBuf.compact();
                    if (bytesRead < 1024) {
                    	break;
                    }
                }
            }

            String resp = "";
            if (isCommandLine) {
            	BufferedReader in = readZiggyAscii();
            	String line;
            	while((line = in.readLine()) != null)
            	{
            		resp += line + "\n";
            	}
            	in.close();
            } else {
            	resp =  "HTTP/1.1 200\n" +
            			"Accept-Ranges: bytes\n" +
		                "Access-Control-Allow-Origin: *\n" +
		                "Connection: keep-alive\n" +
		                "Content-Length: 109032\n" +
		                "Content-Type: text/html\n\n" +
		        		"<div style=\"width: 100%; height: 200px; display: flex; align-items: center; justify-content: center;\"><img src=\"https://sandbox.zeds.openziti.org/assets/images/ZiggyHello.svg\"></img></div>\n";            	
            }

            byte[] responseHeaders = resp.getBytes();
            Future<Integer> future = clt.write(ByteBuffer.wrap(responseHeaders));

            clt.close();
        }
    }
}
