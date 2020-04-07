package emcshop.chat;

import java.time.LocalDateTime;

/**
 * Represents a chat message.
 */
public class ChatMessage {
	private final LocalDateTime date;
	private final String message;

	public ChatMessage(LocalDateTime date, String message) {
		this.date = date;
		this.message = message;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public String getMessage() {
		return message;
	}
}
