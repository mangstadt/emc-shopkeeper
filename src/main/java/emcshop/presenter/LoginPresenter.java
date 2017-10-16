package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

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
        String password = model.getSavedPassword();
        view.setPassword(password);

        view.display();
    }

    void onLogin() {
        String username = view.getUsername();
        String password = view.getPassword();
        boolean savePassword = view.getSavePassword();

        EmcSession session;
        try {
            session = model.login(username, password);
        } catch (IOException e) {
            model.logNetworkError(e);
            view.networkError();
            return;
        }

        if (session == null) {
            view.badLogin();
            return;
        }

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
