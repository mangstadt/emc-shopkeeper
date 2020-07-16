package emcshop.view;

import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import org.apache.commons.lang3.exception.ExceptionUtils;

import emcshop.gui.DialogBuilder;
import emcshop.gui.MyJScrollPane;
import emcshop.gui.images.Images;
import emcshop.util.GuiUtils;
import emcshop.util.RelativeDateFormat;
import emcshop.util.UIDefaultsWrapper;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class DatabaseStartupErrorViewImpl extends JDialog implements IDatabaseStartupErrorView {
	private final JTextArea displayText, stackTrace;
	private final JLabel errorIcon, restoreLoading;
	private final JButton quit, report, restore;
	private final JList<LocalDateTime> backups;
	private final List<ActionListener> restoreListeners = new ArrayList<>();

	public DatabaseStartupErrorViewImpl(Window owner) {
		super(owner, "Error");

		setModalityType(ModalityType.DOCUMENT_MODAL); //go on top of all windows
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		quit = new JButton("Quit");
		GuiUtils.onEscapeKeyPress(this, quit);

		GuiUtils.addCloseDialogListener(this, event -> {
			if (quit.isEnabled()) {
				quit.doClick();
			}
		});
		GuiUtils.onEscapeKeyPress(this, event -> {
			if (quit.isEnabled()) {
				quit.doClick();
			}
		});

		restoreLoading = new JLabel("Working...", Images.LOADING_SMALL, SwingConstants.LEFT);
		restoreLoading.setVisible(false);

		displayText = new JTextArea("An error occurred while starting up the database.");
		displayText.setEditable(false);
		displayText.setBackground(getBackground());
		displayText.setLineWrap(true);
		displayText.setWrapStyleWord(true);

		//http://stackoverflow.com/questions/1196797/where-are-these-error-and-warning-icons-as-a-java-resource
		errorIcon = new JLabel(Images.getErrorIcon());

		stackTrace = new JTextArea();
		stackTrace.setEditable(false);
		stackTrace.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		report = new JButton("Send Error Report");

		backups = new JList<>();
		backups.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		backups.setCellRenderer((list, date, index, selected, hasFocus) -> {
			RelativeDateFormat df = RelativeDateFormat.instance();
			JLabel label = new JLabel(df.format(date));
			UIDefaultsWrapper.assignListFormats(label, selected);
			return label;
		});

		restore = new JButton("Restore Selected Backup");
		restore.addActionListener(event -> {
			LocalDateTime selected = getSelectedBackup();
			if (selected == null) {
				return;
			}

			int result = DialogBuilder.info() //@formatter:off
				.parent(this)
				.title("Confirm Restore")
				.text("Are you sure you want to restore this backup?")
				.buttons(JOptionPane.YES_NO_OPTION)
			.show(); //@formatter:on

			if (result != JOptionPane.YES_OPTION) {
				return;
			}

			restoreLoading.setVisible(true);
			quit.setEnabled(false);
			restore.setEnabled(false);
			backups.setEnabled(false);
			report.setEnabled(false);

			GuiUtils.fireEvents(restoreListeners);
		});

		/////////////////

		setLayout(new MigLayout());
		add(errorIcon, "split 2");
		add(displayText, "w 100:100%:100%, gapleft 10, wrap");
		JScrollPane scroll = new MyJScrollPane(stackTrace);
		add(scroll, "grow, w 100%, h 100%, align center, wrap");
		add(report, "align right, wrap");

		add(new JSeparator(), "w 100%, wrap");

		add(new JLabel("<html><b>Backups:</b></html>"), "wrap");
		JScrollPane pane = new JScrollPane(backups);
		add(pane, "h 100!, w 100%, wrap");
		add(restoreLoading, "align center, wrap");

		add(restore, "split 2, align center");
		add(quit);

		setSize(500, 500);
	}

	@Override
	public void addStartRestoreListener(ActionListener listener) {
		restoreListeners.add(listener);
	}

	@Override
	public LocalDateTime getSelectedBackup() {
		return backups.getSelectedValue();
	}

	@Override
	public void setBackups(List<LocalDateTime> backups) {
		this.backups.setModel(new AbstractListModel<LocalDateTime>() {
			@Override
			public LocalDateTime getElementAt(int index) {
				return backups.get(index);
			}

			@Override
			public int getSize() {
				return backups.size();
			}
		});
	}

	@Override
	public void setThrown(Throwable thrown) {
		stackTrace.setText(ExceptionUtils.getStackTrace(thrown));
		stackTrace.setCaretPosition(0); //scroll to top
	}

	@Override
	public void addSendErrorReportListener(ActionListener listener) {
		report.addActionListener(listener);
	}

	@Override
	public void addCloseListener(ActionListener listener) {
		quit.addActionListener(listener);
		GuiUtils.addCloseDialogListener(this, listener);
	}

	@Override
	public void errorReportSent() {
		report.setEnabled(false);
		report.setText("Reported");

		DialogBuilder.info() //@formatter:off
			.parent(this)
			.text("Error report sent. Thanks!")
		.show(); //@formatter:on
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
