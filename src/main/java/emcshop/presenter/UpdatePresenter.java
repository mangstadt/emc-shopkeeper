package emcshop.presenter;

import java.util.Date;

import emcshop.model.IUpdateModel;
import emcshop.scraper.EmcSession;
import emcshop.view.IUpdateView;

public class UpdatePresenter {
	private final IUpdateView view;
	private final IUpdateModel model;

	private boolean canceled = false;

	public UpdatePresenter(IUpdateView view, IUpdateModel model) {
		this.view = view;
		this.model = model;

		view.addCancelListener(event -> onCancel());
		view.addStopListener(event -> onStop());
		view.addReportErrorListener(event -> onReportError());

		model.addPageDownloadedListener(event -> onPageDownloaded());
		model.addBadSessionListener(event -> onBadSession());
		model.addDownloadErrorListener(event -> onDownloadError());
		model.addDownloadCompleteListener(event -> onDownloadComplete());

		view.setFirstUpdate(model.isFirstUpdate());
		view.setEstimatedTime(model.getEstimatedTime());
		view.setStopAtPage(model.getStopAtPage());
		model.startDownload();
		view.display();
	}

	private void onBadSession() {
		EmcSession session = view.getNewSession();
		if (session == null) {
			view.close();
			canceled = true;
			return;
		}
		model.setSession(session);

		view.reset();
		model.startDownload();
	}

	private void onCancel() {
		model.stopDownload();
		model.discardTransactions();
		canceled = true;
		view.close();
	}

	private void onStop() {
		model.stopDownload();
		model.saveTransactions();
		view.close();
	}

	public void onReportError() {
		model.reportError();
	}

	private void onPageDownloaded() {
		view.setPages(model.getPagesDownloaded());
		view.setShopTransactions(model.getShopTransactionsDownloaded());
		view.setPaymentTransactions(model.getPaymentTransactionsDownloaded());
		view.setBonusFeeTransactions(model.getBonusFeeTransactionsDownloaded());
		view.setOldestParsedTransactonDate(model.getOldestParsedTransactionDate());
	}

	private void onDownloadError() {
		Throwable thrown = model.getDownloadError();
		boolean saveTransactions = view.showDownloadError(thrown);
		if (saveTransactions) {
			model.saveTransactions();
		} else {
			model.discardTransactions();
			canceled = true;
		}
		view.close();
	}

	private void onDownloadComplete() {
		model.saveTransactions();
		view.close();
	}

	public boolean isCanceled() {
		return canceled;
	}

	public int getPageCount() {
		return model.getPagesDownloaded();
	}

	public int getShopTransactions() {
		return model.getShopTransactionsDownloaded();
	}

	public int getPaymentTransactions() {
		return model.getPaymentTransactionsDownloaded();
	}

	public int getBonusFeeTransactions() {
		return model.getBonusFeeTransactionsDownloaded();
	}

	public Date getStarted() {
		return model.getStarted();
	}

	public long getTimeTaken() {
		return model.getTimeTaken();
	}

	public boolean getShowResults() {
		return view.getShowResults();
	}

	public Integer getRupeeBalance() {
		return model.getRupeeBalance();
	}
}
