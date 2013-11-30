package emcshop.gui;

import static emcshop.util.GuiUtils.toolTipText;
import static emcshop.util.NumberFormatter.formatQuantity;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;
import emcshop.db.DbDao;
import emcshop.db.Inventory;
import emcshop.gui.images.ImageManager;

@SuppressWarnings("serial")
public class InventoryTab extends JPanel {
	private final MainFrame owner;
	private final DbDao dao;

	private List<Inventory> inventory = new ArrayList<Inventory>();
	private List<Inventory> inventoryDisplayed = new ArrayList<Inventory>();

	private final JButton addEdit;
	private final JButton delete;
	private final ItemSuggestField item;
	private final JLabel quantityLabel;
	private final JTextField quantity;

	private final ExportComboBox export;
	private final JLabel filterByItemLabel;
	private final FilterTextField filterByItem;

	private InventoryTable table;
	private MyJScrollPane tableScrollPane;

	/**
	 * Defines all of the columns in this table. The order in which the enums
	 * are defined is the order that they will appear in the table.
	 */
	private enum Column {
		CHECKBOX(""), ITEM_NAME("Item Name"), REMAINING("Remaining (stacks)");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public InventoryTab(MainFrame owner, final DbDao dao) {
		this.owner = owner;
		this.dao = dao;

		addEdit = new JButton("Add/Edit");
		addEdit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				addItem();
				filterByItem.setText("");
			}
		});

		delete = new JButton("Delete Selected");
		delete.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				List<Integer> toDelete = new ArrayList<Integer>();
				for (int i = 0; i < table.checkboxes.size(); i++) {
					JCheckBox checkbox = table.checkboxes.get(i);
					if (checkbox.isSelected()) {
						Integer id = inventoryDisplayed.get(i).getId();
						toDelete.add(id);
					}
				}

				try {
					dao.deleteInventory(toDelete);
					dao.commit();
					refresh();
					filterByItem.setText("");
				} catch (SQLException e) {
					dao.rollback();
					throw new RuntimeException(e);
				}
			}
		});

		try {
			ItemSuggestField.init(dao);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		item = new ItemSuggestField(owner);

		quantityLabel = new JLabel("Qty:", ImageManager.getHelpIcon(), SwingConstants.LEFT);
		quantityLabel.setToolTipText(toolTipText("You can specify the quantity in the number of items and/or the number of stacks.\n\n<b>Examples</b>:\n\"5/23\" (5 stacks, plus 23 more)\n\"5/\" (5 stacks)\n\"5\" (5 items total)"));

		quantity = new JTextField();
		quantity.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				addItem();
			}
		});

		export = new ExportComboBoxImpl();

		filterByItemLabel = new JLabel("Filter by item(s):", ImageManager.getHelpIcon(), SwingConstants.LEFT);
		filterByItemLabel.setToolTipText(toolTipText("<b>Filters the table by item.</b>\n<b>Example</b>: <code>wool,\"book\"</code>\n\nMultiple item names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the item name(s), press [<code>Enter</code>] to perform the filtering operation."));

		filterByItem = new FilterTextField();
		filterByItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> filteredItems = filterByItem.getNames();
				table.filter(filteredItems);
				tableScrollPane.scrollToTop();
			}
		});

		try {
			inventory = dao.getInventory();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		inventoryDisplayed = inventory;

		Collections.sort(inventoryDisplayed, new Comparator<Inventory>() {
			@Override
			public int compare(Inventory a, Inventory b) {
				return a.getQuantity() - b.getQuantity();
			}
		});
		table = new InventoryTable();
		refresh();

		///////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		JPanel leftTop = new JPanel(new MigLayout());

		leftTop.add(new JLabel("Item Name:"));
		leftTop.add(quantityLabel, "wrap");
		leftTop.add(item, "w 200");
		leftTop.add(quantity, "w 75, wrap");
		leftTop.add(addEdit, "span 2, wrap");
		leftTop.add(delete, "span 2");

		add(leftTop);

		tableScrollPane = new MyJScrollPane(table);
		add(tableScrollPane, "span 1 3, grow, w 100%, h 100%, wrap");
		table.setColumns();

		add(new JSeparator(), "w 200!, align center, wrap");

		JPanel leftBottom = new JPanel(new MigLayout());

		leftBottom.add(new JLabel("Export:"));
		leftBottom.add(filterByItemLabel, "wrap");
		leftBottom.add(export);
		leftBottom.add(filterByItem, "split 2, w 150!");
		leftBottom.add(filterByItem.getClearButton(), "w 25!, h 20!, wrap");

		add(leftBottom, "growy");
	}

	private void addItem() {
		String itemStr = item.getText();
		String quantityStr = quantity.getText();
		if (itemStr.isEmpty() || quantityStr.isEmpty()) {
			return;
		}

		//parse the quantity
		//e.g. "5/23" means "5 stacks plus 23 more"
		//e.g. "5/" means "5 stacks"
		//e.g. "5" means "5 items"
		Integer quantityValue;
		try {
			String split[] = quantityStr.split("/", 2);
			if (split.length == 1) {
				quantityValue = Integer.valueOf(split[0]);
			} else {
				int remainder = split[1].isEmpty() ? 0 : Integer.valueOf(split[1]);
				quantityValue = Integer.valueOf(split[0]) * 64 + remainder;
			}
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Invalid quantity value.", "Error", JOptionPane.ERROR_MESSAGE);
			quantity.requestFocusInWindow();
			return;
		}

		try {
			dao.upsertInventory(itemStr, quantityValue);
			dao.commit();
		} catch (SQLException e) {
			dao.rollback();
			throw new RuntimeException(e);
		}
		refresh();

		item.setText("");
		quantity.setText("");
		item.requestFocusInWindow();
	}

	public void refresh() {
		try {
			inventory = dao.getInventory();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		inventoryDisplayed = inventory;
		table.sortData();
	}

	private class InventoryTable extends JTable {
		private Column prevColumnClicked;
		private boolean ascending;
		private List<JCheckBox> checkboxes = new ArrayList<JCheckBox>();

		public InventoryTable() {
			prevColumnClicked = Column.REMAINING;
			ascending = true;

			setModel();
			setColumns();

			getTableHeader().setReorderingAllowed(false);
			setColumnSelectionAllowed(false);
			setRowSelectionAllowed(false);
			setCellSelectionEnabled(false);
			setRowHeight(24);

			//change the state of the checkboxes when they are clicked
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					int row = rowAtPoint(event.getPoint());
					if (row < 0) {
						return;
					}

					JCheckBox checkbox = checkboxes.get(row);
					checkbox.setSelected(!checkbox.isSelected());
					AbstractTableModel model = (AbstractTableModel) getModel();
					model.fireTableDataChanged();
				}
			});

			//allow columns to be sorted by clicking on the headers
			getTableHeader().addMouseListener(new MouseAdapter() {
				private final Column columns[] = Column.values();

				@Override
				public void mouseClicked(MouseEvent e) {
					int index = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
					if (index < 0) {
						return;
					}

					Column column = columns[index];
					if (column == Column.CHECKBOX) {
						for (JCheckBox checkbox : checkboxes) {
							checkbox.setSelected(true);
						}
						AbstractTableModel model = (AbstractTableModel) getModel();
						model.fireTableDataChanged();
						return;
					}

					if (column == prevColumnClicked) {
						ascending = !ascending;
					} else {
						prevColumnClicked = column;
						ascending = true;
					}

					sortData();
				}
			});

			setDefaultRenderer(Inventory.class, new TableCellRenderer() {
				private final Color evenRowColor = new Color(255, 255, 255);
				private final Color oddRowColor = new Color(240, 240, 240);
				private final NumberFormat nf = NumberFormat.getInstance();
				{
					nf.setMaximumFractionDigits(2);
				}

				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
					final Inventory inv = (Inventory) value;

					if (col == Column.CHECKBOX.ordinal()) {
						return checkboxes.get(row);
					}

					JLabel label = null;

					if (col == Column.ITEM_NAME.ordinal()) {
						ImageIcon img = ImageManager.getItemImage(inv.getItem());
						label = new JLabel(inv.getItem(), img, SwingConstants.LEFT);
					} else if (col == Column.REMAINING.ordinal()) {
						int quantity = inv.getQuantity();
						double stacks = quantity / 64.0;

						label = new JLabel(formatQuantity(quantity, false) + " (" + nf.format(stacks) + ")");
					}

					//set the background color of the row
					Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
					label.setOpaque(true);
					label.setBackground(color);

					return label;
				}
			});
		}

		public void filter(List<String> filteredItemNames) {
			if (filteredItemNames.isEmpty()) {
				inventoryDisplayed = inventory;
				sortData();
			} else {
				inventoryDisplayed = new ArrayList<Inventory>();
				for (Inventory inv : inventory) {
					String itemName = inv.getItem().toLowerCase();
					for (String filteredItem : filteredItemNames) {
						filteredItem = filteredItem.toLowerCase();
						boolean add = false;
						if (filteredItem.startsWith("\"") && filteredItem.endsWith("\"")) {
							filteredItem = filteredItem.substring(1, filteredItem.length() - 1); //remove double quotes
							if (itemName.equals(filteredItem)) {
								add = true;
							}
						} else if (itemName.contains(filteredItem)) {
							add = true;
						}

						if (add) {
							inventoryDisplayed.add(inv);
							break;
						}
					}
				}
				refresh();
			}
		}

		private void sortData() {
			Comparator<Inventory> comparator = null;

			switch (prevColumnClicked) {
			case CHECKBOX:
				return;
			case ITEM_NAME:
				//sort by item name
				comparator = new Comparator<Inventory>() {
					@Override
					public int compare(Inventory a, Inventory b) {
						if (ascending) {
							return a.getItem().compareToIgnoreCase(b.getItem());
						}
						return b.getItem().compareToIgnoreCase(a.getItem());
					}
				};
				break;
			case REMAINING:
				comparator = new Comparator<Inventory>() {
					@Override
					public int compare(Inventory a, Inventory b) {
						if (ascending) {
							return a.getQuantity() - b.getQuantity();
						}
						return b.getQuantity() - a.getQuantity();
					}
				};
				break;
			}

			Collections.sort(inventoryDisplayed, comparator);
			refresh();
		}

		private void refresh() {
			//rebuild the checkboxes
			checkboxes = new ArrayList<JCheckBox>();
			for (int i = 0; i < inventoryDisplayed.size(); i++) {
				JCheckBox checkbox = new JCheckBox();
				checkboxes.add(checkbox);
			}

			AbstractTableModel model = (AbstractTableModel) getModel();
			model.fireTableStructureChanged();

			setColumns();
		}

		private void setColumns() {
			columnModel.getColumn(Column.CHECKBOX.ordinal()).setMaxWidth(30);
			columnModel.getColumn(Column.ITEM_NAME.ordinal()).setMinWidth(200);
		}

		private void setModel() {
			setModel(new AbstractTableModel() {
				private final Column columns[] = Column.values();

				@Override
				public int getColumnCount() {
					return columns.length;
				}

				@Override
				public String getColumnName(int index) {
					Column column = columns[index];

					String text = column.getName();
					if (prevColumnClicked == column) {
						String arrow = (ascending) ? "\u25bc" : "\u25b2";
						text = arrow + " " + text;
					}
					return text;
				}

				@Override
				public int getRowCount() {
					return inventoryDisplayed.size();
				}

				@Override
				public Object getValueAt(int row, int col) {
					return inventoryDisplayed.get(row);
				}

				public Class<?> getColumnClass(int c) {
					return Inventory.class;
				}

				@Override
				public boolean isCellEditable(int row, int col) {
					if (col == Column.CHECKBOX.ordinal()) {
						return true;
					}
					return false;
				}
			});
		}
	}

	private class ExportComboBoxImpl extends ExportComboBox implements ActionListener {
		@Override
		public String bbCode() {
			//TODO
			return "";
		}

		@Override
		public String csv() {
			//TODO
			return "";
		}
	}
}
