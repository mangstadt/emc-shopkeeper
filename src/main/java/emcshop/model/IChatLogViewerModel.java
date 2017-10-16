package emcshop.model;

import java.io.File;
import java.util.Date;
import java.util.List;

import emcshop.chat.ChatMessage;
import emcshop.db.PaymentTransactionDb;

public interface IChatLogViewerModel {
    File getLogDirectory();

    void setLogDirectory(File dir);

    PaymentTransactionDb getPaymentTransaction();

    List<ChatMessage> getChatMessages(Date date);

    String getCurrentPlayer();
}
