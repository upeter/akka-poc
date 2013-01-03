package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**

 */
public class WorkerRunnable implements Runnable {

	protected Socket clientSocket = null;

	protected volatile boolean mute = false;

	public WorkerRunnable(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}

	public synchronized void resume() {
		mute = false;
		notify();
	}

	public void close() {
		if(!clientSocket.isClosed()) {
			try {
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					clientSocket.getInputStream()));
			String inputLine;

			while ((inputLine = in.readLine()) != null) {
				System.out.println("processing line " + inputLine);
				while (mute) {
					try {
						wait();
					} catch (InterruptedException e) {
					}
				}

			}
		} catch (IOException e) {
			// report exception somewhere.
			e.printStackTrace();
		}
	}
}
