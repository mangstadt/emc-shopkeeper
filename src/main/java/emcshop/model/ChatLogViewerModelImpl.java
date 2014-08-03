package emcshop.model;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import emcshop.AppContext;
import emcshop.Settings;
import emcshop.chat.ChatLogParser;
import emcshop.chat.ChatMessage;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;

public class ChatLogViewerModelImpl implements IChatLogViewerModel {
	private static final AppContext context = AppContext.instance();

	private final Settings settings = context.get(Settings.class);
	private final PaymentTransaction paymentTransaction;
	private ChatLogParser parser;

	public ChatLogViewerModelImpl(PaymentTransaction paymentTransaction) {
		this.paymentTransaction = paymentTransaction;
		parser = new ChatLogParser(getLogDirectory());
	}

	@Override
	public File getLogDirectory() {
		return settings.getChatLogDir();
	}

	@Override
	public void setLogDirectory(File dir) {
		parser = new ChatLogParser(dir);
		settings.setChatLogDir(dir);
		settings.save();
	}

	@Override
	public PaymentTransaction getPaymentTransaction() {
		return paymentTransaction;
	}

	@Override
	public List<ChatMessage> getChatMessages(Date date) {
		try {
			return parser.getLog(date);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getCurrentPlayer() {
		EmcSession session = settings.getSession();
		return (session == null) ? null : session.getUsername();
	}
}
