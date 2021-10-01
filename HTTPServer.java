/**
 ** Yale CS433/533 Demo Basic Web Server
 **/

import java.io.*;
import java.net.*;
import java.util.*;

class HTTPServer{

    public static int serverPort = 6789;    
    public static String WWW_ROOT = "/home/httpd/html/zoo/classes/cs434/";
    //public static String WWW_ROOT = "./";

    public static void main(String args[]) throws Exception  {

	// creates map 
	HashMap<String, String> configArgs = new HashMap<String, String>();

	if (args[0].equals("-config")){
		// sets config values
		Scanner parser = new Scanner(new File(args[1]));
		String tempRoot = "";
		String tempServerName = "";
			
		String iterator = parser.next();
		
		// parse to set values
		while (parser.hasNext()) {
			if (iterator.equals("Listen")){
				iterator = parser.next();
				configArgs.put("Port", iterator);
			}
			else if (iterator.equals("CacheSize")){
				iterator = parser.next();
				configArgs.put("CacheSize", iterator);
			}
			else if (iterator.equals("DocumentRoot")){
				iterator = parser.next();
				tempRoot = iterator;
			}
			else if (iterator.equals("ServerName")){
				iterator = parser.next();
				tempServerName = iterator;
				configArgs.put(tempServerName, tempRoot);
			}
			iterator = parser.next();
		}

		serverPort = Integer.valueOf(configArgs.get("Port"));
	}
	else {
		// see if we do not use default server port
		if (args.length >= 1 )
			serverPort = Integer.parseInt(args[0]);

		// see if we want a different root
		if (args.length >= 2)
			WWW_ROOT = args[1];
	}

	// create server socket
	ServerSocket listenSocket = new ServerSocket(serverPort);
	System.out.println("server listening at: " + listenSocket);
	System.out.println("server www root: " + WWW_ROOT);

	// create server cache - stores static files
	HashMap<String, String> serverCache = new HashMap<String, String>();

	// set max cache size
	int maxServerCacheSize = Integer.valueOf(configArgs.get("CacheSize"));

	while (true) {

	    try {

		    // take a ready connection from the accepted queue
		    Socket connectionSocket = listenSocket.accept();
		    System.out.println("\nReceive request from " + connectionSocket);
	
		    // process a request
		    HTTPRequestHandler wrh = 
		        new HTTPRequestHandler(connectionSocket, WWW_ROOT, serverCache, maxServerCacheSize, configArgs);

		    wrh.processRequest();

	    } catch (Exception e)
		{
		}
	} // end of while (true)
	
} // end of main

} // end of class WebServer
