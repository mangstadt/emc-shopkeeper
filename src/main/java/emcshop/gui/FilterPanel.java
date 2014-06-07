package emcshop.gui;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import net.miginfocom.swing.MigLayout;
import emcshop.ExportType;
import emcshop.util.FilterList;

/**
 * A small toolbar that goes above tables, that allows the table data to be
 * filtered, sorted, and exported.
 */
@SuppressWarnings("serial")
public class FilterPanel extends JPanel {
	private final JLabel filterByItemLabel;
	private final FilterTextField filterByItem;
	private final JLabel filterByPlayerLabel;
	private final FilterTextField filterByPlayer;
	private final JLabel sortByLabel;
	private final SortComboBox sortBy;
	private final JButton export;
	private final JPopupMenu exportMenu;

	private final List<FilterListener> filterListeners = new ArrayList<FilterListener>();
	private final List<ExportListener> exportListeners = new ArrayList<ExportListener>();
	private final List<SortListener> sortListeners = new ArrayList<SortListener>();

	public FilterPanel() {
		ActionListener filterAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (FilterListener listener : filterListeners) {
					listener.filterPerformed(filterByItem.getNames(), filterByPlayer.getNames());
				}
			}
		};

		filterByItemLabel = new HelpLabel("<html><font size=2>Filter by item(s):", "<b>Filters the table by item.</b>\n<b>Example</b>: <code>wool,\"book\"</code>\n\nMultiple item names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the item name(s), press [<code>Enter</code>] to perform the filtering operation.");
		filterByItem = new FilterTextField();
		filterByItem.addActionListener(filterAction);

		filterByPlayerLabel = new HelpLabel("<html><font size=2>Filter by player(s):", "<b>Filters the table by player.</b>\n<b>Example</b>: <code>aikar,max</code>\n\nMultiple player names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the player name(s), press [<code>Enter</code>] to perform the filtering operation.");
		filterByPlayer = new FilterTextField();
		filterByPlayer.addActionListener(filterAction);

		sortByLabel = new JLabel("<html><font size=2>Sort by:");
		sortBy = new SortComboBox();
		Font orig = sortBy.getFont();
		sortBy.setFont(new Font(orig.getName(), orig.getStyle(), orig.getSize() - 2));
		sortBy.addActionListener(new ActionListener() {
			private SortItem prev;

			@Override
			public void actionPerformed(ActionEvent e) {
				SortItem selected = sortBy.getSelectedItem();
				if (prev == selected) {
					//the same item was selected
					return;
				}

				for (SortListener listener : sortListeners) {
					listener.sortPerformed(selected);
				}
				prev = selected;
			}
		});

		exportMenu = new JPopupMenu();
		for (final ExportType type : ExportType.values()) {
			AbstractAction action = new AbstractAction() {
				@Override
				public String toString() {
					return type.toString();
				}

				@Override
				public void actionPerformed(ActionEvent e) {
					for (ExportListener listener : exportListeners) {
						listener.exportPerformed(type);
					}
				}
			};
			action.putValue(Action.NAME, type.toString());
			exportMenu.add(action);
		}

		export = new JButton("<html><font size=2>Export \u00bb");
		export.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				exportMenu.show(export, event.getX(), event.getY());
			}
		});

		///////////////////

		setLayout(new MigLayout("insets 0"));
	}

	public void setVisible(boolean item, boolean player, boolean sort) {
		removeAll();

		if (item) {
			add(filterByItemLabel);
			add(filterByItem, "w 120");
			add(filterByItem.getClearButton(), "w 20, h 20");
		}

		if (player) {
			add(filterByPlayerLabel);
			add(filterByPlayer, "w 120");
			add(filterByPlayer.getClearButton(), "w 20, h 20");
		}

		if (sort) {
			add(sortByLabel);
			add(sortBy, "w 120");
		}

		add(export);

		validate();
	}

	public void addFilterListener(FilterListener listener) {
		filterListeners.add(listener);
	}

	public void addSortListener(SortListener listener) {
		sortListeners.add(listener);
	}

	public void addExportListener(ExportListener listener) {
		exportListeners.add(listener);
	}

	public void clear() {
		filterByItem.setText("");
		filterByPlayer.setText("");
		sortBy.setSelectedIndex(0);
	}

	public static interface FilterListener {
		void filterPerformed(FilterList items, FilterList players);
	}

	public static interface SortListener {
		void sortPerformed(SortItem sort);
	}

	public static interface ExportListener {
		void exportPerformed(ExportType type);
	}

	private static class SortComboBox extends JComboBox {
		public SortComboBox() {
			super(SortItem.values());
		}

		@Override
		public SortItem getSelectedItem() {
			return (SortItem) super.getSelectedItem();
		}
	}

	public static enum SortItem {
		PLAYER("Player Name"), HIGHEST("Highest Net Total"), LOWEST("Lowest Net Total");

		private final String display;

		private SortItem(String display) {
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}
}