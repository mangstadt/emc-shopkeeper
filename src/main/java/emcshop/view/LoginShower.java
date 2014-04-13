package emcshop.view;

import java.awt.Window;

import emcshop.model.ILoginModel;
import emcshop.model.LoginModelImpl;
import emcshop.presenter.LoginPresenter;

public class LoginShower {
	public LoginPresenter show(Window owner) {
		ILoginView view = new LoginViewImpl(owner);
		ILoginModel model = new LoginModelImpl();
		return new LoginPresenter(view, model);
	}
}
