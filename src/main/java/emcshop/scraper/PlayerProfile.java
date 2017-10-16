package emcshop.scraper;

import java.util.Date;

/**
 * Contains the information found in a player's profile page. This class is
 * immutable. Use its {@link Builder} class to create new instances.
 *
 * @author Michael Angstadt
 */
public class PlayerProfile {
    private final String playerName, portraitUrl, title;
    private final boolean private_;
    private final Rank rank;
    private final Date joined;

    private PlayerProfile(Builder builder) {
        playerName = builder.playerName;
        private_ = builder.private_;
        portraitUrl = builder.portraitUrl;
        rank = builder.rank;
        joined = (builder.joined == null) ? null : new Date(builder.joined.getTime());
        title = builder.title;
    }

    /**
     * Gets the player's name.
     *
     * @return the player's name
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Gets whether the profile is private or not.
     *
     * @return true if it's private, false if not
     */
    public boolean isPrivate() {
        return private_;
    }

    /**
     * Gets the URL of the player's portrait.
     *
     * @return the portrait URL or null if not found
     */
    public String getPortraitUrl() {
        return portraitUrl;
    }

    /**
     * Gets the player's rank.
     *
     * @return the player's rank or null if not found
     */
    public Rank getRank() {
        return rank;
    }

    /**
     * Gets the date the player joined EMC.
     *
     * @return the join date or null if not found
     */
    public Date getJoined() {
        return joined;
    }

    /**
     * Gets the player's title.
     *
     * @return the player's title or null if not found
     */
    public String getTitle() {
        return title;
    }

    /**
     * Creates new instances of the {@link PlayerProfile} class.
     *
     * @author Michael Angstadt
     */
    public static class Builder {
        private String playerName, portraitUrl, title;
        private boolean private_;
        private Rank rank;
        private Date joined;

        public Builder() {
            //empty
        }

        public Builder(PlayerProfile orig) {
            playerName = orig.playerName;
            private_ = orig.private_;
            portraitUrl = orig.portraitUrl;
            rank = orig.rank;
            joined = orig.joined;
            title = orig.title;
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

        public Builder rank(Rank rank) {
            this.rank = rank;
            return this;
        }

        public Builder joined(Date joined) {
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
