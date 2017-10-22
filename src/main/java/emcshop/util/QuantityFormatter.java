package emcshop.util;


/**
 * Formats quantity values.
 *
 * @author Michael Angstadt
 */
public class QuantityFormatter extends BaseFormatter {
    public String format(int quantity, int stackSize) {
        if (stackSize <= 1) {
            return format(quantity);
        }

        int stacks = quantity / stackSize;
        if (stacks == 0) {
            return format(quantity);
        }

        int remaining = quantity % stackSize;
        if (remaining < 0) {
            //the remaining part should not contain a "-"
            remaining *= -1;
        }

        String quantityStr = nf.format(stacks) + '/' + remaining;
        if (quantity > 0 && plus) {
            quantityStr = '+' + quantityStr;
        }
        if (color) {
            quantityStr = colorize(quantityStr, quantity);
        }
        return quantityStr;
    }
}
