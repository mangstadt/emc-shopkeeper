package emcshop.gui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.Main;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.JNumberTextField;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class FirstUpdateDialog extends JDialog implements WindowListener {
	private static final int DEFAULT_STOP_PAGE = 5000;
	private static final int DEFAULT_PAYMENT_TRANS_AGE = 7;

	private JButton beginButton, cancel;

	private JNumberTextField stopAt;
	private JLabel estimate;
	private JCheckBox stopAtCheckBox;

	private JNumberTextField paymentTransactionAge;
	private JCheckBox paymentTransactionAgeCheckbox;
	private JLabel paymentTransactionAgeLabel;

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
				cancelled = true;
				dispose();
			}
		});

		beginButton = new JButton("Begin");
		beginButton.addActionListener(new ActionListener() {
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
				cancelled = true;
				dispose();
			}
		});

		estimate = new JLabel() {
			String text;

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

		JLabel warningIcon = new JLabel(ImageManager.getWarningIcon());

		//@formatter:off
		JLabel text = new JLabel(
		"<html><div width=600>" +
		"<b><u><center>This is the first time you are running an update!!</center></u></b><br>" +
		"<center>To ensure accurate results, it is recommended that you <u><b>set move perms to false</b></u> on your res for the duration of this update:</center><br><br>" +
		"<center><b><font size=5><code>/res set move false</code></font></b></center><br>" +
		"<center>Also note, that the higher a transaction page number is, the longer it takes to load (for example, page 2000 takes much longer to load than page 20).  The updater is configured to stop at a certain page so that this first update doesn't take too long, but you may change this value if you wish (below)." +
		"</div></html>");
		//@formatter:on

		setLayout(new MigLayout());

		add(warningIcon, "span 1 4");
		add(text, "align center, wrap");

		JPanel p = new JPanel(new MigLayout());
		p.add(stopAtCheckBox);
		p.add(stopAt, "w 75");
		p.add(estimate);
		add(p, "align center, wrap");

		p = new JPanel(new MigLayout());
		p.add(paymentTransactionAgeCheckbox);
		p.add(paymentTransactionAge, "w 50");
		p.add(paymentTransactionAgeLabel);
		add(p, "align center, wrap");

		p = new JPanel(new FlowLayout());
		p.add(beginButton);
		p.add(cancel);
		add(p, "align center");

		pack();
		setLocationRelativeTo(owner);
	}

	private String calculateEstimateDisplay(int pages) {
		long totalMs = Main.estimateUpdateTime(pages);
		return DurationFormatUtils.formatDuration(totalMs, "HH:mm:ss", true);
	}

	////////////////////////////////////////////////

	@Override
	public void windowActivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		cancelled = true;
		dispose();
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		//do nothing
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
