package emcshop.cli.view;

import emcshop.model.ILoginModel;
import emcshop.model.LoginModelImpl;
import emcshop.presenter.LoginPresenter;
import emcshop.view.ILoginView;

public class LoginShower {
	public LoginPresenter show() {
		ILoginView view = new LoginViewCli();
		ILoginModel model = new LoginModelImpl();
		return new LoginPresenter(view, model);
	}
}
