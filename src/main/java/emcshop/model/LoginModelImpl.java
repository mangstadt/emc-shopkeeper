package emcshop.model;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.mangstadt.emc.net.EmcWebsiteConnection;
import com.github.mangstadt.emc.net.EmcWebsiteConnectionImpl;
import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.net.TwoFactorAuthException;

import emcshop.AppContext;
import emcshop.Settings;
import emcshop.gui.ProfileLoader;
import emcshop.scraper.EmcSession;

public class LoginModelImpl implements ILoginModel {
	private static final Logger logger = Logger.getLogger(LoginModelImpl.class.getName());
	private static final AppContext context = AppContext.instance();

	private final Settings settings;

	public LoginModelImpl() {
		settings = context.get(Settings.class);
	}

	@Override
	public EmcSession login(String username, String password, String twoFactorAuthCode) throws InvalidCredentialsException, TwoFactorAuthException, IOException {
		try (EmcWebsiteConnection connection = new EmcWebsiteConnectionImpl(username, password, twoFactorAuthCode)) {
			return new EmcSession(username, password, connection.getCookieStore());
		}
	}

	@Override
	public String getSavedUsername() {
		return settings.getUsername();
	}

	@Override
	public String getSavedPassword() {
		return settings.getPassword();
	}

	@Override
	public void saveSessionInfo(String username, String password) {
		settings.setUsername(username);
		settings.setPassword(password);
		settings.save();
	}

	@Override
	public EmcSession getSession() {
		return context.get(EmcSession.class);
	}

	@Override
	public void setSession(EmcSession session) {
		context.set(session);

		ProfileLoader profileLoader = context.get(ProfileLoader.class);
		if (profileLoader != null) {
			//CLI does not have a profile loader
			profileLoader.clearPrivateProfilesFromCache();
		}
	}

	@Override
	public void logNetworkError(IOException error) {
		logger.log(Level.SEVERE, "An error occurred while logging the user into EMC.", error);
	}
}
