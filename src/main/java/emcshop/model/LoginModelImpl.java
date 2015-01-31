package emcshop.model;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import emcshop.AppContext;
import emcshop.Settings;
import emcshop.scraper.EmcSession;
import emcshop.util.LoginUtils;

public class LoginModelImpl implements ILoginModel {
	private static final Logger logger = Logger.getLogger(LoginModelImpl.class.getName());
	private static final AppContext context = AppContext.instance();

	private final Settings settings;

	public LoginModelImpl() {
		settings = context.get(Settings.class);
	}

	@Override
	public String login(String username, String password, boolean rememberMe) throws IOException {
		return LoginUtils.login(username, password, rememberMe);
	}

	@Override
	public String getSavedUsername() {
		EmcSession oldSession = settings.getSession();
		return (oldSession == null) ? null : oldSession.getUsername();
	}

	@Override
	public boolean getSavedRememberMe() {
		return settings.isPersistSession();
	}

	@Override
	public void saveSession(EmcSession session, boolean rememberMe) {
		settings.setSession(session);
		settings.setPersistSession(rememberMe);
		settings.save();
	}

	@Override
	public EmcSession getSession() {
		return settings.getSession();
	}

	@Override
	public void logNetworkError(IOException error) {
		logger.log(Level.SEVERE, "An error occurred while logging the user into EMC.", error);
	}
}