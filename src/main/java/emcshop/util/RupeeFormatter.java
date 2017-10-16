package emcshop.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Formats rupee currency values.
 *
 * @author Michael Angstadt
 */
public class RupeeFormatter extends BaseFormatter {
    public RupeeFormatter() {
        this(0);
    }

    /**
     * @param decimals the number of decimal places to include
     */
    public RupeeFormatter(int decimals) {
        super(decimals(decimals) + "'r'");
    }

    private static String decimals(int decimals) {
        StringBuilder sb = new StringBuilder("#,###");
        if (decimals > 0) {
            sb.append('.');
            sb.append(StringUtils.repeat('#', decimals));
        }
        return sb.toString();
    }
}
