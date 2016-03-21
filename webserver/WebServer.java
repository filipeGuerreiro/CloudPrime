import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.* ;
import java.net.* ;
import java.util.* ;

public final class webserver {
	public static void main(String argv[]) throws Exception {
		// Set the port number.
		int port = 9999;

		// Establish the listen socket.
		ServerSocket welcomeSocket = new ServerSocket(port);

		// Process HTTP service requests in an infinite loop.
		while (true) {
			// Listen for a TCP connection request.
			Socket connectionSocket = welcomeSocket.accept();

			// Construct an object to process the HTTP request message.
			HttpRequest request = new HttpRequest( connectionSocket );

			// Create a new thread to process the request.
			Thread thread = new Thread(request);

			// Start the thread.
			thread.start();
		}
	}
}
