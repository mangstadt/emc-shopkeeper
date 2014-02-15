package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Date;

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

		view.addOnLoginListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onLogin();
			}
		});

		view.addOnCancelListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onCancel();
			}
		});

		String username = model.getSavedUsername();
		view.setUsername(username);
		boolean rememberMe = model.getSavedRememberMe();
		view.setRememberMe(rememberMe);

		view.display();
	}

	private void onLogin() {
		Thread t = new Thread() {
			@Override
			public void run() {
				String username = view.getUsername();
				String password = view.getPassword();
				boolean rememberMe = view.getRememberMe();

				String token;
				try {
					token = model.login(username, password, rememberMe);
				} catch (IOException e) {
					model.logNetworkError(e);
					view.networkError();
					return;
				}

				if (token == null) {
					view.badLogin();
					return;
				}

				synchronized (LoginPresenter.this) {
					if (canceled) {
						return;
					}
					EmcSession session = new EmcSession(username, token, new Date());
					model.saveSession(session, rememberMe);
				}

				view.close();
			}
		};
		t.setDaemon(true);
		t.start();
	}

	private void onCancel() {
		synchronized (this) {
			canceled = true;
		}
		view.close();
	}

	public synchronized boolean isCanceled() {
		return canceled;
	}
}
