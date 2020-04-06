package emcshop.presenter;

import emcshop.model.IDatabaseStartupErrorModel;
import emcshop.view.IDatabaseStartupErrorView;

public class DatabaseStartupErrorPresenter {
	private final IDatabaseStartupErrorView view;
	private final IDatabaseStartupErrorModel model;
	private boolean quit = false;

	public DatabaseStartupErrorPresenter(IDatabaseStartupErrorView view, IDatabaseStartupErrorModel model) {
		this.view = view;
		this.model = model;

		view.addSendErrorReportListener(event -> onSendErrorReport());
		view.addCloseListener(event -> onClose());
		view.addStartRestoreListener(event -> onStartRestore());
		model.addRestoreCompleteListener(event -> onRestoreComplete());

		view.setThrown(model.getThrown());
		view.setBackups(model.getBackups());
		model.logError();
		view.display();
	}

	private void onSendErrorReport() {
		model.sendErrorReport();
		view.errorReportSent();
	}

	private void onClose() {
		quit = true;
		view.close();
	}

	private void onStartRestore() {
		model.startRestore(view.getSelectedBackup());
	}

	private void onRestoreComplete() {
		quit = false;
		view.close();
	}

	public boolean getQuit() {
		return quit;
	}
}
