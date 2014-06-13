package emcshop.gui;

import java.awt.Component;
import java.awt.Window;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

import com.google.common.collect.ImmutableMap;

import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.suggest.ContainsMatcher;
import emcshop.gui.lib.suggest.JSuggestField;
import emcshop.util.UIDefaultsWrapper;

@SuppressWarnings("serial")
public class ItemSuggestField extends JSuggestField {
	private static Vector<String> itemNames;
	private static Map<String, ImageIcon> itemIcons;

	/**
	 * @param parent the parent window
	 */
	public ItemSuggestField(Window parent) {
		super(parent, itemNames);

		setSuggestMatcher(new ContainsMatcher());
		setListCellRenderer(new ListCellRenderer() {
			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(2, 4, 2, 4));
			}

			private final ImageIcon empty = ImageManager.getItemImage("_empty");

			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
				String itemName = (String) value;
				label.setText(itemName);

				ImageIcon icon = itemIcons.get(itemName);
				if (icon == null) {
					icon = empty;
				}
				label.setIcon(icon);

				UIDefaultsWrapper.assignListFormats(label, selected);
				return label;
			}
		});
	}

	/**
	 * Builds the list of items that all instances of this control will use.
	 * @param dao the database DAO
	 * @throws SQLException if there's a problem retrieving data from the
	 * database
	 */
	public static void init(DbDao dao) throws SQLException {
		if (itemNames != null) {
			return;
		}

		//get item names
		List<String> itemNamesList = dao.getItemNames();
		itemNames = new Vector<String>(itemNamesList);

		//load item icons
		ImmutableMap.Builder<String, ImageIcon> builder = ImmutableMap.builder();
		for (String itemName : itemNamesList) {
			ImageIcon image = ImageManager.getItemImage(itemName);
			builder.put(itemName, image);
		}
		itemIcons = builder.build();
	}
}
