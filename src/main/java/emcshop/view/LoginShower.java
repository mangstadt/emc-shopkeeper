package emcshop.view;

import java.awt.Window;

import emcshop.model.ILoginModel;
import emcshop.model.LoginModelImpl;
import emcshop.presenter.LoginPresenter;
import emcshop.util.Settings;

public class LoginShower {
	private final Settings settings;

	public LoginShower(Settings settings) {
		this.settings = settings;
	}

	public LoginPresenter show(Window owner) {
		ILoginView view = new LoginViewImpl(owner);
		ILoginModel model = new LoginModelImpl(settings);
		return new LoginPresenter(view, model);
	}
}
