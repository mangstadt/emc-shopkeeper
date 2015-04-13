package emcshop.gui;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.images.ImageManager;

/**
 * A "main menu" button that displays a popup menu when clicked.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class MenuButton extends JToggleButton {
	private final static ImageIcon empty = ImageManager.getImageIcon("menu-empty-icon.png");

	private static final int menuIconSize;
	static {
		Font font = (Font) UIManager.get("Menu.font");
		menuIconSize = font.getSize() * 2;
	}

	private static final int menuItemIconSize;
	static {
		Font font = (Font) UIManager.get("MenuItem.font");
		menuItemIconSize = font.getSize() * 2;
	}

	private final JPopupMenu popupMenu = new JPopupMenu();
	private long popupMenuCanceled = 0;
	private int offsetX = 0, offsetY = 0;

	public MenuButton() {
		setIcon(ImageManager.getImageIcon("menu.png"));

		popupMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuCanceled(PopupMenuEvent event) {
				//do nothing
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
				popupMenuCanceled = System.currentTimeMillis();
				setSelected(false);
			}

			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
				//do nothing
			}
		});

		addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				if (System.currentTimeMillis() - popupMenuCanceled < 100) {
					popupMenuCanceled = 0;
					setSelected(false);
					return;
				}

				popupMenu.show(MenuButton.this, getX() + offsetX, getY() + offsetY);
			}
		});
	}

	/**
	 * Sets the position the popup menu will be displayed, relative to the
	 * button.
	 * @param x the x offset
	 * @param y the y offset
	 */
	public void setOffset(int x, int y) {
		offsetX = x;
		offsetY = y;
	}

	/**
	 * Adds a menu.
	 * @param text the menu label
	 * @return an object for customizing the menu
	 */
	public MenuAdder addMenu(String text) {
		return new MenuAdder(text);
	}

	/**
	 * Adds an item to the menu.
	 * @param text the item label
	 * @return an object for customizing the menu item
	 */
	public MenuItemAdder<JMenuItem> addMenuItem(String text) {
		return new MenuItemAdder<JMenuItem>(new JMenuItem(text));
	}

	/**
	 * Adds a checkbox item to the menu.
	 * @param text the item label
	 * @return an object for customizing the menu item
	 */
	public MenuItemAdder<JCheckBoxMenuItem> addCheckboxMenuItem(String text) {
		return new MenuItemAdder<JCheckBoxMenuItem>(new JCheckBoxMenuItem(text));
	}

	/**
	 * Adds a radio button item to the menu.
	 * @param text the item label
	 * @param group the button group that this item belongs to
	 * @return an object for customizing the menu item
	 */
	public MenuItemAdder<JRadioButtonMenuItem> addRadioButtonMenuItem(String text, ButtonGroup group) {
		JRadioButtonMenuItem item = new JRadioButtonMenuItem(text);
		group.add(item);
		return new MenuItemAdder<JRadioButtonMenuItem>(new JRadioButtonMenuItem(text));
	}

	/**
	 * Adds a horizontal line to separate groups of menu items.
	 */
	public void addSeparator() {
		JPanel panel = new JPanel(new MigLayout("insets 0, fillx"));
		panel.add(new JSeparator(), "growx, gaptop 5, gapbottom 5");
		panel.setOpaque(false);
		popupMenu.add(panel);
	}

	/**
	 * Gets the popup menu instance.
	 * @return the popup menu
	 */
	public JPopupMenu getMenu() {
		return popupMenu;
	}

	public class MenuAdder {
		private final JMenu menu;
		private ImageIcon icon = empty;
		private JMenu parent;

		private MenuAdder(String text) {
			menu = new JMenu(text);
		}

		public MenuAdder icon(ImageIcon icon) {
			this.icon = icon;
			return this;
		}

		public MenuAdder parent(JMenu parent) {
			this.parent = parent;
			return this;
		}

		public JMenu add() {
			menu.setIcon(ImageManager.scale(icon, menuIconSize));

			if (parent == null) {
				popupMenu.add(menu);
			} else {
				parent.add(menu);
			}

			return menu;
		}
	}

	public class MenuItemAdder<T extends JMenuItem> {
		private T item;
		private ImageIcon icon = empty;
		private JMenu parent;

		private MenuItemAdder(T item) {
			this.item = item;
		}

		public MenuItemAdder<T> icon(ImageIcon icon) {
			this.icon = icon;
			return this;
		}

		public MenuItemAdder<T> parent(JMenu parent) {
			this.parent = parent;
			return this;
		}

		public T add(ActionListener onClick) {
			item.setIcon(ImageManager.scale(icon, menuItemIconSize));
			item.addActionListener(onClick);

			if (parent == null) {
				popupMenu.add(item);
			} else {
				parent.add(item);
			}

			return item;
		}
	}
}
