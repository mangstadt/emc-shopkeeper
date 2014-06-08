package emcshop.view;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.gui.HelpLabel;
import emcshop.gui.lib.GroupPanel;
import emcshop.gui.lib.JNumberTextField;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class FirstUpdateViewImpl extends JDialog implements IFirstUpdateView {
	private final JButton begin, cancel;

	private final JNumberTextField stopAt;
	private final JLabel estimate;
	private final JCheckBox stopAtCheckBox;

	private final JNumberTextField paymentTransactionAge;
	private final JCheckBox paymentTransactionAgeCheckbox;
	private final JLabel paymentTransactionAgeLabel;

	private final List<ActionListener> onStopAtChanged = new ArrayList<ActionListener>();

	public FirstUpdateViewImpl(Window owner) {
		super(owner, "First Update", ModalityType.APPLICATION_MODAL);
		setResizable(false);

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
		stopAtCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = stopAtCheckBox.isSelected();
				stopAt.setEnabled(selected);
				estimate.setEnabled(selected);
			}
		});

		paymentTransactionAgeCheckbox = new JCheckBox("Ignore payment transactions older than:");
		paymentTransactionAgeCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = paymentTransactionAgeCheckbox.isSelected();
				paymentTransactionAge.setEnabled(selected);
				paymentTransactionAgeLabel.setEnabled(selected);
			}
		});

		paymentTransactionAge = new JNumberTextField();

		paymentTransactionAgeLabel = new JLabel("days");

		//@formatter:off
		JLabel text = new JLabel(
		"<html><div width=450>" +
		"<b><font color=#aa0000 size=4><center>This is the first time you are running an update!</center></font></b><br>" +
		"<center>To ensure accurate results, it is recommended that you <u><b>set move perms to false</b></u> on your res for the duration of this update:</center><br>" +
		"<center><b><font size=5><code>/res set move false</code></font></b></center>");
		//@formatter:on

		setLayout(new MigLayout());

		add(text, "align center, wrap");

		JPanel settings = new GroupPanel("Settings");

		settings.add(new HelpLabel(null, "The higher a transaction page number is, the longer it takes for the page to be downloaded.  For example, page 2000 takes longer to load than page 20.<br><br>Therefore, it is recommended that you stop around page 5000, but you may change or disable this setting if you wish.  During the update operation, you can also click the \"Stop\" button, which will halt the update process and keep all transactions that were downloaded."), "split 4");
		settings.add(stopAtCheckBox);
		settings.add(stopAt, "w 75");
		settings.add(estimate, "wrap");

		settings.add(new HelpLabel(null, "This setting causes the updater to ignore old payment transactions, since it might be hard to remember what they were for.<br><br>A payment transaction occurs when a player gives rupees to another player using the <code>\"/r pay\"</code> command."), "split 4");
		settings.add(paymentTransactionAgeCheckbox);
		settings.add(paymentTransactionAge, "w 50");
		settings.add(paymentTransactionAgeLabel, "wrap");

		add(settings, "growx, wrap");

		add(begin, "split 2, align center");
		add(cancel);

		pack();
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
	public Integer getMaxPaymentTransactionAge() {
		return paymentTransactionAgeCheckbox.isSelected() ? paymentTransactionAge.getInteger() : null;
	}

	@Override
	public void setMaxPaymentTransactionAge(Integer age) {
		paymentTransactionAge.setNumber(age);
		paymentTransactionAgeCheckbox.setSelected(age != null);
	}

	@Override
	public void setEstimatedTime(Long estimatedTime) {
		String text = (estimatedTime == null) ? "-" : DurationFormatUtils.formatDuration(estimatedTime, "HH:mm:ss", true);
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
