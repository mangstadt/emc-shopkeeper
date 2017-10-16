package emcshop.rupees.scribe;

import emcshop.rupees.dto.ShopTransaction.Builder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Michael Angstadt
 */
public class ShopTransactionScribe extends RupeeTransactionScribe<Builder> {
    private final Pattern soldRegex = Pattern.compile("^Player shop sold ([\\d,]+) (.*?) to (.*)$", Pattern.CASE_INSENSITIVE);
    private final Pattern boughtRegex = Pattern.compile("^Your player shop bought ([\\d,]+) (.*?) from (.*)$", Pattern.CASE_INSENSITIVE);
    private final Pattern otherShopSoldRegex = Pattern.compile("^Sold to player shop ([\\d,]+) (.*?) to (.*)$", Pattern.CASE_INSENSITIVE);
    private final Pattern otherShopBoughtRegex = Pattern.compile("^Player shop purchased ([\\d,]+) (.*?) from (.*)$", Pattern.CASE_INSENSITIVE);

    @Override
    public Builder parse(String description) {
        boolean playerIsCustomer = true;
        int negate = -1;
        Matcher m = soldRegex.matcher(description);
        if (!m.find()) {
            negate = 1;
            playerIsCustomer = true;
            m = boughtRegex.matcher(description);
            if (!m.find()) {
                negate = 1;
                playerIsCustomer = false;
                m = otherShopBoughtRegex.matcher(description);
                if (!m.find()) {
                    negate = -1;
                    playerIsCustomer = false;
                    m = otherShopSoldRegex.matcher(description);
                    if (!m.find()) {
                        return null;
                    }
                }
            }
        }

        //@formatter:off
        Builder builder = new Builder()
                .quantity(parseNumber(m.group(1)) * negate)
                .item(m.group(2));
        //@formatter:on

        String name = m.group(3);
        if (playerIsCustomer) {
            builder.shopCustomer(name);
        } else {
            builder.shopOwner(name);
        }

        return builder;
    }
}