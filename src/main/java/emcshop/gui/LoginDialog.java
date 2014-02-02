package emcshop.gui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.images.ImageManager;
import emcshop.scraper.EmcSession;
import emcshop.util.GuiUtils;

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
	private JPanel messagePanel;
	private EmcSession session;
	private JCheckBox rememberMe;
	private boolean canceled = false;

	public LoginDialog(final Window owner) {
		super(owner, "Login");
		setModal(true);
		setResizable(false);

		//cancel when the window is closed
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				cancel();
			}
		});

		//cancel when escape is pressed
		GuiUtils.onEscapeKeyPress(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				cancel();
			}
		});

		//login when enter is pressed
		GuiUtils.onKeyPress(this, KeyEvent.VK_ENTER, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				login();
			}
		});

		createWidgets();
		layoutWidgets();
		pack();
		setLocationRelativeTo(owner);
	}

	private void createWidgets() {
		login = new JButton("Login");
		login.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				login();
			}
		});

		cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				canceled = true;
				dispose();
			}
		});

		messagePanel = new JPanel();
		messagePanel.add(new JLabel(" "));
		username = new JTextField();
		password = new JPasswordField();
		loading = new JLabel("Logging in...", ImageManager.getLoadingSmall(), SwingConstants.CENTER);
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

		add(login, "split 2, align center");
		add(cancel);
	}

	private void cancel() {
		canceled = true;
		dispose();
	}

	private void login() {
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
				} catch (IOException e) {
					logger.log(Level.SEVERE, "An error occurred while logging the user into EMC.", e);
					showError("Network error contacting EMC.");
					return;
				}

				if (session == null) {
					showError("Invalid login credentials.");
					password.requestFocus();
					password.selectAll();
					return;
				}

				dispose();
			}
		};
		t.setDaemon(true);
		t.start();
	}

	private void showError(String text) {
		JLabel error = new JLabel("<html><font color=red>" + text + "</font></html>");
		messagePanel.add(error);

		login.setEnabled(true);
		messagePanel.remove(loading);
		username.setEnabled(true);
		password.setEnabled(true);
		rememberMe.setEnabled(true);
		validate();
	}

	/**
	 * Shows the login dialog.
	 * @param owner the owner window
	 * @param rememberMe if the "remember me" checkbox should be checked
	 * @return the user's EMC session and the state of the "remember me"
	 * checkbox, or null if the user canceled the dialog
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
	 * checkbox, or null if the user canceled the dialog
	 */
	public static Result show(Window owner, boolean rememberMe, String username) {
		LoginDialog dialog = new LoginDialog(owner);
		dialog.rememberMe.setSelected(rememberMe);
		if (username != null) {
			dialog.username.setText(username);
			dialog.password.requestFocusInWindow();
		}
		dialog.setVisible(true);

		return dialog.canceled ? null : new Result(dialog.session, dialog.rememberMe.isSelected());
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