package emcshop.model;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.github.mangstadt.emc.net.EmcWebsiteConnection;
import com.github.mangstadt.emc.net.EmcWebsiteConnectionImpl;
import com.github.mangstadt.emc.net.InvalidCredentialsException;

import emcshop.AppContext;
import emcshop.Settings;
import emcshop.scraper.EmcSession;

public class LoginModelImpl implements ILoginModel {
	private static final Logger logger = Logger.getLogger(LoginModelImpl.class.getName());
	private static final AppContext context = AppContext.instance();

	private final Settings settings;

	public LoginModelImpl() {
		settings = context.get(Settings.class);
	}

	@Override
	public EmcSession login(String username, String password) throws IOException {
		EmcWebsiteConnection connection = null;
		try {
			connection = new EmcWebsiteConnectionImpl(username, password);
			return new EmcSession(username, password, connection.getCookieStore());
		} catch (InvalidCredentialsException e) {
			return null;
		} finally {
			IOUtils.closeQuietly(connection);
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
	}

	@Override
	public void logNetworkError(IOException error) {
		logger.log(Level.SEVERE, "An error occurred while logging the user into EMC.", error);
	}
}
