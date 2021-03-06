/**
 ** Yale CS433/533 Demo Basic Web Server
 **/
import java.io.*;
import java.net.*;
import java.net.Authenticator.RequestorType;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import javax.print.DocFlavor.STRING;

import jdk.nashorn.internal.runtime.Debug;

import java.time.Instant;

class HTTPRequestHandler {

    static boolean _DEBUG = true;
    static int     reqCount = 0;

	static boolean isModifiedSinceFlag = false;
	static boolean isMobileUser = false;
	String isModifiedSinceString = "";
	Date isModifiedSinceDate;
	Date lastModifiedDate;

	static boolean fileIsExecutable = false;
	String executableName = "./";

	String queryString = "";
	HashMap<String, String> envMap = new HashMap<String, String>();
	String requestMethod;

    String WWW_ROOT;
    Socket connSocket;
	HashMap<String, byte[]> serverCache;
	int maxServerCacheSize;
	HashMap<String, String> configMap;

    BufferedReader inFromClient;
    DataOutputStream outToClient;

    String urlName;
	String hostName;
    String fileName;
    File fileInfo;

	String tempPostInputFileName = "tempPostFile";
	File tempPostInputFileInfo;

	int postMsgLength;
	String POSTbodyInput;
	String POSTcontentType;

	Integer statusCode = 200;

    public HTTPRequestHandler(Socket connectionSocket, 
			     String WWW_ROOT, HashMap<String, byte[]> serverCache, int maxServerCacheSize, HashMap<String, String> configMap) throws Exception
    {
        reqCount ++;

	    this.WWW_ROOT = WWW_ROOT;
	    this.connSocket = connectionSocket;
		this.serverCache = serverCache;
		this.maxServerCacheSize = maxServerCacheSize;
		this.configMap = configMap;

	    inFromClient =
	      new BufferedReader(new InputStreamReader(connSocket.getInputStream()));

	    outToClient =
	      new DataOutputStream(connSocket.getOutputStream());

    }

    public void processRequest() 
    {

	try {
	    mapURL2File();

	    if ( fileInfo != null ) // found the file and knows its info
	    {
		    outputResponseHeader();
			if (isModifiedSinceFlag && isModifiedSinceDate.before(lastModifiedDate) || fileIsExecutable){
		    	outputResponseBody();
			}
	    } // do not handle error

	    connSocket.close();
	} catch (Exception e) {
	    outputError(400, "Server error");
	}



    } // end of processARequest

