package emcshop.view;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml3;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.chat.ChatMessage;
import emcshop.gui.MyJScrollPane;
import emcshop.scraper.PaymentTransaction;
import emcshop.util.GuiUtils;
import emcshop.util.Listeners;

@SuppressWarnings("serial")
public class ChatLogViewerViewImpl extends JDialog implements IChatLogViewerView {
	private PaymentTransaction paymentTransaction;

	private final JTextField logDir;
	private final JButton loadLogDir;
	private final DatePicker datePicker;
	private final JEditorPane messages;

	private final Listeners closeListeners = new Listeners();

	public ChatLogViewerViewImpl(Window owner) {
		super(owner);
		setTitle("Chat Log Viewer");
		setModal(true);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		GuiUtils.addCloseDialogListener(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				closeListeners.fire();
			}
		});
		GuiUtils.onEscapeKeyPress(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				closeListeners.fire();
			}
		});

		//////////////////////////////////////////////////

		logDir = new JTextField();
		logDir.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				loadLogDir.doClick();
			}
		});

		loadLogDir = new JButton("Load");

		datePicker = new DatePicker();
		datePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		datePicker.setShowNoneButton(false);
		datePicker.setShowTodayButton(true);
		datePicker.setStripTime(true);

		messages = new JEditorPane();
		messages.setEditable(false);
		messages.setContentType("text/html");

		//////////////////////////////////////////////////

		setLayout(new MigLayout());

		add(logDir, "w 100%, split 2");
		add(loadLogDir, "wrap");

		add(new JLabel("Date:"), "split 2");
		add(datePicker, "wrap");

		MyJScrollPane scroll = new MyJScrollPane(messages);
		add(scroll, "grow, w 100%, h 100%");

		setSize(800, 500);
		setLocationRelativeTo(owner);
	}

	@Override
	public void addDateChangedListener(ActionListener listener) {
		datePicker.addActionListener(listener);
	}

	@Override
	public void addLogDirectoryChanged(ActionListener listener) {
		loadLogDir.addActionListener(listener);
	}

	@Override
	public void addCloseListener(ActionListener listener) {
		closeListeners.add(listener);
	}

	@Override
	public File getLogDirectory() {
		return new File(logDir.getText());
	}

	@Override
	public void setLogDirectory(File logDirectory) {
		String text = (logDirectory == null) ? "" : logDirectory.getAbsolutePath();
		logDir.setText(text);
	}

	@Override
	public void setPaymentTransaction(PaymentTransaction paymentTransaction) {
		this.paymentTransaction = paymentTransaction;
	}

	@Override
	public Date getDate() {
		return datePicker.getDate();
	}

	@Override
	public void setDate(Date date) {
		try {
			datePicker.setDate(date);
		} catch (PropertyVetoException e) {
			//ignore
		}
	}

	@Override
	public void setChatMessages(List<ChatMessage> chatMessages) {
		StringBuilder sb = new StringBuilder("<html><span style=\"font-family:monospace; font-size:14pt\">");
		DateFormat df = new SimpleDateFormat("HH:mm:ss");
		int caretPosition = 0;
		List<Integer> lineLengths = new ArrayList<Integer>(chatMessages.size());
		for (ChatMessage chatMessage : chatMessages) {
			Date date = chatMessage.getDate();
			String message = chatMessage.getMessage();
			int prevLength = sb.length();

			boolean highlight = (paymentTransaction != null && paymentTransaction.getTs().equals(date));

			//TODO http://stackoverflow.com/questions/9388264/jeditorpane-with-inline-image
			if (highlight) {
				//set caret position so the highlighted text is in the middle
				if (!lineLengths.isEmpty()) {
					caretPosition = sb.length();

					int from = lineLengths.size() - 1;
					int to = lineLengths.size() - 12;
					if (to < 0) {
						to = 0;
					}
					for (int i = from; i >= to; i--) {
						caretPosition -= lineLengths.get(i);
					}

					if (caretPosition < 0) {
						caretPosition = 0;
					}
				}

				sb.append("<span style=\"background-color:yellow\">");
			}

			sb.append('[').append(df.format(date)).append("] ");
			sb.append(escapeHtml3(message));

			if (highlight) {
				sb.append("</span>");
			}

			lineLengths.add(sb.length() - prevLength);

			sb.append("<br>");

		}

		messages.setText(sb.toString());
		messages.setCaretPosition(caretPosition);
	}

	@Override
	public void showError(String message) {
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
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
