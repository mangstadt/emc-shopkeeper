package emcshop.rupees.dto;

/**
 * The fee that players are charged to send mail to other players.
 *
 * @author Michael Angstadt
 */
public class MailFee extends RupeeTransaction {
    private final String player, subject;

    private MailFee(Builder builder) {
        super(builder);
        player = builder.player;
        subject = builder.subject;
    }

    public String getPlayer() {
        return player;
    }

    public String getSubject() {
        return subject;
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        private String player, subject;

        public Builder player(String player) {
            this.player = player;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public MailFee build() {
            return new MailFee(this);
        }
    }
}