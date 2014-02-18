package emcshop.cli.view;

import emcshop.model.ILoginModel;
import emcshop.model.LoginModelImpl;
import emcshop.presenter.LoginPresenter;
import emcshop.util.Settings;
import emcshop.view.ILoginView;

public class LoginShower {
	private final Settings settings;

	public LoginShower(Settings settings) {
		this.settings = settings;
	}

	public LoginPresenter show() {
		ILoginView view = new LoginViewCli();
		ILoginModel model = new LoginModelImpl(settings);
		return new LoginPresenter(view, model);
	}
}
