package tech.jaboc.chatprogram;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Random;

public class Main {
	public static final int DEFAULTPORT = 1273;
	public static int userID;
	public static String username = "Username";
	public static boolean acceptIncomingConnections = true;
	public static int port = 1273;
	
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | UnsupportedLookAndFeelException | IllegalAccessException | InstantiationException e) {
			e.printStackTrace();
		}
		
		StartupFrame sf = new StartupFrame();
		
		JFrame f = new JFrame("Jaboc Secure Chat");
		f.setContentPane(sf.panelMain);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.pack();
		f.setVisible(true);
		
		sf.launchButton.addActionListener(actionEvent -> {
			String username = sf.usernameTextField.getText();
			boolean acceptIncoming = sf.acceptIncomingConnections.isSelected();
			int port = -1;
			StringBuilder errors = new StringBuilder();
			try {
				port = Integer.parseInt(sf.portTextField.getText());
			} catch (NumberFormatException e) {
				errors.append("\nPort must be a number");
			}
			if (port >= 65535 || port <= 1023) {
				errors.append("\nPort must be between 1023 and 65535");
			}
			if (username == null || username.isEmpty()) {
				errors.append("\nUsername must not be empty");
			} else if (username.length() > 120) {
				errors.append("\nUsername must be shorter than 120 characters");
			}
			if (errors.length() != 0) {
				errors.insert(0, "The following errors are present:");
				JOptionPane.showMessageDialog(f, errors, "Errors present", JOptionPane.ERROR_MESSAGE);
			} else {
				userID = new Random().nextInt(Integer.MAX_VALUE);
				
				Main.username = username;
				acceptIncomingConnections = acceptIncoming;
				Main.port = port;
				
				if (acceptIncomingConnections) {
					ServerSocketThread sst = new ServerSocketThread(port);
					sst.startServer();
				} else {
					ConnectionHandler conn = new ConnectionHandler(null, false);
					conn.start();
				}
				f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				f.setVisible(false);
				f.dispose();
			}
		});
	}
	
	/**
	 * Returns the exception's stack trace as a String
	 *
	 * @param e The Exception to output
	 * @return The exception's stack trace
	 */
	public static String getExceptionStackTrace(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString();
	}
}
