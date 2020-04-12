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

import emcshop.gui.images.Images;
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
		setListCellRenderer(new ListCellRenderer<String>() {
			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(2, 4, 2, 4));
			}

			private final ImageIcon empty = Images.getItemImage("_empty");

			@Override
			public Component getListCellRendererComponent(JList<? extends String> list, String itemName, int index, boolean selected, boolean hasFocus) {
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
	 * Sets the list of items that all instances of this control will use. This
	 * method should be called only once during application startup.
	 * @param itemNames the item names to populate this control with
	 */
	public static void init(List<String> itemNames) throws SQLException {
		ItemSuggestField.itemNames = new Vector<>(itemNames);

		ImmutableMap.Builder<String, ImageIcon> builder = ImmutableMap.builder();
		for (String itemName : itemNames) {
			ImageIcon image = Images.getItemImage(itemName);
			builder.put(itemName, image);
		}
		itemIcons = builder.build();
	}
}
