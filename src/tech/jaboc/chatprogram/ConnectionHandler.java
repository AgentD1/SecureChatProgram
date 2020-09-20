package tech.jaboc.chatprogram;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

import java.io.*;
import java.net.*;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConnectionHandler extends Thread {
	Socket socket;
	MainFrame frameBound;
	
	boolean isConnectionIncoming;
	boolean isOriginal = false;
	
	PrintWriter out;
	BufferedReader in;
	
	DataOutputStream dataOut;
	DataInputStream dataIn;
	
	StyledDocument doc;
	
	public static int windowCount = 0;
	
	private SecretKeySpec aesKey;
	private AlgorithmParameters aesParams;
	
	public ConnectionHandler(Socket me, boolean incomingConnection) {
		socket = me;
		isConnectionIncoming = incomingConnection;
		if (windowCount == 0) {
			isOriginal = true;
		}
	}
	
	/**
	 * Runs the Connection handler with the parameters set earlier
	 */
	@Override
	public void run() {
		windowCount++;
		
		if (socket != null) {
			try {
				// Create all the necessary streams
				out = new PrintWriter(socket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				dataOut = new DataOutputStream(socket.getOutputStream());
				dataIn = new DataInputStream(socket.getInputStream());
			} catch (IOException e) {
				return;
			}
		}
		
		// Create the JFrame
		JFrame f = new JFrame(Main.username + ": Not Connected");
		if (socket != null) {
			f.setTitle(Main.userID + ": " + socket.getInetAddress());
		}
		frameBound = new MainFrame();
		f.setContentPane(frameBound.panelMain);
		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		f.pack();
		f.setVisible(true);
		
		initStyledDocument();
		
		// Create a window listener to safely clean up the socket and decrement the windowCount variable when the JFrame is closed
		f.addWindowListener(new WindowListener() {
			@Override
			public void windowClosed(WindowEvent windowEvent) {
				if (isOriginal) {
					System.exit(0);
				}
				if (socket != null) {
					try {
						sendEncryptedMessage("1  ");
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				windowCount--;
				if (windowCount == 0) {
					System.exit(0);
				}
			}
			
			// Despite only using 1 function in WindowEvent, I have to override all of them.
			// region necessary garbage
			public void windowOpened(WindowEvent windowEvent) {
			}
			
			public void windowClosing(WindowEvent windowEvent) {
			}
			
			public void windowIconified(WindowEvent windowEvent) {
			}
			
			public void windowDeiconified(WindowEvent windowEvent) {
			}
			
			public void windowActivated(WindowEvent windowEvent) {
			}
			
			public void windowDeactivated(WindowEvent windowEvent) {
			}
			// endregion
		});
		
		// This action initiates a connection to the requested ip
		ActionListener userWantsToConnectEvent = actionEvent -> {
			Socket s;
			try {
				String requestedAddress = frameBound.ipField.getText();
				if (requestedAddress == null || requestedAddress.equals("")) {
					return;
				}
				if (requestedAddress.contains(":")) {
					int port = Integer.parseInt(requestedAddress.split(":")[1]);
					s = new Socket(requestedAddress.split(":")[0], port);
				} else {
					s = new Socket(requestedAddress, Main.DEFAULTPORT);
				}
				ConnectionHandler handler = new ConnectionHandler(s, true);
				handler.start();
			} catch (UnknownHostException e) {
				appendOtherMessage("\nCould not connect to the address " + frameBound.ipField.getText(), "system");
			} catch (IOException e) {
				tryOutputException(e);
			}
		};
		
		frameBound.connectButton.addActionListener(userWantsToConnectEvent);
		frameBound.ipField.addActionListener(userWantsToConnectEvent);
		
		if (socket == null) {
			frameBound.connectionStatus.setText("Not connected");
			// This is as far as the main window goes.
			return;
		}
		
		// This action sends the contents of messageField as a message to the other client
		ActionListener userWantsToSendMessage = actionEvent -> {
			if (!socket.isClosed()) {
				try {
					sendEncryptedMessage("0" + frameBound.messageField.getText()); // The leading 0 means this is a message
				} catch (IOException e) {
					tryOutputException(e);
					return;
				}
				appendMessage(LocalDateTime.now(), false, frameBound.messageField.getText());
				frameBound.messageField.setText("");
			}
		};
		
		frameBound.sendButton.addActionListener(userWantsToSendMessage);
		frameBound.messageField.addActionListener(userWantsToSendMessage);
		
		DoKeyExchange();
		
		frameBound.connectionStatus.setText("Connected to IP " + socket.getInetAddress());
		
		String otherUsername;
		try {
			// The initiator of the connection sends their username first
			if (isConnectionIncoming) {
				otherUsername = receiveEncryptedMessage();
				sendEncryptedMessage(Main.username);
			} else {
				sendEncryptedMessage(Main.username);
				otherUsername = receiveEncryptedMessage();
			}
		} catch (IOException e) {
			tryOutputException(e);
			return;
		}
		
		appendOtherMessage(otherUsername + " connected", "system");
		
		try {
			while (socket.isConnected() && !socket.isClosed()) {
				String inputString = receiveEncryptedMessage();
				if (inputString == null || inputString.startsWith("1")) { // Other user has disconnected
					appendOtherMessage("\n" + otherUsername + " disconnected", "system");
					socket.close();
					socket = null;
					return;
				} else {
					appendMessage(LocalDateTime.now(), true, inputString.substring(1));
				}
			}
		} catch (SocketException e) {
			socket = null;
			if (!e.getMessage().contains("closed")) { // Socket closed is expected when the other user disconnects, so only react if this exception isn't Socket closed
				tryOutputException(e);
			}
		} catch (IOException e) {
			tryOutputException(e);
			socket = null;
		}
	}
	
	// region Styled Document
	
	/**
	 * Initializes the doc variable with all the required text styles
	 */
	public void initStyledDocument() {
		doc = (StyledDocument) frameBound.chatMessagesGoHereTextPane.getDocument();
		
		Style incomingStyle = doc.addStyle("incoming", null);
		StyleConstants.setForeground(incomingStyle, Color.getHSBColor(0.5f, 1, 0.5f));
		
		Style outgoingStyle = doc.addStyle("outgoing", null);
		StyleConstants.setForeground(outgoingStyle, Color.getHSBColor(0, 1, 1));
		
		Style regularStyle = doc.addStyle("regular", null);
		StyleConstants.setForeground(regularStyle, Color.getHSBColor(0, 0, 0));
		
		Style systemStyle = doc.addStyle("system", null);
		StyleConstants.setForeground(systemStyle, Color.getHSBColor(0, 0, 0.5f));
	}
	
	/**
	 * Append a sent or received message to the styled document
	 *
	 * @param time     The time the message was sent or received
	 * @param incoming Whether this message was sent (false) or received (true)
	 * @param message  The message
	 */
	public void appendMessage(LocalDateTime time, boolean incoming, String message) {
		String timestamp = "[" + time.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ";
		appendOtherMessage("\n" + timestamp + (incoming ? "<< " : ">> "), incoming ? "incoming" : "outgoing");
		appendOtherMessage(message, "regular");
	}
	
	/**
	 * Append a message of any type to the styled document
	 *
	 * @param message The message to append
	 * @param style   The style to print the message with
	 */
	public void appendOtherMessage(String message, String style) {
		try {
			doc.insertString(doc.getLength(), message, doc.getStyle(style));
		} catch (BadLocationException e) {
			tryOutputException(e);
		}
	}
	
	// endregion
	
	// region Encryption
	
	/**
	 * Performs a Diffie Hellman key exchange with the connected client. Also initializes the AES cipher.
	 */
	void DoKeyExchange() {
		try {
			// Create a Diffie Hellman Public-Private key pair
			KeyPairGenerator ourKeygen = KeyPairGenerator.getInstance("DH");
			ourKeygen.initialize(2048);
			KeyPair ourKeyPair = ourKeygen.generateKeyPair();
			
			// Initialize the Diffie Hellman key agreement object
			KeyAgreement agreement = KeyAgreement.getInstance("DH");
			agreement.init(ourKeyPair.getPrivate());
			
			// The client who initiated the connection sends their public key first
			if (isConnectionIncoming) {
				sendMessage(ourKeyPair.getPublic().getEncoded());
			}
			
			// Receive and decode the other client's public key
			byte[] otherPubKeyBytes = receiveMessage();
			
			KeyFactory ourKeyFac = KeyFactory.getInstance("DH");
			X509EncodedKeySpec x509 = new X509EncodedKeySpec(otherPubKeyBytes);
			PublicKey otherPubKey = ourKeyFac.generatePublic(x509);
			
			// The client who received the connection sends their public key second
			if (!isConnectionIncoming) {
				sendMessage(ourKeyPair.getPublic().getEncoded());
			}
			
			// Generate the shared secret
			agreement.doPhase(otherPubKey, true);
			byte[] sharedSecret = agreement.generateSecret();
			
			// Generate the AES key from the shared secret
			aesKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");
			
			// The client that received the connection controls the cipher's parameters
			if (isConnectionIncoming) {
				// Create the AES cipher instance
				Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
				c.init(Cipher.ENCRYPT_MODE, aesKey);
				
				// Encode and send the cipher's parameters
				byte[] enc = c.getParameters().getEncoded();
				sendMessage(enc);
				
				aesParams = c.getParameters();
			} else {
				// Receive, decode, and store the other client's cipher parameters
				byte[] enc = receiveMessage();
				aesParams = AlgorithmParameters.getInstance("AES");
				aesParams.init(enc);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Encrypt a message into its raw byte form with the cipher
	 *
	 * @param message The message as a string
	 * @return The encrypted message as an array of bytes
	 */
	byte[] EncryptMessage(String message) {
		Cipher c;
		try {
			c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.ENCRYPT_MODE, aesKey, aesParams);
			return c.doFinal(message.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Decrypts a message from its raw byte form with the cipher
	 *
	 * @param message The encrypted message as an array of bytes
	 * @return The decrypted message as a string
	 */
	String DecryptMessage(byte[] message) {
		Cipher c;
		try {
			c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, aesKey, aesParams);
			return new String(c.doFinal(message));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// endregion
	
	// region Networking
	
	/**
	 * Encrypt a message then send it to the connected client
	 *
	 * @param message The message to send as a string
	 * @throws IOException Any exceptions with the socket during sending
	 */
	public void sendEncryptedMessage(String message) throws IOException {
		byte[] encryptedMessage = EncryptMessage(message);
		sendMessage(encryptedMessage);
	}
	
	/**
	 * Send a raw byte message to the connected client
	 *
	 * @param message The raw message
	 * @throws IOException Any exceptions with the socket during sending
	 */
	public void sendMessage(byte[] message) throws IOException {
		dataOut.writeInt(message.length);
		dataOut.write(message);
	}
	
	/**
	 * Synchronously receive a raw message from the connected client
	 *
	 * @return The raw message received
	 * @throws IOException Any exceptions with the socket while receiving
	 */
	public byte[] receiveMessage() throws IOException {
		int byteCount = dataIn.readInt();
		return dataIn.readNBytes(byteCount);
	}
	
	/**
	 * Synchronously receive and decrypt a message from the connected client
	 *
	 * @return The message received
	 * @throws IOException Any exceptions with the socket while receiving
	 */
	public String receiveEncryptedMessage() throws IOException {
		return DecryptMessage(receiveMessage());
	}
	
	// endregion
	
	/**
	 * Attempt to print the exception's stack trace to the styled document. Also prints the exception to the console.
	 *
	 * @param e The exception to print
	 */
	public void tryOutputException(Exception e) {
		e.printStackTrace();
		try {
			doc.insertString(doc.getLength(), "\nThe following error occurred:\n" + Main.getExceptionStackTrace(e), doc.getStyle("system"));
		} catch (BadLocationException ex) {
			ex.printStackTrace();
		}
	}
}
