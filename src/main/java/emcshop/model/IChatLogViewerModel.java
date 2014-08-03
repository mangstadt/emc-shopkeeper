package emcshop.model;

import java.io.File;
import java.util.Date;
import java.util.List;

import emcshop.chat.ChatMessage;
import emcshop.scraper.PaymentTransaction;

public interface IChatLogViewerModel {
	File getLogDirectory();

	void setLogDirectory(File dir);

	PaymentTransaction getPaymentTransaction();

	List<ChatMessage> getChatMessages(Date date);
}
