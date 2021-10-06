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
import java.time.Instant;

class HTTPRequestHandler {

    static boolean _DEBUG = true;
    static int     reqCount = 0;

	static boolean isModifiedSinceFlag = false;
	static boolean isMobileUser = false;
	String isModifiedSinceString = "";
	Date isModifiedSinceDate;

	Date lastModifiedDate;

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
			if (isModifiedSinceFlag && isModifiedSinceDate.before(lastModifiedDate)){
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

	    // GET request
	    String[] request = requestMessageLine.split("\\s");
	    if (request.length < 2 || !request[0].equals("GET") || !request[2].equals("HTTP/1.1"))
	    {
		    outputError(500, "Bad request");
		    return;
	    }

	    // parse URL to retrieve file name
	    urlName = request[1];
	    if ( urlName.startsWith("/") == true )
	    	urlName  = urlName.substring(1);

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

		// Process OTHER Header Requests
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
					isMobileUser = true;
				}
			}

			// If-Modified-Since Request - ex. Wed, 21 Oct 2015 07:28:00 GMT
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

	    // map to file name
	    fileName = WWW_ROOT + urlName;
	    DEBUG("Map to File name: " + fileName);

	    fileInfo = new File(fileName);
	    if ( !fileInfo.isFile() ) 
	    {
		    outputError(404,  "Not Found");
		    fileInfo = null;
	    }
		// fileName exists as a file
		else {
			lastModifiedDate = new Date(fileInfo.lastModified());
		}

    } // end mapURL2file


    private void outputResponseHeader() throws Exception 
    {
		if (!isModifiedSinceDate.before(lastModifiedDate)){
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

		//outToClient.writeBytes("temp: " + sdf.format(isModifiedSinceDate) + "\r\n");

		// Server Header
		outToClient.writeBytes("Server: " + hostName + "\r\n");

		// Last-Modified Header
		outToClient.writeBytes("Last-Modified: " + sdf.format(lastModifiedDate) + "\r\n");

		// Content-Type Header
		if (urlName.endsWith(".jpg"))
	        outToClient.writeBytes("Content-Type: image/jpeg\r\n");
	    else if (urlName.endsWith(".gif"))
	        outToClient.writeBytes("Content-Type: image/gif\r\n");
	    else if (urlName.endsWith(".html") || urlName.endsWith(".htm"))
	        outToClient.writeBytes("Content-Type: text/html\r\n");
	    else
	        outToClient.writeBytes("Content-Type: text/plain\r\n");

		// Content Length Header
		outToClient.writeBytes("Content-Length: " + String.valueOf((int)fileInfo.length()) + "\r\n");
		outToClient.writeBytes("\r\n");
    }

    private void outputResponseBody() throws Exception 
    {	
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
