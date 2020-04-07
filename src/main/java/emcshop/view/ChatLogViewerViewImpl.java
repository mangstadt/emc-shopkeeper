package emcshop.view;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml3;

import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
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
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.chat.ChatMessage;
import emcshop.db.PaymentTransactionDb;
import emcshop.gui.MyJScrollPane;
import emcshop.util.GuiUtils;
import emcshop.util.Listeners;
import emcshop.util.TimeUtils;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class ChatLogViewerViewImpl extends JDialog implements IChatLogViewerView {
	private PaymentTransactionDb paymentTransaction;
	private String currentPlayer;

	private final JTextField logDir;
	private final JButton loadLogDir, prevDay, nextDay;
	private final DatePicker datePicker;
	private final FilterPanel filterPanel;
	private final ChatLogEditorPane messages;
	private List<ChatMessage> chatMessages;

	private final Listeners dateChangedListeners = new Listeners();
	private final Listeners closeListeners = new Listeners();

	public ChatLogViewerViewImpl(Window owner) {
		super(owner);
		setTitle("Chat Log Viewer");
		setModal(true);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		GuiUtils.addCloseDialogListener(this, event -> closeListeners.fire());
		GuiUtils.onEscapeKeyPress(this, event -> closeListeners.fire());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent event) {
				messages.setChatMessages(chatMessages, paymentTransaction);

				messages.requestFocusInWindow();
				if (paymentTransaction != null && !messages.foundPaymentTransaction()) {
					JOptionPane.showMessageDialog(ChatLogViewerViewImpl.this, "Payment transaction not found in chat log.");
				}
			}
		});

		//////////////////////////////////////////////////

		loadLogDir = new JButton("Reload");

		logDir = new JTextField();
		logDir.addActionListener(event -> loadLogDir.doClick());

		datePicker = new DatePicker();
		datePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		datePicker.setShowNoneButton(false);
		datePicker.setShowTodayButton(true);
		datePicker.setStripTime(true);

		prevDay = new JButton("<");
		prevDay.setToolTipText("Previous day");
		prevDay.addActionListener(event -> {
			LocalDate date = getDate();
			setDate(date.minusDays(1));

			dateChangedListeners.fire();
		});

		nextDay = new JButton(">");
		nextDay.setToolTipText("Next day");
		nextDay.addActionListener(event -> {
			LocalDate date = getDate();
			setDate(date.plusDays(1));

			dateChangedListeners.fire();
		});

		messages = new ChatLogEditorPane();
		messages.setEditable(false);
		messages.setText("<center>Loading...</center>");

		filterPanel = new FilterPanel();
		filterPanel.addSearchListener(keyword -> messages.update());

		//////////////////////////////////////////////////

		setLayout(new MigLayout());

		add(new JLabel("Minecraft Log Directory:"), "split 3");
		add(logDir, "w 100%");
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
	public void setPaymentTransaction(PaymentTransactionDb paymentTransaction) {
		this.paymentTransaction = paymentTransaction;
	}

	@Override
	public LocalDate getDate() {
		return TimeUtils.toLocalDate(datePicker.getDate());
	}

	@Override
	public void setDate(LocalDate date) {
		try {
			datePicker.setDate(TimeUtils.toDate(date));
		} catch (PropertyVetoException ignore) {
		}
	}

	@Override
	public void setCurrentPlayer(String currentPlayer) {
		this.currentPlayer = currentPlayer;
	}

	@Override
	public void setChatMessages(List<ChatMessage> chatMessages) {
		this.chatMessages = chatMessages;
		if (isVisible()) {
			messages.setChatMessages(chatMessages, paymentTransaction);
		}
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
		private final DateTimeFormatter df = DateTimeFormatter.ofPattern("HH:mm:ss");
		private final Pattern gaveRupeesRegex = Pattern.compile("^You paid ([\\d,]+) rupees to (.*)");
		private final Pattern receivedRupeesRegex = Pattern.compile("^You just received ([\\d,]+) rupees from (.*)");

		private PaymentTransactionDb paymentTransaction;
		private Pattern paymentTransactionRegex;
		private List<ChatMessage> chatMessages = Collections.emptyList();
		private boolean foundPaymentTransaction;

		public ChatLogEditorPane() {
			setContentType("text/html");
		}

		public void setChatMessages(List<ChatMessage> chatMessages, PaymentTransactionDb paymentTransaction) {
			this.chatMessages = chatMessages;
			setPaymentTransaction(paymentTransaction);
			update();
		}

		public void setPaymentTransaction(PaymentTransactionDb paymentTransaction) {
			this.paymentTransaction = paymentTransaction;

			if (paymentTransaction == null) {
				paymentTransactionRegex = null;
				return;
			}

			int amount = paymentTransaction.getAmount();
			String regex = "\\[(\\d\\d):(\\d\\d):(\\d\\d)\\] ";
			NumberFormat nf = NumberFormat.getInstance(Locale.US);
			if (amount < 0) {
				amount *= -1;
				regex += "You paid " + nf.format(amount) + " rupees to " + paymentTransaction.getPlayer();
			} else {
				regex += "You just received " + nf.format(amount) + " rupees from " + paymentTransaction.getPlayer();
			}
			paymentTransactionRegex = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		}

		public void update() {
			if (chatMessages.isEmpty()) {
				setText("<html><div align=\"center\"><b>No log entries for this date.");
				return;
			}

			String search = filterPanel.search.getText().trim();
			search = escapeHtml3(search);

			foundPaymentTransaction = false;
			StringBuilder sb = new StringBuilder("<html><span style=\"font-family:monospace; font-size:14pt\">");
			for (ChatMessage chatMessage : chatMessages) {
				String message = chatMessage.getMessage();
				message = message.trim();
				message = message.replaceAll("\\s{2,}", " ");

				LocalDateTime date = chatMessage.getDate();

				String escapedMessage = escapeHtml3(message);

				//color payment transactions
				boolean isPaymentTransaction = false;
				String paymentTransactionPlayer = null;
				Integer paymentTransactionAmount = null;
				Matcher m = gaveRupeesRegex.matcher(escapedMessage);
				if (m.find()) {
					isPaymentTransaction = true;
					paymentTransactionAmount = Integer.valueOf(m.group(1).replace(",", "")) * -1;
					escapedMessage = escapedMessage.replace(m.group(1), "<span style=\"color:red\"><b>" + m.group(1) + "</b></span>");
					paymentTransactionPlayer = m.group(2);
				}
				m = receivedRupeesRegex.matcher(escapedMessage);
				if (m.find()) {
					isPaymentTransaction = true;
					paymentTransactionAmount = Integer.valueOf(m.group(1).replace(",", ""));
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
				boolean isChatMessage = false;
				if (escapedMessage.startsWith("To ") || escapedMessage.startsWith("From ")) {
					escapedMessage = "<span style=\"color:purple\">" + escapedMessage + "</span>";
					isChatMessage = true;
				} else {
					int spacePos = escapedMessage.indexOf(' ');
					if (spacePos >= 0) {
						String channelCode = escapedMessage.substring(0, spacePos);
						ChatChannel channel = ChatChannel.find(channelCode);
						if (channel != null) {
							escapedMessage = "<span style=\"color:" + channel.getColor() + "\"><b>" + channelCode + "</b></span>" + escapedMessage.substring(spacePos);
							isChatMessage = true;
						}
					}
				}

				//highlight the selected payment transaction
				//TODO http://stackoverflow.com/questions/9388264/jeditorpane-with-inline-image
				boolean highlight;
				if (paymentTransaction != null && isPaymentTransaction) {
					LocalDateTime paymentTransactionTs = TimeUtils.toLocalDateTime(paymentTransaction.getTs());
					long minutesDifference = Math.abs(date.until(paymentTransactionTs, ChronoUnit.MINUTES));
					highlight = (minutesDifference < 1 && paymentTransaction.getAmount() == paymentTransactionAmount && paymentTransaction.getPlayer().equalsIgnoreCase(paymentTransactionPlayer));
				} else {
					highlight = false;
				}

				if (highlight) {
					foundPaymentTransaction = true;
					sb.append("<span style=\"background-color:yellow\">");
				}

				//grey out hidden messages
				boolean hide = (!isChatMessage && !isPaymentTransaction);
				if (hide) {
					sb.append("<span style=\"color:#cccccc\">");
				}

				String dateStr = "[" + df.format(date) + "] ";
				sb.append(escapeHtml3(dateStr)).append(escapedMessage);

				if (hide) {
					sb.append("</span>");
				}
				if (highlight) {
					sb.append("</span>");
				}

				sb.append("<br>");
			}

			setText(sb.toString());

			//search for the payment transaction in the "Document" object's text in order to find the caret position
			int caretPosition = 1;
			if (paymentTransaction != null) {
				LocalDateTime paymentTransactionTs = TimeUtils.toLocalDateTime(paymentTransaction.getTs());
				String text = getDocumentText();
				LocalDate date = getDate();

				Matcher m = paymentTransactionRegex.matcher(text);
				while (m.find()) {
					LocalTime time = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
					LocalDateTime messageDate = LocalDateTime.of(date, time);
					long minutesDifference = Math.abs(messageDate.until(paymentTransactionTs, ChronoUnit.MINUTES));
					if (minutesDifference < 1) {
						caretPosition = m.start();
						break;
					}
				}
			}
			setCaretPosition(caretPosition);
		}

		private String getDocumentText() {
			try {
				Document document = getDocument();
				return document.getText(0, document.getLength());
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}
		}

		public boolean foundPaymentTransaction() {
			return foundPaymentTransaction;
		}
	}

	private enum ChatChannel {
		/*
		 * Used to be called Town chat.
		 */
		COMMUNITY("[CT](-[1-9U])?", "green"),

		/*
		 * Used to be called Economy chat.
		 */
		MARKET("[EM](-[1-9U])?", "#00cc00"),

		/*
		 * Used to be labeled "S", but this is used by Server chat now.
		 */
		SUPPORTER("Sup(-[1-9U])?", "#00cccc"),

		LOCAL("L", "#cccc00"), //@formatter:off
		RESIDENCE("R", "blue"),
		GROUP("G", "#009999"),
		SERVER("S", "red"); //@formatter:on

		private final Predicate<String> regex;
		private final String color;

		private ChatChannel(String regex, String color) {
			this.regex = Pattern.compile("^(" + regex + ")$").asPredicate();
			this.color = color;
		}

		public String getColor() {
			return color;
		}

		public static ChatChannel find(String code) {
			return Arrays.stream(values()) //@formatter:off
				.filter(c -> c.regex.test(code))
			.findFirst().orElse(null); //@formatter:on
		}
	}

	private class FilterPanel extends JPanel {
		private final JLabel searchLabel;
		private final JTextField search;

		private final List<SearchListener> searchListeners = new ArrayList<>();

		public FilterPanel() {
			searchLabel = new JLabel("Search current log:");

			search = new JTextField();
			search.addActionListener(event -> {
				String keyword = search.getText();
				for (SearchListener listener : searchListeners) {
					listener.searchPerformed(keyword);
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
