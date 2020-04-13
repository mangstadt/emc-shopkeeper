package emcshop.gui;

import java.awt.Font;
import java.awt.Point;
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

import emcshop.gui.images.Images;
import net.miginfocom.swing.MigLayout;

/**
 * A "main menu" button that displays a popup menu when clicked.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class MenuButton extends JToggleButton {
	private final static ImageIcon empty = Images.get("menu-empty-icon.png");

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

	public MenuButton() {
		setIcon(Images.get("menu.png"));

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

		addActionListener(event -> {
			if (System.currentTimeMillis() - popupMenuCanceled < 100) {
				popupMenuCanceled = 0;
				setSelected(false);
				return;
			}

			Point buttonScreenLocation = getLocationOnScreen();
			int x = buttonScreenLocation.x;
			int y = buttonScreenLocation.y + getHeight();

			//"show()" is supposed to set the location of the menu relative to the parent component, but it never seems to set the location correctly for some reason
			//however, we still need to call "show()" because the menu won't work right otherwise
			popupMenu.show(this, x, y);

			//this sets the menu's on-screen location (what we want)
			popupMenu.setLocation(x, y);
		});
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
		return new MenuItemAdder<>(new JMenuItem(text));
	}

	/**
	 * Adds a checkbox item to the menu.
	 * @param text the item label
	 * @return an object for customizing the menu item
	 */
	public MenuItemAdder<JCheckBoxMenuItem> addCheckboxMenuItem(String text) {
		return new MenuItemAdder<>(new JCheckBoxMenuItem(text));
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
		return new MenuItemAdder<>(item);
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
			menu.setIcon(Images.scale(icon, menuIconSize));

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
			item.setIcon(Images.scale(icon, menuItemIconSize));
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
