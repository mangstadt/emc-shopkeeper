package emcshop.cli.view;

import java.awt.event.ActionListener;
import java.io.Console;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import emcshop.util.GuiUtils;
import emcshop.view.ILoginView;

public class LoginViewCli implements ILoginView {
	private final PrintStream out = System.out;
	private final Console console = System.console();

	private String savedUsername, username, password;
	private boolean rememberMe;

	private final List<ActionListener> onLoginListeners = new ArrayList<ActionListener>();
	private final List<ActionListener> onCancelListeners = new ArrayList<ActionListener>();

	@Override
	public void addOnLoginListener(ActionListener listener) {
		onLoginListeners.add(listener);
	}

	@Override
	public void addOnCancelListener(ActionListener listener) {
		onCancelListeners.add(listener);
	}

	@Override
	public void setUsername(String username) {
		this.savedUsername = this.username = username;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public void setRememberMe(boolean rememberMe) {
		this.rememberMe = rememberMe;
	}

	@Override
	public boolean getRememberMe() {
		return rememberMe;
	}

	@Override
	public void networkError() {
		out.println("Network error.  Please try again.");
		prompt();
	}

	@Override
	public void badLogin() {
		out.println("Login failed.  Please try again.");
		prompt();
	}

	@Override
	public void display() {
		prompt();
	}

	@Override
	public void close() {
		//empty
	}

	private void prompt() {
		if (savedUsername == null) {
			username = console.readLine("Username: ");
		} else {
			username = console.readLine("Username [" + savedUsername + "]: ");
			if (username.isEmpty()) {
				username = savedUsername;
			}
		}
		password = new String(console.readPassword("Password: "));

		out.println("Logging in...");
		GuiUtils.fireEvents(onLoginListeners);
	}
}
