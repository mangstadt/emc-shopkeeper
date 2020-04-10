package emcshop.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import emcshop.chat.ChatMessage;
import emcshop.db.PaymentTransactionDb;

public interface IChatLogViewerModel {
	Path getLogDirectory();

	void setLogDirectory(Path dir);

	PaymentTransactionDb getPaymentTransaction();

	List<ChatMessage> getChatMessages(LocalDate date);

	String getCurrentPlayer();
}
