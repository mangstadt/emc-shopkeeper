package emcshop.db;

import java.time.LocalDateTime;

/**
 * Represents a row in the "players" table.
 * @author Michael Angstadt
 */
public class Player {
	private Integer id;
	private String name;
	private LocalDateTime firstSeen;
	private LocalDateTime lastSeen;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDateTime getFirstSeen() {
		return firstSeen;
	}

	public void setFirstSeen(LocalDateTime firstSeen) {
		this.firstSeen = firstSeen;
	}

	public LocalDateTime getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(LocalDateTime lastSeen) {
		this.lastSeen = lastSeen;
	}
}