    private void mapURL2File() throws Exception 
    {
				
	    String requestMessageLine = inFromClient.readLine();
	    DEBUG("Request " + reqCount + ": " + requestMessageLine);

	    // GET OR POST request
	    String[] request = requestMessageLine.split("\\s");
	    if (request.length < 2 || !(request[0].equals("GET") || request[0].equals("POST")) || !request[2].equals("HTTP/1.1"))
	    {
		    outputError(500, "Bad request");
		    return;
	    }

		requestMethod = request[0];

	    // parse URL to retrieve file name
	    urlName = request[1];
	    if ( urlName.startsWith("/") == true )
	    	urlName  = urlName.substring(1);

		// indicates that there is a query string
		if (urlName.indexOf("?") != -1){
			queryString = urlName.substring(urlName.lastIndexOf("?") + 1);
			urlName = urlName.substring(0, urlName.lastIndexOf("?"));
		}

		// Content Selection is request ends in /
		if (urlName.endsWith("/") && !urlName.startsWith(".")){
			urlName = "index.html";
		}

		requestMessageLine = inFromClient.readLine();
		request = requestMessageLine.split("\\s");
	
		// Host Request
		if (request.length != 2 && !request[0].equals("HOST:")){
			outputError(500, "Bad request");
			return;
		}
		hostName = request[1];
		if (configMap.get(hostName) == null){
			outputError(404, "Host Not Found");
			return;
		}
		WWW_ROOT = configMap.get(hostName);	

		if (requestMethod.equals("POST")){
			while ( !requestMessageLine.equals("") ) {
				requestMessageLine = inFromClient.readLine();
				request = requestMessageLine.split("\\s");

				// Content-Type Header
				if (request[0].equals("Content-Type:")){
					// varying content types
					if (!(request[1].equals("application/json") || request[1].equals("application/x-www-form-urlencoded"))){
						outputError(500, "Bad request");
					}
					POSTcontentType = request[1];
				}
				// Content Length Header
				else if (request[0].equals("Content-Length:")){
					if (request.length == 2){
						postMsgLength = Integer.valueOf(request[1]);
					}
				}
			}
			requestMessageLine = inFromClient.readLine();
			// BODY of POST
			if (requestMessageLine.length() > postMsgLength){
				outputError(500, "Bad request");
			}
			POSTbodyInput = requestMessageLine;
			tempPostInputFileInfo = new File(tempPostInputFileName);
			BufferedWriter writer = new BufferedWriter(new FileWriter(tempPostInputFileInfo));
			writer.write(POSTbodyInput);
			writer.close();
			outToClient.writeBytes("\r\n");
		}
		else if (requestMethod.equals("GET")){
			// Process OTHER GET Header Requests
			while ( !requestMessageLine.equals("") ) {
				requestMessageLine = inFromClient.readLine();
				request = requestMessageLine.split("\\s");
				
				// User-Agent Request
				if (request[0].equals("User-Agent:")){
					if (!request[1].equals("Mozilla/5.0")){
						outputError(500, "Invalid User Agent");
						return;
					}
					if (request[2].equals("(iPhone;")){
						DEBUG("iphone god i love this");
						isMobileUser = true;
					}
				}

				// If-Modified-Since Request
				else if (request[0].equals("If-Modified-Since:")){
					isModifiedSinceFlag = true;

					for (int i = 1; i < request.length; i++){
						isModifiedSinceString = isModifiedSinceString.concat(request[i]);
						if (i != request.length - 1){
							isModifiedSinceString = isModifiedSinceString.concat(" ");
						}
					}

					isModifiedSinceDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").parse(isModifiedSinceString);
				}

				// If future implementation requires more headers, insert them here
			}
		}
	    // map to file name
	    fileName = WWW_ROOT + urlName;

	    fileInfo = new File(fileName);
		fileName = fileInfo.getCanonicalPath();
		DEBUG(fileName);
		File canonFileInfo = new File(WWW_ROOT);
		String canonicalRoot = canonFileInfo.getCanonicalPath();
		if (isMobileUser && fileName.equals(canonicalRoot)){
			File mobileFile = new File (canonicalRoot + "/index_m.html");
			if (!mobileFile.isFile()){
				mobileFile = new File (canonicalRoot + "/index.html");
			}
			fileInfo = mobileFile;
		}

	    if ( !fileInfo.isFile() ) 
	    {
		    outputError(404,  "Not Found");
			fileInfo = null;
			return;
	    }
		// fileName exists as a file
		else if (!fileName.equals(canonicalRoot)) {
			lastModifiedDate = new Date(fileInfo.lastModified());
		}
		
		// file is an executable
		if (fileInfo.canExecute()){
			fileIsExecutable = true;
			executableName = fileInfo.getAbsolutePath();
		}
		
    } // end mapURL2file


