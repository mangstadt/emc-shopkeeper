package emcshop.presenter;

import java.io.IOException;

import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.net.TwoFactorAuthException;

import emcshop.model.ILoginModel;
import emcshop.scraper.EmcSession;
import emcshop.view.ILoginView;

public class LoginPresenter {
	private final ILoginView view;
	private final ILoginModel model;

	private boolean canceled;

	public LoginPresenter(ILoginView view, ILoginModel model) {
		this.view = view;
		this.model = model;

		view.addOnLoginListener(event -> onLogin());
		view.addOnCancelListener(event -> onCancel());

		String username = model.getSavedUsername();
		view.setUsername(username);
		String password = model.getSavedPassword();
		view.setPassword(password);

		view.display();
	}

	void onLogin() {
		String username = view.getUsername();
		String password = view.getPassword();
		String twoFactorAuthCode = view.getTwoFactorAuthCode();

		EmcSession session;
		try {
			session = model.login(username, password, twoFactorAuthCode);
		} catch (InvalidCredentialsException e) {
			view.badLogin();
			return;
		} catch (TwoFactorAuthException e) {
			if (twoFactorAuthCode == null) {
				view.twoFactorAuthCodeRequired();
			} else {
				view.badTwoFactorAuthCode();
			}
			return;
		} catch (IOException e) {
			model.logNetworkError(e);
			view.networkError();
			return;
		}

		boolean savePassword = view.getSavePassword();
		synchronized (this) {
			if (canceled) {
				return;
			}
			model.setSession(session);
			model.saveSessionInfo(username, savePassword ? password : null);
		}

		view.close();
	}

	void onCancel() {
		synchronized (this) {
			canceled = true;
		}
		view.close();
	}

	public synchronized boolean isCanceled() {
		return canceled;
	}

	public EmcSession getSession() {
		return model.getSession();
	}
}
