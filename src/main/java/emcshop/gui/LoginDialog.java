package emcshop.gui;

import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;
import emcshop.EmcSession;
import emcshop.gui.images.ImageManager;

/**
 * Dialog for logging the user into EMC.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class LoginDialog extends JDialog {
	private static final Logger logger = Logger.getLogger(LoginDialog.class.getName());
	private JButton login;
	private JButton cancel;
	private JTextField username;
	private JPasswordField password;
	private JLabel loading;
	private JLabel error;
	private JPanel messagePanel;
	private EmcSession session;
	private JCheckBox rememberMe;

	public LoginDialog(final Window owner) {
		super(owner, "Login");
		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		createWidgets();
		layoutWidgets();
		setResizable(false);

		//cancel when escape is pressed
		getRootPane().registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onClickCancel();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		//login when enter is pressed
		getRootPane().registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onClickLogin();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Shows the login dialog.
	 * @param owner the owner window
	 * @param rememberMe if the "remember me" checkbox should be checked
	 * @return the user's EMC session and the state of the "remember me"
	 * checkbox
	 */
	public static Result show(Window owner, boolean rememberMe) {
		return show(owner, rememberMe, null);
	}

	/**
	 * Shows the login dialog.
	 * @param owner the owner window
	 * @param rememberMe if the "remember me" checkbox should be checked
	 * @param username the player's username or null if there isn't one
	 * @return the user's EMC session and the state of the "remember me"
	 * checkbox
	 */
	public static Result show(Window owner, boolean rememberMe, String username) {
		LoginDialog dialog = new LoginDialog(owner);
		dialog.rememberMe.setSelected(rememberMe);
		if (username != null) {
			dialog.username.setText(username);
			dialog.password.requestFocusInWindow();
		}
		dialog.setVisible(true);
		return new Result(dialog.session, dialog.rememberMe.isSelected());
	}

	private void createWidgets() {
		login = new JButton("Login");
		login.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onClickLogin();
			}
		});

		cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				onClickCancel();
			}
		});

		messagePanel = new JPanel();
		messagePanel.add(new JLabel(" "));
		username = new JTextField();
		password = new JPasswordField();
		loading = new JLabel("Logging in...", ImageManager.getLoadingSmall(), SwingConstants.CENTER);
		error = new JLabel() {
			@Override
			public void setText(String text) {
				super.setText("<html><font color=red>" + text + "</font></html>");
			}
		};

		rememberMe = new JCheckBox("Remember me");
	}

	private void layoutWidgets() {
		setLayout(new MigLayout());

		add(new JLabel("<html><center><b>Please enter your </b><code>empireminecraft.com</code><b><br>login credentials.</b></center></html>"), "align center, wrap");

		add(messagePanel, "align center, wrap");

		JPanel p = new JPanel(new MigLayout("insets 0"));
		p.add(new JLabel("Username:"), "align right");
		p.add(username, "w 150, wrap");
		p.add(new JLabel("Password:"), "align right");
		p.add(password, "w 150, wrap");
		add(p, "align center, wrap");

		add(rememberMe, "align center, wrap");

		p = new JPanel(new FlowLayout());
		p.add(login);
		p.add(cancel);
		add(p, "align center");
	}

	private void onClickCancel() {
		dispose();
	}

	private void onClickLogin() {
		messagePanel.removeAll();
		messagePanel.add(loading);
		login.setEnabled(false);
		username.setEnabled(false);
		password.setEnabled(false);
		rememberMe.setEnabled(false);
		validate();
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					session = EmcSession.login(username.getText(), new String(password.getPassword()), rememberMe.isSelected());
					if (session == null) {
						error.setText("Invalid login credentials.");
						messagePanel.add(error);
					} else if (isVisible()) { //if the user has cancelled
						dispose();
					}
				} catch (IOException e) {
					logger.log(Level.SEVERE, "An error occurred while logging the user into EMC.", e);
					error.setText("Error contacting EMC.");
					messagePanel.add(error);
				} finally {
					login.setEnabled(true);
					messagePanel.remove(loading);
					username.setEnabled(true);
					password.setEnabled(true);
					rememberMe.setEnabled(true);
					validate();
				}
			}
		};
		t.start();
	}

	public static class Result {
		private final EmcSession session;
		private final boolean rememberMe;

		public Result(EmcSession session, boolean rememberMe) {
			this.session = session;
			this.rememberMe = rememberMe;
		}

		/**
		 * Gets the session that was created.
		 * @return the session or null if the use didn't login
		 */
		public EmcSession getSession() {
			return session;
		}

		/**
		 * Whether to persist the session or not.
		 * @return true to persist the session, false not to
		 */
		public boolean isRememberMe() {
			return rememberMe;
		}
	}
}