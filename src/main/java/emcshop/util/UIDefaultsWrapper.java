package emcshop.util;

import java.awt.Color;

import javax.swing.JComponent;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

/**
 * Wraps calls to {@link UIDefaults}.
 */
public class UIDefaultsWrapper {
    private static final UIDefaults defaults = UIManager.getLookAndFeel().getDefaults();

    /**
     * Assigns the settings for lists to a component.
     *
     * @param component the component
     * @param selected  true if the component is selected, false if not
     */
    public static void assignListFormats(JComponent component, boolean selected) {
        component.setOpaque(true);
        component.setForeground(getListForeground(selected));
        component.setBackground(getListBackground(selected));
    }

    /**
     * Gets the foreground color of labels.
     *
     * @return the foreground color
     */
    public static Color getLabelForeground() {
        return defaults.getColor("Label.foreground");
    }

    /**
     * Gets the foreground color for selected list items.
     *
     * @return the foreground color
     */
    public static Color getListForegroundSelected() {
        return defaults.getColor("List.selectionForeground");
    }

    /**
     * Gets the foreground color for unselected list items.
     *
     * @return the foreground color
     */
    public static Color getListForegroundUnselected() {
        return defaults.getColor("List.foreground");
    }

    /**
     * Gets the foreground color for a list item.
     *
     * @param selected true if the item is selected, false if not
     * @return the foreground color
     */
    public static Color getListForeground(boolean selected) {
        return selected ? getListForegroundSelected() : getListForegroundUnselected();
    }

    /**
     * Gets the background color for selected list items.
     *
     * @return the background color
     */
    public static Color getListBackgroundSelected() {
        return defaults.getColor("List.selectionBackground");
    }

    /**
     * Gets the background color for unselected list items.
     *
     * @return the background color
     */
    public static Color getListBackgroundUnselected() {
        return defaults.getColor("List.background");
    }

    /**
     * Gets the background color for a list item.
     *
     * @param selected true if the item is selected, false if not
     * @return the background color
     */
    public static Color getListBackground(boolean selected) {
        return selected ? getListBackgroundSelected() : getListBackgroundUnselected();
    }

    private UIDefaultsWrapper() {
        //hide
    }
}
