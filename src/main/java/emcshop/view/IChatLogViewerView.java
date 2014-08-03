package emcshop.view;

import java.awt.event.ActionListener;
import java.io.File;
import java.util.Date;
import java.util.List;

import emcshop.chat.ChatMessage;
import emcshop.scraper.PaymentTransaction;

public interface IChatLogViewerView {
	void addDateChangedListener(ActionListener listener);

	void addLogDirectoryChanged(ActionListener listener);

	void addCloseListener(ActionListener listener);

	File getLogDirectory();

	void setLogDirectory(File logDirectory);

	void setPaymentTransaction(PaymentTransaction paymentTransaction);

	Date getDate();

	void setDate(Date dateToDisplay);

	void setChatMessages(List<ChatMessage> chatMessages);

	void setCurrentPlayer(String currentPlayer);

	void showError(String string);

	void display();

	void close();
}
