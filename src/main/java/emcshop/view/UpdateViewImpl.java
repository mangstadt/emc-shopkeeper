package emcshop.view;

import static emcshop.util.GuiUtils.toolTipText;

import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.BevelBorder;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.gui.images.Images;
import emcshop.presenter.LoginPresenter;
import emcshop.scraper.EmcSession;
import emcshop.util.GuiUtils;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class UpdateViewImpl extends JDialog implements IUpdateView {
	private final NumberFormat nf = NumberFormat.getInstance();
	private final LoginShower loginShower;
	private final JButton cancel, stop;
	private final JLabel pagesLabel, shopTransactionsLabel, paymentTransactionsLabel, bonusFeeTransactionsLabel, timerLabel;
	private final JCheckBox display;
	private final List<ActionListener> reportErrorListeners = new ArrayList<>();

	private int pagesCount, shopTransactionsCount, paymentTransactionsCount, bonusFeeTransactionsCount;
	private LocalDateTime oldestTransactionDate;

	private Duration estimatedTime;
	private Integer stopAtPage;
	private TimerThread timer;

	public UpdateViewImpl(Window owner, LoginShower loginShower) {
		super(owner, "Updating Transactions");
		setModal(true);
		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);
		getRootPane().setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		setResizable(false);

		this.loginShower = loginShower;

		cancel = new JButton("Cancel");
		cancel.setToolTipText(toolTipText("Stops the update process and <b>discards</b> all transactions that were parsed."));
		GuiUtils.onEscapeKeyPress(this, cancel);

		stop = new JButton("Stop");
		stop.setToolTipText(toolTipText("Stops the update process and <b>saves</b> all transactions that were parsed."));

		pagesLabel = new JLabel();
		shopTransactionsLabel = new JLabel();
		paymentTransactionsLabel = new JLabel();
		bonusFeeTransactionsLabel = new JLabel();
		reset();

		timerLabel = new JLabel("...");
		display = new JCheckBox("Display transactions when finished");
		display.setSelected(true);

		/////////////

		setLayout(new MigLayout());

		add(new JLabel(Images.LOADING), "span 2, split 2, align center");
		add(new JLabel("<html><b>Updating...</b></html>"), "wrap");

		add(new JLabel("Pages:"));
		add(pagesLabel, "wrap");

		add(new JLabel("Transactions:"), "span 2, wrap");
		add(new JLabel("Shop:"), "gapleft 30");
		add(shopTransactionsLabel, "wrap");
		add(new JLabel("Payment:"), "gapleft 30");
		add(paymentTransactionsLabel, "wrap");
		add(new JLabel("Bonus/Fee:"), "gapleft 30");
		add(bonusFeeTransactionsLabel, "wrap");

		add(new JLabel("Time:"));
		add(timerLabel, "wrap");

		add(display, "span 2, align center, wrap");

		add(cancel, "span 2, split 2, align center");
		add(stop);

		pack();
		setSize(getWidth() + 20, getHeight());
		setLocationRelativeTo(owner);
	}

	@Override
	public void addCancelListener(ActionListener listener) {
		cancel.addActionListener(listener);
		GuiUtils.addCloseDialogListener(this, listener);
	}

	@Override
	public void addStopListener(ActionListener listener) {
		stop.addActionListener(listener);
	}

	@Override
	public void addReportErrorListener(ActionListener listener) {
		reportErrorListeners.add(listener);
	}

	@Override
	public boolean getShowResults() {
		return display.isSelected();
	}

	@Override
	public void setFirstUpdate(boolean firstUpdate) {
		if (!firstUpdate) {
			cancel.setToolTipText(null);

			remove(stop);
			validate();
			repaint();
		}
	}

	@Override
	public void setEstimatedTime(Duration estimatedTime) {
		this.estimatedTime = estimatedTime;
	}

	@Override
	public void setStopAtPage(Integer stopAtPage) {
		this.stopAtPage = stopAtPage;
	}

	@Override
	public void reset() {
		stopTimer();
		startTimer();

		setPages(0);
		setShopTransactions(0);
		setPaymentTransactions(0);
		setBonusFeeTransactions(0);
	}

	@Override
	public void setPages(int pages) {
		pagesCount = pages;

		String text = nf.format(pages);
		if (stopAtPage != null) {
			text += " / " + nf.format(stopAtPage);
		}

		this.pagesLabel.setText(text);
	}

	@Override
	public void setShopTransactions(int count) {
		shopTransactionsCount = count;
		shopTransactionsLabel.setText(nf.format(count));
	}

	@Override
	public void setPaymentTransactions(int count) {
		paymentTransactionsCount = count;
		paymentTransactionsLabel.setText(nf.format(count));
	}

	@Override
	public void setBonusFeeTransactions(int count) {
		bonusFeeTransactionsCount = count;
		bonusFeeTransactionsLabel.setText(nf.format(count));
	}

	@Override
	public void setOldestParsedTransactonDate(LocalDateTime date) {
		oldestTransactionDate = date;
	}

	@Override
	public EmcSession getNewSession() {
		stopTimer();
		LoginPresenter presenter = loginShower.show(this);
		return presenter.isCanceled() ? null : presenter.getSession();
	}

	@Override
	public boolean showDownloadError(Throwable thrown) {
		stopTimer();
		cancel.setEnabled(false);
		stop.setEnabled(false);

		UpdateErrorDialog dialog = new UpdateErrorDialog(thrown);
		dialog.setVisible(true);
		return dialog.saveTransactions;
	}

	@Override
	public void display() {
		startTimer();
		setVisible(true);
	}

	@Override
	public void close() {
		dispose();
	}

	private void stopTimer() {
		if (timer != null) {
			timer.interrupt();
		}
	}

	private void startTimer() {
		timer = new TimerThread();
		timer.setDaemon(true);
		timer.start();
	}

	private class TimerThread extends Thread {
		@Override
		public void run() {
			final String estimatedTimeDisplay = (estimatedTime == null) ? null : DurationFormatUtils.formatDuration(estimatedTime.getSeconds() * 1000, "HH:mm:ss", true);
			final Instant start = Instant.now();

			while (isDisplayable()) {
				Duration elapsed = Duration.between(start, Instant.now());
				String timerText = DurationFormatUtils.formatDuration(elapsed.getSeconds() * 1000, "HH:mm:ss", true);

				if (estimatedTimeDisplay != null) {
					timerText += " / " + estimatedTimeDisplay;
				}
				timerLabel.setText(timerText);

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

	private class UpdateErrorDialog extends JDialog {
		private boolean saveTransactions = false;

		private UpdateErrorDialog(final Throwable thrown) {
			super(UpdateViewImpl.this, "Error");
			setModal(true);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			GuiUtils.closeOnEscapeKeyPress(this);

			JTextArea displayText = new JTextArea("An unexpected error occurred while downloading your transactions.");
			displayText.setEditable(false);
			displayText.setBackground(getBackground());
			displayText.setLineWrap(true);
			displayText.setWrapStyleWord(true);

			JLabel errorIcon = new JLabel(Images.getErrorIcon());

			JTextArea stackTrace = new JTextArea(ExceptionUtils.getStackTrace(thrown));
			stackTrace.setEditable(false);
			stackTrace.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

			final JButton report = new JButton("Send Error Report");
			report.addActionListener(event -> {
				GuiUtils.fireEvents(reportErrorListeners);
				report.setEnabled(false);
				report.setText("Reported");
				JOptionPane.showMessageDialog(UpdateErrorDialog.this, "Error report sent.  Thanks!");
			});

			JButton save = new JButton("Save Transactions");
			save.addActionListener(event -> {
				saveTransactions = true;
				dispose();
			});

			JButton discard = new JButton("Discard transactions");
			discard.addActionListener(event -> dispose());

			setLayout(new MigLayout());
			add(errorIcon, "split 2");
			add(displayText, "w 100:100%:100%, gapleft 10, wrap");
			JScrollPane scroll = new JScrollPane(stackTrace);
			scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
			add(scroll, "grow, w 100%, h 100%, align center, wrap");
			add(report, "align right, wrap");

			int transactionsCount = shopTransactionsCount + paymentTransactionsCount + bonusFeeTransactionsCount;
			DateTimeFormatter df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
			//@formatter:off
			add(new JLabel(
			"<html>" +
				"<b>Do you want to save the transactions that have been parsed or try starting over from scratch?</b><br><br>" +
				"<table border=0>" +
					"<tr><td>Date of oldest transaction:</td><td>" + df.format(oldestTransactionDate) + "</td></tr>" +
					"<tr><td>Total transactions parsed:</td><td>" + transactionsCount + "</td></tr>" +
					"<tr><td>Total pages parsed:</td><td>" + pagesCount + "</td></tr>" +
				"</table>" +
			"</html>"), "align center, wrap");
			//@formatter:on

			add(save, "align center, split 2");
			add(discard);

			setSize(500, 500);
			setLocationRelativeTo(UpdateViewImpl.this);
		}
	}
}
