package emcshop.model;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import emcshop.AppContext;
import emcshop.Settings;
import emcshop.chat.ChatLogParser;
import emcshop.chat.ChatMessage;
import emcshop.db.PaymentTransactionDb;

public class ChatLogViewerModelImpl implements IChatLogViewerModel {
	private static final AppContext context = AppContext.instance();

	private final Settings settings = context.get(Settings.class);
	private final PaymentTransactionDb paymentTransaction;
	private ChatLogParser parser;

	public ChatLogViewerModelImpl() {
		this(null);
	}

	public ChatLogViewerModelImpl(PaymentTransactionDb paymentTransaction) {
		this.paymentTransaction = paymentTransaction;
		parser = new ChatLogParser(getLogDirectory());
	}

	@Override
	public Path getLogDirectory() {
		return settings.getChatLogDir();
	}

	@Override
	public void setLogDirectory(Path dir) {
		parser = new ChatLogParser(dir);
		settings.setChatLogDir(dir);
		settings.save();
	}

	@Override
	public PaymentTransactionDb getPaymentTransaction() {
		return paymentTransaction;
	}

	@Override
	public List<ChatMessage> getChatMessages(LocalDate date) {
		try {
			return parser.getLog(date);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getCurrentPlayer() {
		return settings.getUsername();
	}
}
