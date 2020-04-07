package emcshop.model;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

import emcshop.chat.ChatMessage;
import emcshop.db.PaymentTransactionDb;

public interface IChatLogViewerModel {
	File getLogDirectory();

	void setLogDirectory(File dir);

	PaymentTransactionDb getPaymentTransaction();

	List<ChatMessage> getChatMessages(LocalDate date);

	String getCurrentPlayer();
}
