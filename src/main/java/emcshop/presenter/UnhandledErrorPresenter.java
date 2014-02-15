package emcshop.presenter;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import emcshop.model.UnhandledErrorModel;
import emcshop.model.UnhandledErrorModelImpl;
import emcshop.view.UnhandledErrorView;
import emcshop.view.UnhandledErrorViewImpl;

public class UnhandledErrorPresenter {
	private final UnhandledErrorView view;
	private final UnhandledErrorModel model;

	public UnhandledErrorPresenter(UnhandledErrorView view, UnhandledErrorModel model) {
		this.view = view;
		this.model = model;

		view.addSendErrorReportListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onSendErrorReport();
			}
		});

		view.addCloseListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onClose();
			}
		});

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

	/**
	 * Shows the error dialog.
	 * @param owner the owner window
	 * @param message the message
	 * @param thrown the thrown exception
	 */
	public static void show(Window owner, String message, Throwable thrown) {
		UnhandledErrorView view = new UnhandledErrorViewImpl(owner);
		UnhandledErrorModel model = new UnhandledErrorModelImpl(message, thrown);
		new UnhandledErrorPresenter(view, model);
	}
}
