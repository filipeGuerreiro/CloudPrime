import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.* ;
import java.net.* ;
import java.util.* ;

public final class WebServer {
	public static void main(String argv[]) throws Exception {
		// Define o port number.
		int port = 8000;

		// Estabelecer o socket de escuta.
		ServerSocket welcomeSocket = new ServerSocket(port);

		// Processa solicitações de serviço HTTP processo em loop infinito.
		while (true) {
			// Espera por um pedido de conexão TCP.
			Socket connectionSocket = welcomeSocket.accept();

			// Construir um objeto para processar a mensagem de pedido HTTP.
			HttpRequest request = new HttpRequest( connectionSocket );

			// Criar uma nova thread para processar o pedido.
			Thread thread = new Thread(request);

			// Inicia a thread.
			thread.start();
		}
	}
}

final class HttpRequest implements Runnable {
	final static String CRLF = "\r\n";
	Socket socket;

	// Constructor
	public HttpRequest(Socket socket) throws Exception {
		this.socket = socket;
	}
 
	// Implementar o método run() da interface Runnable.
	public void run() {
		try {processRequest();}catch (Exception e) {System.out.println(e);}
	}
	
	private void processRequest() throws Exception {	
		// Obter uma referência para fluxos de entrada e de saída do socket.
		InputStream instream = socket.getInputStream();
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());

		// Configurar filtros fluxo de entrada.
		BufferedReader br = new BufferedReader(new InputStreamReader(instream));//reads the input data

		// Obter a linha de pedido da mensagem de solicitação HTTP.
		String requestLine = br.readLine();

		// Mostrar a linha de solicitação.
		System.out.println();
		System.out.println(requestLine);
		// Extrair o fileName a partir da requestLine.
		StringTokenizer tokens = new StringTokenizer(requestLine);
		tokens.nextToken();
		String fileName = tokens.nextToken();

		fileName = "." + fileName;

		//Abrir o arquivo solicitado.

		FileInputStream fis = null;
		boolean fileExists = true;
		try {
			fis = new FileInputStream(fileName);
		} catch (FileNotFoundException e) {
			fileExists = false;
		}

		//Construir a mensagem de resposta.
		String statusLine = null;
		String contentTypeLine = null;
		String entityBody = null;

		if (fileExists) {
			statusLine = "HTTP/1.0 200 OK" + CRLF; 
			contentTypeLine = "Content-type: " + contentType( fileName ) + CRLF;
		}
		else {
			statusLine = "HTTP/1.0 404 Not Found" + CRLF;
			contentTypeLine = "Content-type: " + "text/html" + CRLF;
			entityBody = "<HTML>" + "<HEAD><TITLE>Not Found</TITLE></HEAD>" + "<BODY>Not Found</BODY></HTML>";
		}


		//Enviar a linha de status.
		os.writeBytes(statusLine);

		//Enviar a linha de tipo de conteúdo.
		os.writeBytes(contentTypeLine);

		//Envia uma linha em branco para indicar o final das linhas de cabeçalho.
		os.writeBytes(CRLF);



		//Enviar o corpo da entidade.
		if (fileExists) {
			sendBytes(fis, os);
			os.writeBytes(statusLine);
			os.writeBytes(contentTypeLine);
			fis.close();
		} else {
			os.writeBytes(statusLine);
			os.writeBytes(entityBody);
			os.writeBytes(contentTypeLine);
		}


		System.out.println(fileName);
		// Obter e exibir as linhas de cabeçalho.
		String headerLine = null;
		while ((headerLine = br.readLine()).length() != 0) {
			System.out.println(headerLine);
		}
		
		
		
		// Fechar streams e sockets.
		os.close();
		br.close();
		socket.close();
	}
	
	
	
	//retornar os tipos de arquivos
	private static String contentType(String fileName) {
		if(fileName.endsWith(".htm") || fileName.endsWith(".html")) {
			return "text/html";
		}
		if(fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		if(fileName.endsWith(".gif")) {
			return "image/gif";
		}
		return "application/octet-stream";
	}
	
	
	//Configurar os fluxos de entrada e saída 
	private static void sendBytes(FileInputStream fis, OutputStream os) throws Exception {
		// Construir um buffer de um Kb para armazenar os bytes no caminho para o socket.
		byte[] buffer = new byte[1024];
		int bytes = 0;

		// Copiar arquivo para o fluxo de saída de soquete.
		while((bytes = fis.read(buffer)) != -1 ) {
			os.write(buffer, 0, bytes);
		}
	}
}

