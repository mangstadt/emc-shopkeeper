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
import java.util.Calendar;
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
	private String currentPlayer;

	private final JTextField logDir;
	private final JButton loadLogDir, prevDay, nextDay;
	private final DatePicker datePicker;
	private final JEditorPane messages;

	private final Listeners dateChangedListeners = new Listeners();
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

		prevDay = new JButton("<");
		prevDay.setToolTipText("Previous day");
		prevDay.addActionListener(new ActionListener() {
			private final Calendar c = Calendar.getInstance();

			@Override
			public void actionPerformed(ActionEvent e) {
				c.setTime(datePicker.getDate());
				c.add(Calendar.DATE, -1);
				setDate(c.getTime());

				dateChangedListeners.fire();
			}
		});

		nextDay = new JButton(">");
		prevDay.setToolTipText("Next day");
		nextDay.addActionListener(new ActionListener() {
			private final Calendar c = Calendar.getInstance();

			@Override
			public void actionPerformed(ActionEvent e) {
				c.setTime(datePicker.getDate());
				c.add(Calendar.DATE, 1);
				setDate(c.getTime());

				dateChangedListeners.fire();
			}
		});

		messages = new JEditorPane();
		messages.setEditable(false);
		messages.setContentType("text/html");

		//////////////////////////////////////////////////

		setLayout(new MigLayout());

		add(logDir, "w 100%, split 2");
		add(loadLogDir, "wrap");

		add(new JLabel("Date:"), "split 4");
		add(datePicker);
		add(prevDay);
		add(nextDay, "wrap");

		MyJScrollPane scroll = new MyJScrollPane(messages);
		add(scroll, "grow, w 100%, h 100%");

		setSize(800, 500);
		setLocationRelativeTo(owner);
	}

	@Override
	public void addDateChangedListener(ActionListener listener) {
		dateChangedListeners.add(listener);
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
	public void setCurrentPlayer(String currentPlayer) {
		this.currentPlayer = currentPlayer;
	}

	@Override
	public void setChatMessages(List<ChatMessage> chatMessages) {
		StringBuilder sb = new StringBuilder("<html><span style=\"font-family:monospace; font-size:14pt\">");
		DateFormat df = new SimpleDateFormat("HH:mm:ss");
		int caretPosition = 0;
		int totalTextLength = 0;
		List<Integer> lineLengths = new ArrayList<Integer>(chatMessages.size());
		for (ChatMessage chatMessage : chatMessages) {
			Date date = chatMessage.getDate();
			String message = chatMessage.getMessage();

			boolean highlight = (paymentTransaction != null && paymentTransaction.getTs().equals(date));

			//TODO http://stackoverflow.com/questions/9388264/jeditorpane-with-inline-image
			if (highlight) {
				//set caret position so the highlighted text is in the middle
				if (!lineLengths.isEmpty()) {
					caretPosition = totalTextLength;

					int from = lineLengths.size() - 1;
					int to = lineLengths.size() - 5;
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

			String escapedMessage = escapeHtml3(message);
			if (currentPlayer != null) {
				escapedMessage = escapedMessage.replace(currentPlayer, "<span style=\"color:red\">" + currentPlayer + "</span>");
			}

			sb.append('[').append(df.format(date)).append("] ");
			sb.append(escapedMessage);

			if (highlight) {
				sb.append("</span>");
			}

			sb.append("<br>");

			int lineLength = message.length() + 12;
			totalTextLength += lineLength;
			lineLengths.add(lineLength);
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
