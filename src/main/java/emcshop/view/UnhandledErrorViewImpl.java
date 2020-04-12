package emcshop.view;

import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.lang3.exception.ExceptionUtils;

import emcshop.gui.DialogBuilder;
import emcshop.gui.MyJScrollPane;
import emcshop.gui.images.Images;
import emcshop.util.GuiUtils;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class UnhandledErrorViewImpl extends JDialog implements IUnhandledErrorView {
	private final JTextArea displayText, stackTrace;
	private final JLabel errorIcon;
	private final JButton close, report;

	public UnhandledErrorViewImpl(Window owner) {
		super(owner, "Error");
		setModalityType(ModalityType.DOCUMENT_MODAL); //go on top of all windows

		displayText = new JTextArea();
		displayText.setEditable(false);
		displayText.setBackground(getBackground());
		displayText.setLineWrap(true);
		displayText.setWrapStyleWord(true);

		//http://stackoverflow.com/questions/1196797/where-are-these-error-and-warning-icons-as-a-java-resource
		errorIcon = new JLabel(Images.getErrorIcon());

		stackTrace = new JTextArea();
		stackTrace.setEditable(false);
		stackTrace.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		close = new JButton("Close");
		GuiUtils.onEscapeKeyPress(this, close);

		report = new JButton("Send Error Report");

		/////////////////

		setLayout(new MigLayout());
		add(errorIcon, "split 2");
		add(displayText, "w 100:100%:100%, gapleft 10, wrap");
		JScrollPane scroll = new MyJScrollPane(stackTrace);
		add(scroll, "grow, w 100%, h 100%, align center, wrap");
		add(close, "split 2, align center");
		add(report);

		setSize(500, 300);
	}

	@Override
	public void setMessage(String message) {
		displayText.setText(message);
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
		close.addActionListener(listener);
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
