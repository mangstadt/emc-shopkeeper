package emcshop.view;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

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
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class LoginViewImpl extends JDialog implements ILoginView {
	private final JButton login, cancel;
	private final JTextField username;
	private final JPasswordField password;
	private final JLabel loading;
	private final JPanel messagePanel;
	private final JCheckBox rememberMe;

	public LoginViewImpl(final Window owner) {
		super(owner, "Login");
		setModal(true);
		setResizable(false);

		login = new JButton("Login");
		login.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				messagePanel.removeAll();
				messagePanel.add(loading);
				login.setEnabled(false);
				username.setEnabled(false);
				password.setEnabled(false);
				rememberMe.setEnabled(false);
				validate();
			}
		});
		GuiUtils.onKeyPress(this, KeyEvent.VK_ENTER, login);

		cancel = new JButton("Cancel");
		GuiUtils.onEscapeKeyPress(this, cancel);

		messagePanel = new JPanel();
		messagePanel.add(new JLabel(" "));
		username = new JTextField();
		password = new JPasswordField();
		loading = new JLabel("Logging in...", ImageManager.getLoadingSmall(), SwingConstants.CENTER);
		rememberMe = new JCheckBox("Remember me");

		///////////////////////

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

		pack();
		setLocationRelativeTo(owner);
	}

	@Override
	public void addOnLoginListener(ActionListener listener) {
		login.addActionListener(listener);
	}

	@Override
	public void addOnCancelListener(final ActionListener listener) {
		cancel.addActionListener(listener);
		GuiUtils.addCloseDialogListener(this, listener);
	}

	@Override
	public void setUsername(String username) {
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
	public String getPassword() {
		return new String(password.getPassword());
	}

	@Override
	public boolean getRememberMe() {
		return rememberMe.isSelected();
	}

	@Override
	public void setRememberMe(boolean rememberMe) {
		this.rememberMe.setSelected(rememberMe);
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

	@Override
	public void close() {
		dispose();
	}

	@Override
	public void display() {
		setVisible(true);
	}
}
