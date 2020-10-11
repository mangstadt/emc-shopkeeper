package emcshop.view;

import static emcshop.util.GuiUtils.shake;

import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import emcshop.gui.DialogBuilder;
import emcshop.gui.HelpLabel;
import emcshop.gui.images.Images;
import emcshop.gui.lib.GroupPanel;
import emcshop.util.GuiUtils;
import emcshop.util.Listeners;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class LoginViewImpl extends JDialog implements ILoginView {
	private final Listeners loginListeners = new Listeners();
	private final JButton login, cancel;
	private final JTextField username, twoFactorAuthCode;
	private final JPasswordField password;
	private final JCheckBox savePassword;
	private final JLabel loading;
	private final JPanel messagePanel;
	private String origUsername;

	public LoginViewImpl(Window owner) {
		super(owner, "Login");
		setModal(true);
		setResizable(false);

		cancel = new JButton("Cancel");
		GuiUtils.onEscapeKeyPress(this, cancel);

		messagePanel = new JPanel();
		username = new JTextField();
		password = new JPasswordField();
		savePassword = new JCheckBox("Save password");
		twoFactorAuthCode = new JTextField();
		loading = new JLabel("Logging in...", Images.LOADING_SMALL, SwingConstants.CENTER);

		login = new JButton("Login");
		login.addActionListener(event -> {
			if (origUsername != null && !origUsername.equalsIgnoreCase(username.getText())) {
				boolean cancel = !showUsernameChangedDialog();
				if (cancel) {
					return;
				}
			}

			messagePanel.removeAll();
			messagePanel.add(loading);

			login.setEnabled(false);
			username.setEnabled(false);
			password.setEnabled(false);
			savePassword.setEnabled(false);
			twoFactorAuthCode.setEnabled(false);

			pack();
			validate();

			Thread t = new Thread(loginListeners::fire);
			t.setDaemon(true);
			t.start();
		});
		GuiUtils.onKeyPress(this, KeyEvent.VK_ENTER, login);

		///////////////////////

		setLayout(new MigLayout());

		add(new JLabel(Images.EMC_LOGO), "align center, wrap");

		add(new HelpLabel("<html><b>Please enter your login credentials.", "Your login credentials to the EMC website are required in order to download your rupee transactions.  EMC Shopkeeper will not steal your password, I promise!"), "align center, wrap");

		add(messagePanel, "align center, wrap");

		JPanel p = new JPanel(new MigLayout("insets 0"));
		p.add(new JLabel("Username:"), "align right");
		p.add(username, "w 150, wrap");
		p.add(new JLabel("Password:"), "align right");
		p.add(password, "w 150, wrap");

		/*
		 * The pack() method does not take multi-line labels into account when calculating the window height.
		 * Separate the text into multiple label objects so that the window height is calculated correctly.
		 */
		JLabel l1 = new JLabel("If your account uses two-factor");
		l1.setHorizontalAlignment(SwingConstants.CENTER);
		GuiUtils.unboldFont(l1);
		JLabel l2 = new JLabel("authentication, enter the code below:");
		l2.setHorizontalAlignment(SwingConstants.CENTER);
		GuiUtils.unboldFont(l2);

		JPanel twoFactorAuthPanel = new GroupPanel("");
		twoFactorAuthPanel.add(l1, "w 100%, wrap");
		twoFactorAuthPanel.add(l2, "w 100%, wrap");
		twoFactorAuthPanel.add(new JLabel("2FA Code:"), "split 2, align right");
		twoFactorAuthPanel.add(twoFactorAuthCode, "w 150, align left, wrap");
		p.add(twoFactorAuthPanel, "span 2");

		add(p, "align center, wrap");

		add(savePassword, "split 2, align center");
		add(new HelpLabel("", "Selecting this option will save your password to the hard drive so that EMC Shopkeeper can auto-fill your password on this screen."), "wrap");

		add(login, "split 2, align center");
		add(cancel);

		pack();

		setLocationRelativeTo(owner);
	}

	@Override
	public void addOnLoginListener(ActionListener listener) {
		loginListeners.add(listener);
	}

	@Override
	public void addOnCancelListener(ActionListener listener) {
		cancel.addActionListener(listener);
		GuiUtils.addCloseDialogListener(this, listener);
	}

	@Override
	public void setUsername(String username) {
		origUsername = username;

		this.username.setText(username);
		if (username != null) {
			password.requestFocus();
		}
	}

	@Override
	public String getUsername() {
		return username.getText();
	}

	@Override
	public void setPassword(String password) {
		savePassword.setSelected(password != null);
		this.password.setText(password);
	}

	@Override
	public String getPassword() {
		return new String(password.getPassword());
	}

	@Override
	public String getTwoFactorAuthCode() {
		String code = twoFactorAuthCode.getText().trim();
		return code.isEmpty() ? null : code;
	}

	@Override
	public boolean getSavePassword() {
		return savePassword.isSelected();
	}

	@Override
	public void setSavePassword(boolean savePassword) {
		this.savePassword.setSelected(savePassword);
	}

	@Override
	public void networkError() {
		showError("Network error contacting EMC.");
	}

	@Override
	public void badLogin() {
		showError("Invalid login credentials.");
		password.requestFocus();
		password.selectAll();
	}

	@Override
	public void twoFactorAuthCodeRequired() {
		showError("2FA code is required.");
		twoFactorAuthCode.requestFocus();
	}

	@Override
	public void badTwoFactorAuthCode() {
		showError("2FA code is invalid.");
		twoFactorAuthCode.requestFocus();
		twoFactorAuthCode.selectAll();
	}

	private void showError(String text) {
		messagePanel.removeAll();

		JLabel error = new JLabel("<html><font color=red>" + text + "</font></html>");
		messagePanel.add(error);

		login.setEnabled(true);
		username.setEnabled(true);
		password.setEnabled(true);
		savePassword.setEnabled(true);
		twoFactorAuthCode.setEnabled(true);

		validate();
		shake(this);
	}

	@Override
	public void close() {
		dispose();
	}

	@Override
	public void display() {
		setVisible(true);
	}

	/**
	 * @return true to continue, false to cancel
	 */
	private boolean showUsernameChangedDialog() {
		int choice = DialogBuilder.warning() //@formatter:off
			.parent(this)
			.title("Warning")
			.text(
				"You have changed the username to something different!",
				"",
				"If your Minecraft username changed, you can safely continue.",
				"",
				"But if you're trying to download the transactions of an alt account, you should create a separate profile:",
				"1. Go to [Menu Button > Settings] and click \"Show Profiles on Startup\".",
				"2. Restart EMC Shopkeeper.",
				"3. At the \"Choose Profile\" dialog, type a name for your new profile in the textbox and click OK.",
				"4. EMC Shopkeeper will create a new profile and load into it. You can now safely download the transactions of your alt account on this new profile.")
			.buttons(JOptionPane.YES_NO_OPTION, "Continue", "*Cancel")
		.show(); //@formatter:on

		return choice == JOptionPane.YES_OPTION;
	}
}
