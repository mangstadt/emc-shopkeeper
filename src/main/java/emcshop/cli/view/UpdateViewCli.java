package emcshop.cli.view;

import java.awt.event.ActionListener;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Date;

import org.apache.commons.lang3.exception.ExceptionUtils;

import emcshop.presenter.LoginPresenter;
import emcshop.scraper.EmcSession;
import emcshop.view.IUpdateView;

public class UpdateViewCli implements IUpdateView {
	private final PrintStream out = System.out;
	private final NumberFormat nf = NumberFormat.getInstance();
	private final LoginShower loginShower;
	private int pages, shopTransactions, paymentTransactions, bonusFeeTransactions;

	public UpdateViewCli(LoginShower loginShower) {
		this.loginShower = loginShower;
	}

	@Override
	public EmcSession getNewSession() {
		LoginPresenter presenter = loginShower.show();
		return presenter.getSession();
	}

	@Override
	public boolean showDownloadError(Throwable thrown) {
		out.println("Error downloading transactions.  Download will be canceled.");
		out.println(ExceptionUtils.getStackTrace(thrown));
		return false;
	}

	@Override
	public void addCancelListener(ActionListener listener) {
		//empty
	}

	@Override
	public void addStopListener(ActionListener listener) {
		//empty
	}

	@Override
	public boolean getShowResults() {
		return false;
	}

	@Override
	public void setFirstUpdate(boolean firstUpdate) {
		//empty
	}

	@Override
	public void setEstimatedTime(Long estimatedTime) {
		//empty
	}

	@Override
	public void setStopAtPage(Integer stopAtPage) {
		//empty
	}

	@Override
	public void setOldestParsedTransactonDate(Date date) {
		//empty
	}

	@Override
	public void setPages(int pages) {
		this.pages = pages;
		updateDisplay();
	}

	@Override
	public void setShopTransactions(int count) {
		this.shopTransactions = count;
		updateDisplay();
	}

	@Override
	public void setPaymentTransactions(int count) {
		this.paymentTransactions = count;
		updateDisplay();
	}

	@Override
	public void setBonusFeeTransactions(int count) {
		this.bonusFeeTransactions = count;
		updateDisplay();
	}

	private void updateDisplay() {
		int transactions = shopTransactions + paymentTransactions + bonusFeeTransactions;
		out.print("\rPages: " + nf.format(pages) + " | Transactions: " + nf.format(transactions));
	}

	@Override
	public void reset() {
		//empty
	}

	@Override
	public void display() {
		//empty
	}

	@Override
	public void close() {
		//empty
	}
}
