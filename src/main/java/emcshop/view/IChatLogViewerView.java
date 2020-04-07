package emcshop.view;

import java.awt.event.ActionListener;
import java.io.File;
import java.time.LocalDate;
import java.util.List;

import emcshop.chat.ChatMessage;
import emcshop.db.PaymentTransactionDb;

public interface IChatLogViewerView {
	void addDateChangedListener(ActionListener listener);

	void addLogDirectoryChanged(ActionListener listener);

	void addCloseListener(ActionListener listener);

	File getLogDirectory();

	void setLogDirectory(File logDirectory);

	void setPaymentTransaction(PaymentTransactionDb paymentTransaction);

	LocalDate getDate();

	void setDate(LocalDate dateToDisplay);

	void setChatMessages(List<ChatMessage> chatMessages);

	void setCurrentPlayer(String currentPlayer);

	void showError(String string);

	void display();

	void close();
}
