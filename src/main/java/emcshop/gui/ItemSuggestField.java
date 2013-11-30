package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.suggest.ContainsMatcher;
import emcshop.gui.lib.suggest.JSuggestField;

@SuppressWarnings("serial")
public class ItemSuggestField extends JSuggestField {
	private static final Color selectedItemBgColor = new Color(192, 192, 192);
	private static Vector<String> itemNames;
	private static Map<String, JLabel> itemIconLabels;

	public ItemSuggestField(Window parent) {
		super(parent, itemNames);
		setSuggestMatcher(new ContainsMatcher());
		setListCellRenderer(new ListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				String itemName = (String) value;
				JLabel label = itemIconLabels.get(itemName);
				if (label == null) {
					ImageIcon image = ImageManager.getItemImage("_empty");
					label = new JLabel(itemName, image, SwingConstants.LEFT);
				}

				label.setOpaque(isSelected);
				if (isSelected) {
					label.setBackground(selectedItemBgColor);
				}
				return label;
			}
		});
	}

	/**
	 * Builds the list of items that all instances of this control will use.
	 * @param dao
	 * @throws SQLException
	 */
	public static void init(DbDao dao) throws SQLException {
		if (itemNames != null) {
			return;
		}
		
		//build labels for item icons
		List<String> itemNamesList = dao.getItemNames();
		itemNames = new Vector<String>(itemNamesList);
		itemIconLabels = new HashMap<String, JLabel>();
		for (String itemName : itemNamesList) {
			ImageIcon image = ImageManager.getItemImage(itemName);
			JLabel label = new JLabel(itemName, image, SwingConstants.LEFT);
			itemIconLabels.put(itemName, label);
		}
	}
}
