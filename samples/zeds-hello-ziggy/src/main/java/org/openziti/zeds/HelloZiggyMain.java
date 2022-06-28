package org.openziti.zeds;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;

import java.net.InetAddress;
import java.security.KeyStore;
import java.util.Properties;
import java.util.Scanner;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import org.openziti.ZitiAddress;
import org.openziti.api.Service;
import org.openziti.ZitiContext.Status;
import org.openziti.Ziti;
import org.openziti.ZitiContext;
import org.openziti.identity.Enroller;
import org.openziti.ZitiConnection;

import kotlinx.coroutines.flow.*;

public class HelloZiggyMain {
	public static void main(String[] args) throws Exception {
		System.out.println("Running \"Hello Ziggy\" application...");
		if (args[0].equals("serve")) {
			System.out.println("Running Service Host...");
			HelloZiggyBind.run();
		} else if (args[0].equals("access")) {
			System.out.println("Running Service Access...");
			HelloZiggyDial.run();
		} else if (args[0].equals("enroll")) {
			System.out.println("Running Identity Enroller...");
			HelloZiggyEnroll.run();
		}
	}
}
