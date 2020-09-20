package tech.jaboc.chatprogram;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerSocketThread extends Thread {
	ServerSocket socket;
	public boolean running = false;
	public int port;
	
	public ServerSocketThread(int myPort) {
		port = myPort;
	}
	
	/**
	 * Starts the server
	 */
	public void startServer() {
		if (!running) {
			running = true;
			start();
		}
	}
	
	/**
	 * Stops the server
	 */
	public void stopServer() {
		if (running) {
			running = false;
			interrupt();
		}
	}
	
	@Override
	public void run() {
		if (!running) {
			return;
		}
		
		try {
			socket = new ServerSocket();
			socket.bind(new InetSocketAddress(port));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		ConnectionHandler nullHandler = new ConnectionHandler(null, false);
		
		nullHandler.start();
		
		while (running) {
			try {
				Socket accepted = socket.accept();
				
				ConnectionHandler connectionHandler = new ConnectionHandler(accepted, false);
				
				connectionHandler.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
