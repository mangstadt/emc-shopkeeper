package emcshop.chat;

import java.util.Date;

/**
 * Represents a chat message.
 */
public class ChatMessage {
	private final Date date;
	private final String message;

	public ChatMessage(Date date, String message) {
		this.date = date;
		this.message = message;
	}

	public Date getDate() {
		return date;
	}

	public String getMessage() {
		return message;
	}
}
