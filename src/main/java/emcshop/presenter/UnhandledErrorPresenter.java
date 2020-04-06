package emcshop.presenter;

import java.awt.Window;

import emcshop.model.IUnhandledErrorModel;
import emcshop.model.UnhandledErrorModelImpl;
import emcshop.view.IUnhandledErrorView;
import emcshop.view.UnhandledErrorViewImpl;

public class UnhandledErrorPresenter {
	private final IUnhandledErrorView view;
	private final IUnhandledErrorModel model;

	public UnhandledErrorPresenter(IUnhandledErrorView view, IUnhandledErrorModel model) {
		this.view = view;
		this.model = model;

		view.addSendErrorReportListener(event -> onSendErrorReport());
		view.addCloseListener(event -> onClose());

		view.setMessage(model.getMessage());
		view.setThrown(model.getThrown());
		model.logError();
		view.display();
	}

	private void onSendErrorReport() {
		model.sendErrorReport();
		view.errorReportSent();
	}

	private void onClose() {
		view.close();
	}

	public static void show(Window owner, String message, Throwable thrown) {
		IUnhandledErrorView view = new UnhandledErrorViewImpl(owner);
		IUnhandledErrorModel model = new UnhandledErrorModelImpl(message, thrown);
		new UnhandledErrorPresenter(view, model);
	}
}
