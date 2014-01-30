package emcshop.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.Main;
import emcshop.gui.lib.JNumberTextField;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class FirstUpdateDialog extends JDialog {
	private static final int DEFAULT_STOP_PAGE = 5000;
	private static final int DEFAULT_PAYMENT_TRANS_AGE = 7;

	private final JButton begin, cancel;

	private final JNumberTextField stopAt;
	private final JLabel estimate;
	private final JCheckBox stopAtCheckBox;

	private final JNumberTextField paymentTransactionAge;
	private final JCheckBox paymentTransactionAgeCheckbox;
	private final JLabel paymentTransactionAgeLabel;

	private boolean cancelled;

	public static Result show(JDialog owner) {
		FirstUpdateDialog dialog = new FirstUpdateDialog(owner);
		dialog.setVisible(true);

		Integer stopAtPage = null;
		Long estimatedTime = null;
		if (dialog.stopAtCheckBox.isSelected()) {
			stopAtPage = dialog.stopAt.getInteger();
			if (stopAtPage != null) {
				estimatedTime = Main.estimateUpdateTime(stopAtPage);
			}
		}

		Integer oldestPaymentTransactionDays = null;
		if (dialog.paymentTransactionAgeCheckbox.isSelected()) {
			oldestPaymentTransactionDays = dialog.paymentTransactionAge.getInteger();
		}

		return new Result(dialog.cancelled, stopAtPage, estimatedTime, oldestPaymentTransactionDays);
	}

	public FirstUpdateDialog(JDialog owner) {
		super(owner, "First Update", true);
		setResizable(false);
		GuiUtils.onEscapeKeyPress(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				cancel();
			}
		});

		begin = new JButton("Begin");
		begin.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				cancelled = false;
				dispose();
			}
		});

		cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				cancel();
			}
		});

		estimate = new JLabel() {
			private String text;

			@Override
			public void setText(String text) {
				this.text = text;

				StringBuilder sb = new StringBuilder();
				sb.append("<html>");
				if (!isEnabled()) {
					sb.append("<font color=\"#999999\">");
				}
				sb.append("Estimated time: <b><code>").append(text).append("</code></b>");
				if (!isEnabled()) {
					sb.append("</font>");
				}
				sb.append("</html>");
				super.setText(sb.toString());
			}

			@Override
			public void setEnabled(boolean enabled) {
				super.setEnabled(enabled);
				setText(text);
			}
		};
		estimate.setText(calculateEstimateDisplay(DEFAULT_STOP_PAGE));

		stopAt = new JNumberTextField();
		stopAt.setNumber(DEFAULT_STOP_PAGE);
		stopAt.addKeyListener(new KeyAdapter() {
			public void keyTyped(KeyEvent e) {
				char c = e.getKeyChar();
				if ((c < '0' || c > '9') && c != KeyEvent.VK_BACK_SPACE) {
					e.consume();
				}
			}

			@Override
			public void keyReleased(KeyEvent arg0) {
				Integer stopAtInt = stopAt.getInteger();
				if (stopAtInt == null) {
					estimate.setText("-");
				} else {
					estimate.setText(calculateEstimateDisplay(stopAtInt));
				}
			}
		});

		stopAtCheckBox = new JCheckBox("Stop at page:");
		stopAtCheckBox.setSelected(true);
		stopAtCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = stopAtCheckBox.isSelected();
				stopAt.setEnabled(selected);
				estimate.setEnabled(selected);
			}
		});

		paymentTransactionAgeCheckbox = new JCheckBox("Ignore payment transactions older than:");
		paymentTransactionAgeCheckbox.setSelected(true);
		paymentTransactionAgeCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = paymentTransactionAgeCheckbox.isSelected();
				paymentTransactionAge.setEnabled(selected);
				paymentTransactionAgeLabel.setEnabled(selected);
			}
		});

		paymentTransactionAge = new JNumberTextField();
		paymentTransactionAge.setNumber(DEFAULT_PAYMENT_TRANS_AGE);

		paymentTransactionAgeLabel = new JLabel("days");

		//@formatter:off
		JLabel text = new JLabel(
		"<html><div width=600>" +
		"<b><u><font color=red><center>This is the first time you are running an update!</center></font></u></b><br>" +
		"<center>To ensure accurate results, it is recommended that you <u><b>set move perms to false</b></u> on your res for the duration of this update:</center><br>" +
		"<center><b><font size=5><code>/res set move false</code></font></b></center><br>" +
		"</div></html>");
		//@formatter:on

		setLayout(new MigLayout());

		add(text, "align center, wrap");

		add(new JLabel("<html><b>Settings:</b></html>"), "wrap");

		add(new HelpLabel(null, "The higher a transaction page number is, the longer it takes for EMC to load the page.  For example, page 2000 takes much longer to load than page 20.<br><br>Therefore, it is recommended that you stop around page 5000, but you may change or disable this setting if you wish.  During the update operation, you can also click the \"Stop\" button, which will halt the update process and keep all transactions that were downloaded."), "split 4");
		add(stopAtCheckBox);
		add(stopAt, "w 75");
		add(estimate, "wrap");

		add(new HelpLabel(null, "This setting causes the updater to ignore old payment transactions, since it might be hard to remember what they were for.<br><br>A payment transaction occurs when a player gives rupees to another player using the <code>\"/r pay\"</code> command."), "split 4");
		add(paymentTransactionAgeCheckbox);
		add(paymentTransactionAge, "w 50");
		add(paymentTransactionAgeLabel, "wrap");

		add(begin, "split 2, align center");
		add(cancel);

		pack();
		setLocationRelativeTo(owner);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent arg0) {
				cancel();
			}
		});
	}

	private String calculateEstimateDisplay(int pages) {
		long totalMs = Main.estimateUpdateTime(pages);
		return DurationFormatUtils.formatDuration(totalMs, "HH:mm:ss", true);
	}

	private void cancel() {
		cancelled = true;
		dispose();
	}

	public static class Result {
		private final boolean cancelled;
		private final Integer stopAtPage;
		private final Long estimatedTime;
		private final Integer oldestPaymentTransactionDays;

		public Result(boolean cancelled, Integer stopAtPage, Long estimatedTime, Integer oldestPaymentTransactionDays) {
			this.cancelled = cancelled;
			this.stopAtPage = stopAtPage;
			this.estimatedTime = estimatedTime;
			this.oldestPaymentTransactionDays = oldestPaymentTransactionDays;
		}

		public boolean isCancelled() {
			return cancelled;
		}

		public Integer getStopAtPage() {
			return stopAtPage;
		}

		public Long getEstimatedTime() {
			return estimatedTime;
		}

		public Integer getOldestPaymentTransactionDays() {
			return oldestPaymentTransactionDays;
		}
	}
}
