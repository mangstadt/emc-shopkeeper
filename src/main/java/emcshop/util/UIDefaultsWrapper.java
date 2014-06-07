package emcshop.util;

import java.awt.Color;

import javax.swing.UIDefaults;
import javax.swing.UIManager;

/**
 * Wraps calls to {@link UIDefaults}.
 */
public class UIDefaultsWrapper {
	private static final UIDefaults defaults = UIManager.getLookAndFeel().getDefaults();

	/**
	 * Gets the background color for selected list items.
	 * @return the background color
	 */
	public static Color getListSelected() {
		return defaults.getColor("List.selectionBackground");
	}

	/**
	 * Gets the background color for unselected list items.
	 * @return the background color
	 */
	public static Color getListUnselected() {
		return defaults.getColor("List.background");
	}

	/**
	 * Gets the background color for a list item.
	 * @param selected true if the item is selected, false if not
	 * @return the background color
	 */
	public static Color getListBackground(boolean selected) {
		return selected ? getListSelected() : getListUnselected();
	}

	private UIDefaultsWrapper() {
		//hide
	}
}
