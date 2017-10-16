package emcshop.view;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml3;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import net.miginfocom.swing.MigLayout;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.chat.ChatMessage;
import emcshop.db.PaymentTransactionDb;
import emcshop.gui.MyJScrollPane;
import emcshop.util.GuiUtils;
import emcshop.util.Listeners;

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
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent arg0) {
                messages.setChatMessages(chatMessages, paymentTransaction);

                messages.requestFocusInWindow();
                if (paymentTransaction != null && !messages.foundPaymentTransaction()) {
                    JOptionPane.showMessageDialog(ChatLogViewerViewImpl.this, "Payment transaction not found in chat log.");
                }
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
        nextDay.setToolTipText("Next day");
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
        messages.setText("<center>Loading...</center>");

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
    public void setPaymentTransaction(PaymentTransactionDb paymentTransaction) {
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
        private final DateFormat df = new SimpleDateFormat("HH:mm:ss");
        private final Map<String, String> channelColors = new HashMap<String, String>();

        {
            channelColors.put("T", "green");
            channelColors.put("L", "#cccc00");
            channelColors.put("S", "#00cccc");
            channelColors.put("R", "blue");
            channelColors.put("G", "#009999");
            channelColors.put("E", "#00cc00");
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

                Date date = chatMessage.getDate();

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
                    highlight = (diff < 1000 * 60 && paymentTransaction.getAmount() == paymentTransactionAmount && paymentTransaction.getPlayer().equalsIgnoreCase(paymentTransactionPlayer));
                } else {
                    highlight = false;
                }

                if (highlight) {
                    foundPaymentTransaction = true;
                    sb.append("<span style=\"background-color:yellow\">");
                }

                //grey out hidden messages
                boolean hide = (!isChat && !isPaymentTransaction);
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
                String text = getDocumentText();

                Calendar c = Calendar.getInstance();
                c.setTime(datePicker.getDate());

                Matcher m = paymentTransactionRegex.matcher(text);
                while (m.find()) {
                    c.set(Calendar.HOUR, Integer.parseInt(m.group(1)));
                    c.set(Calendar.MINUTE, Integer.parseInt(m.group(2)));
                    c.set(Calendar.SECOND, Integer.parseInt(m.group(3)));
                    Date messageDate = c.getTime();
                    long diff = Math.abs(paymentTransaction.getTs().getTime() - messageDate.getTime());
                    if (diff < 1000 * 60) {
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

    private class FilterPanel extends JPanel {
        private final JLabel searchLabel;
        private final JTextField search;

        private final List<SearchListener> searchListeners = new ArrayList<SearchListener>();

        public FilterPanel() {
            searchLabel = new JLabel("<html><font size=2>Highlight:");

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
