package emcshop.scraper;

import java.util.Date;

/**
 * Contains the information found in a player's profile page.
 */
public class PlayerProfile {
	private String playerName;
	private boolean private_;
	private String portraitUrl;
	private Rank rank;
	private Date joined;
	private String title;

	public String getPlayerName() {
		return playerName;
	}

	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	public boolean isPrivate() {
		return private_;
	}

	public void setPrivate(boolean private_) {
		this.private_ = private_;
	}

	public String getPortraitUrl() {
		return portraitUrl;
	}

	public void setPortraitUrl(String portraitUrl) {
		this.portraitUrl = portraitUrl;
	}

	public Rank getRank() {
		return rank;
	}

	public void setRank(Rank rank) {
		this.rank = rank;
	}

	public Date getJoined() {
		return joined;
	}

	public void setJoined(Date joined) {
		this.joined = joined;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
}
