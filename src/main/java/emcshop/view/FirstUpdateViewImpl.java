package emcshop.view;

import static emcshop.util.GuiUtils.unboldFont;

import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.gui.lib.GroupPanel;
import emcshop.gui.lib.JNumberTextField;
import emcshop.util.GuiUtils;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class FirstUpdateViewImpl extends JDialog implements IFirstUpdateView {
	private final JButton begin, cancel;

	private final JNumberTextField stopAt;
	private final JLabel estimate;
	private final JCheckBox stopAtCheckBox;

	private final JNumberTextField paymentTransactionAge;
	private final JCheckBox paymentTransactionAgeCheckbox;
	private final JLabel paymentTransactionAgeLabel;

	private final List<ActionListener> onStopAtChanged = new ArrayList<>();

	public FirstUpdateViewImpl(Window owner) {
		super(owner, "First Update", ModalityType.APPLICATION_MODAL);

		/*
		 * Allow user to resize the window in case any of the controls are being
		 * cut off (see note above the setSize() method call).
		 */
		//setResizable(false);

		begin = new JButton("Begin");

		cancel = new JButton("Cancel");
		GuiUtils.onEscapeKeyPress(this, cancel);

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

		stopAt = new JNumberTextField();
		stopAt.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				char c = e.getKeyChar();
				if ((c < '0' || c > '9') && c != KeyEvent.VK_BACK_SPACE) {
					e.consume();
				}
			}

			@Override
			public void keyReleased(KeyEvent arg0) {
				for (ActionListener listener : onStopAtChanged) {
					listener.actionPerformed(null);
				}
			}
		});

		stopAtCheckBox = new JCheckBox("Stop at page:");
		stopAtCheckBox.addActionListener(event -> {
			boolean selected = stopAtCheckBox.isSelected();
			stopAt.setEnabled(selected);
			estimate.setEnabled(selected);
		});

		paymentTransactionAge = new JNumberTextField();
		paymentTransactionAgeLabel = new JLabel("days");

		paymentTransactionAgeCheckbox = new JCheckBox("Ignore reason-less payment transactions older than:");
		paymentTransactionAgeCheckbox.addActionListener(event -> {
			boolean selected = paymentTransactionAgeCheckbox.isSelected();
			paymentTransactionAge.setEnabled(selected);
			paymentTransactionAgeLabel.setEnabled(selected);
		});

		setLayout(new MigLayout());

		add(new JLabel("<html><b><font color=#aa0000 size=4>This is the first time you are running an update!</font></b>", SwingConstants.CENTER), "w 100%, wrap");
		add(new JLabel("<html><center>Review the settings below, then click \"Begin\" to start downloading your transactions."), "w 100%, wrap");

		JPanel stopAtPagePanel = new GroupPanel("Stop At Page");
		JLabel stopAtPageDescription = new JLabel("<html>The higher a transaction page number is, the longer it takes for the page to be downloaded.  For example, page 2000 takes longer to download than page 20. Therefore, it is recommended that you stop around page 5000, but you may change or disable this setting if you wish.  During the update operation, you can also click the \"Stop\" button, which will cause it to stop at whatever page it is on.");
		unboldFont(stopAtPageDescription);
		stopAtPagePanel.add(stopAtPageDescription, "wrap");
		stopAtPagePanel.add(stopAtCheckBox, "split 3");
		stopAtPagePanel.add(stopAt, "w 75");
		stopAtPagePanel.add(estimate);
		add(stopAtPagePanel, "w 450, wrap");

		JPanel paymentTransactionsPanel = new GroupPanel("Payment Transactions");
		JLabel paymentTransactionsLabel = new JLabel("<html>This setting causes the updater to ignore old payment transactions that don't have a reason associated with them, since it might be hard to remember what they were for. A payment transaction occurs when a player gives rupees to another player using the <code>\"/r pay\"</code> command. The command lets you assign an optional free-form note to the transaction, called a \"reason\", to help you remember what the payment was for.");
		unboldFont(paymentTransactionsLabel);
		paymentTransactionsPanel.add(paymentTransactionsLabel, "wrap");
		paymentTransactionsPanel.add(paymentTransactionAgeCheckbox, "split 3");
		paymentTransactionsPanel.add(paymentTransactionAge, "w 50");
		paymentTransactionsPanel.add(paymentTransactionAgeLabel);
		add(paymentTransactionsPanel, "w 450, wrap");

		add(begin, "split 2, align center");
		add(cancel);

		pack();

		/*
		 * Many of the labels wrap to multiple lines. The pack() method does not
		 * take this into account, so underestimates the height of the window.
		 */
		setSize(getWidth(), (int) (getHeight() * 1.7));

		setLocationRelativeTo(owner);
	}

	@Override
	public void addOnCancelListener(ActionListener listener) {
		cancel.addActionListener(listener);
		GuiUtils.addCloseDialogListener(this, listener);
	}

	@Override
	public void addOnBeginListener(ActionListener listener) {
		begin.addActionListener(listener);
	}

	@Override
	public void addStopAtPageChangedListener(ActionListener listener) {
		onStopAtChanged.add(listener);
	}

	@Override
	public Integer getStopAtPage() {
		return stopAtCheckBox.isSelected() ? stopAt.getInteger() : null;
	}

	@Override
	public void setStopAtPage(Integer stopAtPage) {
		stopAt.setNumber(stopAtPage);
		stopAtCheckBox.setSelected(stopAtPage != null);

		for (ActionListener listener : onStopAtChanged) {
			listener.actionPerformed(null);
		}
	}

	@Override
	public Duration getMaxPaymentTransactionAge() {
		return paymentTransactionAgeCheckbox.isSelected() ? Duration.ofDays(paymentTransactionAge.getInteger()) : null;
	}

	@Override
	public void setMaxPaymentTransactionAge(Duration age) {
		paymentTransactionAge.setNumber(age.toDays());
		paymentTransactionAgeCheckbox.setSelected(age != null);
	}

	@Override
	public void setEstimatedTime(Duration estimatedTime) {
		String text;
		if (estimatedTime == null) {
			text = "-";
		} else if (estimatedTime.toDays() > 0) {
			text = "over a day";
		} else {
			text = DurationFormatUtils.formatDuration(estimatedTime.toMillis(), "HH:mm:ss", true);
		}
		estimate.setText(text);
	}

	@Override
	public void display() {
		setVisible(true);
	}

	@Override
	public void close() {
		dispose();
	}
}
