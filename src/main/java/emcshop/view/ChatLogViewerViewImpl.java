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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
	private final FilterPanel filterPanel;
	private final ChatLogEditorPane messages;

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

		filterPanel = new FilterPanel();
		filterPanel.addSearchListener(new SearchListener() {
			@Override
			public void searchPerformed(String keyword) {
				messages.update();
			}
		});

		messages = new ChatLogEditorPane();
		messages.setEditable(false);

		//////////////////////////////////////////////////

		setLayout(new MigLayout());

		add(logDir, "w 100%, split 2");
		add(loadLogDir, "wrap");

		add(new JLabel("Date:"), "split 4");
		add(datePicker);
		add(prevDay);
		add(nextDay, "wrap");

		add(filterPanel, "wrap");

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
		messages.setChatMessages(chatMessages, paymentTransaction);
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

	private class ChatLogEditorPane extends JEditorPane {
		private final DateFormat df = new SimpleDateFormat("HH:mm:ss");
		private final Map<String, String> channelColors = new HashMap<String, String>();
		{
			channelColors.put("T", "green");
			channelColors.put("L", "#cccc00");
			channelColors.put("S", "#00cccc");
			channelColors.put("R", "blue");
			channelColors.put("G", "#009999");
		}
		private final Pattern chatRegex;
		{
			StringBuilder sb = new StringBuilder("^(");

			sb.append('[');
			for (String channel : channelColors.keySet()) {
				sb.append(channel);
			}
			sb.append(']');

			sb.append("|From|To) ");

			chatRegex = Pattern.compile(sb.toString());
		}
		private final Pattern gaveRupeesRegex = Pattern.compile("^You paid ([\\d,]+) rupees to (.*)");
		private final Pattern receivedRupeesRegex = Pattern.compile("^You just received ([\\d,]+) rupees from (.*)");

		private PaymentTransaction paymentTransaction;
		private List<ChatMessage> chatMessages = Collections.emptyList();
		private List<Integer> lineLengths;

		public ChatLogEditorPane() {
			setContentType("text/html");
		}

		public void setChatMessages(List<ChatMessage> chatMessages, PaymentTransaction paymentTransaction) {
			this.chatMessages = chatMessages;
			this.paymentTransaction = paymentTransaction;
			update();
		}

		public void update() {
			if (chatMessages.isEmpty()) {
				setText("<html><div align=\"center\"><b>No log entries for this date.");
				return;
			}

			int caretPosition = 0;
			int totalTextLength = 0;
			lineLengths = new ArrayList<Integer>(chatMessages.size());
			String search = filterPanel.search.getText().trim();
			search = escapeHtml3(search);

			StringBuilder sb = new StringBuilder("<html><span style=\"font-family:monospace; font-size:14pt\">");
			for (ChatMessage chatMessage : chatMessages) {
				String message = chatMessage.getMessage();
				Date date = chatMessage.getDate();

				String escapedMessage = escapeHtml3(message);

				//color payment transactions
				boolean isPaymentTransaction = false;
				String paymentTransactionPlayer = null;
				Matcher m = gaveRupeesRegex.matcher(escapedMessage);
				if (m.find()) {
					isPaymentTransaction = true;
					escapedMessage = escapedMessage.replace(m.group(1), "<span style=\"color:red\"><b>" + m.group(1) + "</b></span>");
					paymentTransactionPlayer = m.group(2);
				}
				m = receivedRupeesRegex.matcher(escapedMessage);
				if (m.find()) {
					isPaymentTransaction = true;
					escapedMessage = escapedMessage.replace(m.group(1), "<span style=\"color:green\"><b>" + m.group(1) + "</b></span>");
					paymentTransactionPlayer = m.group(2);
				}

				//make the current player's name red
				if (currentPlayer != null) {
					String player = escapeHtml3(currentPlayer);
					escapedMessage = escapedMessage.replace(player, "<span style=\"color:red\">" + player + "</span>");
				}

				//highlight the search term
				if (!search.isEmpty()) {
					escapedMessage = escapedMessage.replaceAll("(?i)(" + Pattern.quote(search) + ")", "<span style=\"background-color:yellow\">$1</span>");
				}

				//color the chat message
				boolean isChat = chatRegex.matcher(message).find();
				if (isChat) {
					if (escapedMessage.startsWith("To") || escapedMessage.startsWith("From")) {
						escapedMessage = "<span style=\"color:purple\">" + escapedMessage + "</span>";
					} else {
						char channel = escapedMessage.charAt(0);
						String color = channelColors.get(channel + "");
						if (color != null) {
							escapedMessage = "<span style=\"color:" + color + "\"><b>" + channel + "</b></span>" + escapedMessage.substring(1);
						}
					}
				}

				//highlight the selected payment transaction
				//TODO http://stackoverflow.com/questions/9388264/jeditorpane-with-inline-image
				boolean highlight;
				if (paymentTransaction != null && isPaymentTransaction) {
					long diff = Math.abs(paymentTransaction.getTs().getTime() - date.getTime());
					highlight = (diff < 1000 * 60 && paymentTransaction.getPlayer().equalsIgnoreCase(paymentTransactionPlayer));
				} else {
					highlight = false;
				}

				if (highlight) {
					sb.append("<span style=\"background-color:yellow\">");
				}

				//grey out hidden messages
				boolean hide = (!isChat && !isPaymentTransaction);
				if (hide) {
					sb.append("<span style=\"color:#cccccc\">");
				}

				sb.append('[').append(df.format(date)).append("] ");
				sb.append(escapedMessage);

				if (hide) {
					sb.append("</span>");
				}
				if (highlight) {
					sb.append("</span>");
				}

				sb.append("<br>");

				//set caret position so the highlighted text is in the middle
				if (highlight && !lineLengths.isEmpty()) {
					caretPosition = totalTextLength + 400;
					//caretPosition = sb.length();

					//					int from = lineLengths.size() - 1;
					//					int to = lineLengths.size() - 5;
					//					if (to < 0) {
					//						to = 0;
					//					}
					//					for (int i = from; i >= to; i--) {
					//						caretPosition -= lineLengths.get(i);
					//					}
					//
					//					if (caretPosition < 0) {
					//						caretPosition = 0;
					//					}
				}

				int lineLength = message.length() + 12;
				totalTextLength += lineLength;
				lineLengths.add(lineLength);
			}

			setText(sb.toString());
			setCaretPosition(caretPosition);
		}
	}

	private class FilterPanel extends JPanel {
		private final JLabel searchLabel;
		private final JTextField search;

		private final List<SearchListener> searchListeners = new ArrayList<SearchListener>();

		public FilterPanel() {
			searchLabel = new JLabel("<html><font size=2>Search:");

			search = new JTextField();
			search.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String keyword = search.getText();
					for (SearchListener listener : searchListeners) {
						listener.searchPerformed(keyword);
					}
				}
			});

			////////////////////////////////////////

			setLayout(new MigLayout("insets 0"));

			add(searchLabel);
			add(search, "w 150");
		}

		public void addSearchListener(SearchListener listener) {
			searchListeners.add(listener);
		}

		@Override
		public void setEnabled(boolean enabled) {
			super.setEnabled(enabled);
			search.setEnabled(enabled);
		}
	}

	private interface SearchListener {
		void searchPerformed(String keyword);
	}
}
