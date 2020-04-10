package emcshop.presenter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import emcshop.db.PaymentTransactionDb;
import emcshop.model.IChatLogViewerModel;
import emcshop.view.IChatLogViewerView;

public class ChatLogViewerPresenter {
	private final IChatLogViewerView view;
	private final IChatLogViewerModel model;

	public ChatLogViewerPresenter(IChatLogViewerView view, IChatLogViewerModel model) {
		this.view = view;
		this.model = model;

		view.addDateChangedListener(event -> onDateChanged());
		view.addLogDirectoryChanged(event -> onLogDirectoryChanged());
		view.addCloseListener(event -> onClose());

		view.setLogDirectory(model.getLogDirectory());
		view.setCurrentPlayer(model.getCurrentPlayer());

		PaymentTransactionDb paymentTransaction = model.getPaymentTransaction();
		view.setPaymentTransaction(paymentTransaction);

		LocalDate dateToDisplay = (paymentTransaction == null) ? LocalDate.now() : paymentTransaction.getTs().toLocalDate();
		view.setDate(dateToDisplay);
		view.setChatMessages(model.getChatMessages(dateToDisplay));

		view.display();
	}

	private void onDateChanged() {
		LocalDate date = view.getDate();
		view.setChatMessages(model.getChatMessages(date));
	}

	private void onLogDirectoryChanged() {
		Path dir = view.getLogDirectory();
		if (!Files.exists(dir)) {
			view.showError("The specified log directory does not exist.");
			return;
		}
		if (!Files.isDirectory(dir)) {
			view.showError("The specified path is not a directory.");
			return;
		}

		model.setLogDirectory(dir);
		onDateChanged();
	}

	private void onClose() {
		view.close();
	}
}
