package emcshop.db;

import java.util.Date;

/**
 * Represents a row in the "players" table.
 * @author Michael Angstadt
 */
public class Player {
	private Integer id;
	private String name;
	private Date firstSeen;
	private Date lastSeen;

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

	public Date getFirstSeen() {
		return firstSeen;
	}

	public void setFirstSeen(Date firstSeen) {
		this.firstSeen = firstSeen;
	}

	public Date getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(Date lastSeen) {
		this.lastSeen = lastSeen;
	}

}
