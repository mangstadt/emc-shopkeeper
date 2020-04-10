package emcshop.scraper;

import java.time.LocalDate;

/**
 * Contains the information found in a player's profile page. This class is
 * immutable. Use its {@link Builder} class to create new instances.
 * @author Michael Angstadt
 */
public class PlayerProfile {
	private final String playerName, portraitUrl, rank, rankColor, title;
	private final boolean private_;
	private final LocalDate joined;

	private PlayerProfile(Builder builder) {
		playerName = builder.playerName;
		portraitUrl = builder.portraitUrl;
		rank = builder.rank;
		rankColor = builder.rankColor;
		title = builder.title;
		private_ = builder.private_;
		joined = builder.joined;
	}

	/**
	 * Gets the player's name.
	 * @return the player's name
	 */
	public String getPlayerName() {
		return playerName;
	}

	/**
	 * Gets whether the profile is private or not.
	 * @return true if it's private, false if not
	 */
	public boolean isPrivate() {
		return private_;
	}

	/**
	 * Gets the URL of the player's portrait.
	 * @return the portrait URL or null if not found
	 */
	public String getPortraitUrl() {
		return portraitUrl;
	}

	/**
	 * Gets the player's rank.
	 * @return the player's rank (e.g. "Diamond Supporter") or null if player
	 * has no rank
	 */
	public String getRank() {
		return rank;
	}

	/**
	 * Gets the color of the rank (e.g. light blue for "Diamond Supporter").
	 * @return the color in hex format (e.g. "#00BFBF") or null if no color was
	 * specified
	 */
	public String getRankColor() {
		return rankColor;
	}

	/**
	 * Gets the date the player joined EMC.
	 * @return the join date or null if not found
	 */
	public LocalDate getJoined() {
		return joined;
	}

	/**
	 * Gets the player's title.
	 * @return the player's title (e.g. "Dedicated Member") or null if player
	 * has no title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Creates new instances of the {@link PlayerProfile} class.
	 * @author Michael Angstadt
	 */
	public static class Builder {
		private String playerName, portraitUrl, rank, rankColor, title;
		private boolean private_;
		private LocalDate joined;

		public Builder() {
			//empty
		}

		public Builder(PlayerProfile orig) {
			playerName = orig.playerName;
			portraitUrl = orig.portraitUrl;
			rank = orig.rank;
			rankColor = orig.rankColor;
			title = orig.title;
			private_ = orig.private_;
			joined = orig.joined;
		}

		public Builder playerName(String playerName) {
			this.playerName = playerName;
			return this;
		}

		public Builder private_(boolean private_) {
			this.private_ = private_;
			return this;
		}

		public Builder portraitUrl(String portraitUrl) {
			this.portraitUrl = portraitUrl;
			return this;
		}

		public Builder rank(String rank, String color) {
			this.rank = rank;
			this.rankColor = color;
			return this;
		}

		public Builder joined(LocalDate joined) {
			this.joined = joined;
			return this;
		}

		public Builder title(String title) {
			this.title = title;
			return this;
		}

		public PlayerProfile build() {
			return new PlayerProfile(this);
		}
	}
}