    private void outputResponseHeader() throws Exception 
    {
		
		if (!fileIsExecutable && !isModifiedSinceDate.before(lastModifiedDate)){
			statusCode = 304;
		}
		
		String statusMsg = returnStatusMessage(statusCode);

		// HTTP/1.1 Header
	    outToClient.writeBytes("HTTP/1.0 " + statusCode.toString() + statusMsg + "\r\n");
		
		// Date Header
		Date current = new Date();
		Instant now = current.toInstant();
		Date dateNow = Date.from(now);
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		outToClient.writeBytes("Date: " + sdf.format(dateNow) + "\r\n");

		// Server Header
		outToClient.writeBytes("Server: " + hostName + "\r\n");

		// Last-Modified Header
		if (!fileIsExecutable){
			outToClient.writeBytes("Last-Modified: " + sdf.format(lastModifiedDate) + "\r\n");
		}
		
		// Content-Type Header
		if (urlName.endsWith(".jpg"))
	        outToClient.writeBytes("Content-Type: image/jpeg\r\n");
	    else if (urlName.endsWith(".gif"))
	        outToClient.writeBytes("Content-Type: image/gif\r\n");
	    else if (urlName.endsWith(".html") || urlName.endsWith(".htm"))
	        outToClient.writeBytes("Content-Type: text/html\r\n");
	    else if (!fileIsExecutable){
	        outToClient.writeBytes("Content-Type: text/plain\r\n");
		}

		// Content Length Header
		if (!fileIsExecutable){ // use Transfer-Encoding header for executables
			outToClient.writeBytes("Content-Length: " + String.valueOf((int)fileInfo.length()) + "\r\n");
			outToClient.writeBytes("\r\n");
		}
    }

    private void outputResponseBody() throws Exception 
    {	
		if (!fileIsExecutable){
			int numOfBytes = (int) fileInfo.length();
			// send file content
			FileInputStream fileStream = new FileInputStream (fileName);
			byte[] fileInBytes = new byte[numOfBytes];

			if (!serverCache.containsKey(fileName)){
				fileStream.read(fileInBytes);
				if ((serverCache.size() + numOfBytes) < maxServerCacheSize) {
					serverCache.put(fileName, fileInBytes);
				}
			}
			else {
				fileInBytes = serverCache.get(fileName);
			}

			outToClient.write(fileInBytes, 0, numOfBytes);
			fileStream.close();
		} 
		else {

			ProcessBuilder pb = new ProcessBuilder(executableName);
			Map<String, String> env = pb.environment();
			pb.redirectInput(tempPostInputFileInfo);
			env.put("QUERY_STRING", queryString);
			env.put("REMOTE_ADDR", "127.0.0.1");
			env.put("REMOTE_HOST", "127.0.0.1"); // host name of client, but substituted with REMOTE_ADDR for now according to docs
			env.put("REMOTE_IDENT", "");
			env.put("REMOTE_USER", "");
			env.put("REQUEST_METHOD", requestMethod);
			env.put("SERVER_NAME", configMap.get(hostName));
			env.put("SERVER_PORT", configMap.get("Port"));
			env.put("SERVER_PROTOCOL", "HTTP/1.1");
			env.put("SERVER_SOFTWARE", "Java/" + System.getProperty("java.version"));
			Process p = pb.start();
			BufferedInputStream exeResponse = new BufferedInputStream(p.getInputStream());
			
			// Printing out Content-Type Header from CGI Script
			char charOfContentType = (char)exeResponse.read();
			while (charOfContentType != '\n'){
				outToClient.writeChar(charOfContentType);
				charOfContentType = (char)exeResponse.read();
			}
			outToClient.writeBytes("\r\n\r\n");

			// Content of CGI Output
			byte[] chunkedBuf = new byte[1024];
			int bytesChunked = 0;
			while ((bytesChunked = exeResponse.read(chunkedBuf, 0, 1024)) != -1){
				outToClient.writeBytes(String.valueOf(bytesChunked) + "\\r\\n");
				outToClient.writeBytes("\r\n");
				outToClient.write(chunkedBuf, 0, bytesChunked);
				outToClient.writeBytes("\r\n");
			}

		}
    }

    void outputError(int errCode, String errMsg)
    {
	    try {
	        outToClient.writeBytes("HTTP/1.0 " + errCode + " " + errMsg + "\r\n");
	    } catch (Exception e) {}
    }

    static void DEBUG(String s) 
    {
       if (_DEBUG)
          System.out.println( s );
    }

	String returnStatusMessage(Integer code){
		// will add more codes in next iteration
		String msg = "Invalid code";
		if (code == 200){
			msg = " OK";
		}
		else if (code == 304){
			msg = " Not Modified";
		}
		return msg;
	}
}
