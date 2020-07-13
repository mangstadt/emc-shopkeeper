package emcshop.gui;

import static emcshop.util.GuiUtils.shrinkFont;
import static emcshop.util.GuiUtils.toolTipText;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.AbstractCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import org.apache.commons.io.FileUtils;

import emcshop.AppContext;
import emcshop.ExportType;
import emcshop.ItemIndex;
import emcshop.ItemIndex.CategoryInfo;
import emcshop.QueryExporter;
import emcshop.Settings;
import emcshop.db.DbDao;
import emcshop.db.Inventory;
import emcshop.gui.ExportButton.ExportListener;
import emcshop.gui.images.Images;
import emcshop.gui.lib.GroupPanel;
import emcshop.util.ChesterFile;
import emcshop.util.GuiUtils;
import emcshop.util.QuantityFormatter;
import emcshop.util.UIDefaultsWrapper;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class InventoryTab extends JPanel implements ExportListener {
	private static final Logger logger = Logger.getLogger(InventoryTab.class.getName());
	private static final AppContext context = AppContext.instance();

	private final CategoryInfo ALL = new CategoryInfo(-1, "ALL", null);
	private final CategoryInfo MISC = new CategoryInfo(-1, "misc", null);
	private final CategoryInfo LOW = new CategoryInfo(-1, "low in stock", null);

	private final MainFrame owner;
	private final DbDao dao = context.get(DbDao.class);
	private final ItemIndex index = ItemIndex.instance();

	private final FilterPanel filterPanel;
	private final JButton addItem;
	private final JButton chester;
	private final ItemSuggestField item;
	private final JLabel quantityLabel;
	private final QuantityTextField quantity;
	private final JLabel lowThresholdLabel;
	private final QuantityTextField lowThreshold;

	private InventoryTable table;
	private boolean showQuantitiesInStacks = context.get(Settings.class).isShowQuantitiesInStacks();
	private MyJScrollPane tableScrollPane;

	/**
	 * Defines all of the columns in this table. The order in which the enums
	 * are defined is the order that they will appear in the table.
	 */
	private enum Column {
		CHECKBOX(""), ITEM_NAME("Item Name"), REMAINING("Remaining"), LOW_THRESHOLD("Low Threshold");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public InventoryTab(MainFrame owner) {
		this.owner = owner;

		//		try {
		//			Random r = new Random();
		//			for (String item : index.getItemNames()) {
		//				dao.upsertInventory(item, r.nextInt(512), false);
		//			}
		//
		//			for (Inventory inv : dao.getInventory()) {
		//				dao.updateInventoryLowThreshold(inv.getItem(), r.nextInt(64));
		//			}
		//			dao.commit();
		//		} catch (Exception e) {
		//
		//		}

		filterPanel = new FilterPanel(this);
		filterPanel.addFilterListener((items, category) -> {
			table.filter(items, category);
			tableScrollPane.scrollToTop();
		});
		filterPanel.addDeleteListener(event -> {
			List<Row> selected = table.getSelected();
			if (selected.isEmpty()) {
				return;
			}

			List<Integer> selectedIds = new ArrayList<>(selected.size());
			for (Row row : selected) {
				selectedIds.add(row.inventory.getId());
			}

			try {
				dao.deleteInventory(selectedIds);
				dao.commit();
			} catch (SQLException e) {
				dao.rollback();
				throw new RuntimeException(e);
			}

			table.model.data.removeAll(selected);
			table.model.fireTableDataChanged();
		});
		filterPanel.setDeleteEnabled(false);

		addItem = new JButton("Add");
		addItem.addActionListener(event -> addItem());

		chester = new JButton("Start Chester");
		chester.setToolTipText(toolTipText("Allows you to record your shop's inventory by simply opening your shop chests in-game.  Requires the \"Chester\" mod to be installed on your Minecraft client."));
		chester.addActionListener(event -> {
			ChesterDialog dialog = new ChesterDialog();
			dialog.setVisible(true);
			if (dialog.cancelled) {
				return;
			}

			Collection<Inventory> items = dialog.getItems();
			if (items.isEmpty()) {
				return;
			}

			try {
				for (Inventory item : items) {
					dao.upsertInventory(item);
				}
				dao.commit();
			} catch (SQLException e) {
				dao.rollback();
			}

			refresh();
		});

		item = new ItemSuggestField(owner);

		quantityLabel = new HelpLabel("Qty:", "In addition to specifying the exact number of items, you can also specify the quantity in stacks.\n\n<b>Examples</b>:\n\"264\" (264 items total)\n\"4/10\" (4 stacks, plus 10 more)\n\"4/\" (4 stacks)\n\"+1/\" (add 1 stack to the existing amount)\n\"-1/\" (subtract 1 stack from the existing amount)\n\nNote that <b>stack size varies depending on the item</b>!  Most items can hold 64 in a stack, but some can only hold 16 (like Signs) and others are not stackable at all (like armor)!");

		quantity = new QuantityTextField();
		quantity.addKeyListener(new KeyAdapter() {
			//do not use "addActionListener()" because this ends up causing a "key released" event to fire while the item name field is focused
			@Override
			public void keyReleased(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ENTER) {
					addItem();
				}
			}
		});

		lowThresholdLabel = new HelpLabel("Theshold:", "This number defines when an item will be considered \"low in stock\".");

		lowThreshold = new QuantityTextField();
		lowThreshold.addKeyListener(new KeyAdapter() {
			//do not use "addActionListener()" because this ends up causing a "key released" event to fire while the item name field is focused
			@Override
			public void keyReleased(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ENTER) {
					addItem();
				}
			}
		});

		table = new InventoryTable();
		table.setSortKeys(new RowSorter.SortKey(Column.REMAINING.ordinal(), SortOrder.ASCENDING), new RowSorter.SortKey(Column.ITEM_NAME.ordinal(), SortOrder.ASCENDING));

		///////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		JPanel outterPanel = new JPanel(new MigLayout("insets 0"));

		JPanel addPanel = new GroupPanel("Add Item");
		addPanel.add(new JLabel("Item Name:"));
		addPanel.add(item, "w :150:150");
		addPanel.add(quantityLabel);
		addPanel.add(quantity, "w :70:70");
		addPanel.add(lowThresholdLabel);
		addPanel.add(lowThreshold, "w :70:70");
		addPanel.add(addItem);
		outterPanel.add(addPanel, "wrap");

		outterPanel.add(filterPanel, "wrap");

		tableScrollPane = new MyJScrollPane(table);
		outterPanel.add(tableScrollPane, "w 100%, h 100%");

		add(outterPanel, "h 100%, align center");

		refresh();
	}

	private void addItem() {
		//check for field to be populated
		String itemStr = item.getText();
		String quantityStr = quantity.getText();
		if (itemStr.isEmpty() || quantityStr.isEmpty()) {
			return;
		}

		//get quantity value
		int stackSize = index.getStackSize(itemStr);
		boolean add = quantity.isAdd();
		int qty;
		try {
			qty = quantity.getQuantity(stackSize);
		} catch (NumberFormatException e) {
			DialogBuilder.error() //@formatter:off
				.parent(this)
				.title("Error")
				.text("Invalid quantity value.")
			.show(); //@formatter:on

			quantity.requestFocusInWindow();
			return;
		}

		//get threshold value
		Integer threshold;
		try {
			threshold = lowThreshold.getQuantity(stackSize);
		} catch (NumberFormatException e) {
			DialogBuilder.error() //@formatter:off
				.parent(this)
				.title("Error")
				.text("Invalid threshold value.")
			.show(); //@formatter:on

			lowThreshold.requestFocusInWindow();
			return;
		}

		//update database
		int inventoryId;
		try {
			inventoryId = dao.upsertInventory(itemStr, qty, add);
			if (threshold != null) {
				dao.updateInventoryLowThreshold(itemStr, threshold);
			}
			dao.commit();
		} catch (SQLException e) {
			dao.rollback();
			throw new RuntimeException(e);
		}

		//find the table row that corresponds with the updated record
		int rowIndex = -1;
		for (int i = 0; i < table.model.getRowCount(); i++) {
			Row row = table.model.data.get(i);
			if (row.inventory.getId() == inventoryId) {
				rowIndex = i;
				break;
			}
		}

		table.resetJustInserted();

		if (rowIndex == -1) {
			//no row found, so insert a new one

			Inventory inventory = new Inventory();
			inventory.setId(inventoryId);
			inventory.setItem(itemStr);
			inventory.setQuantity(qty);
			if (threshold != null) {
				inventory.setLowInStockThreshold(threshold);
			}

			Row newRow = new Row(inventory);
			newRow.justInserted = true;
			table.model.data.add(newRow);
			table.model.fireTableRowsInserted(table.model.getRowCount() - 1, table.model.getRowCount() - 1);
		} else {
			//update row with new data
			Row row = table.model.data.get(rowIndex);
			if (add) {
				qty = row.inventory.getQuantity() + qty;
			}
			row.inventory.setQuantity(qty);
			if (threshold != null) {
				row.inventory.setLowInStockThreshold(threshold);
			}
			row.justInserted = true;
			table.model.fireTableRowsUpdated(rowIndex, rowIndex);
		}

		item.setText("");
		quantity.setText("");
		//don't clear threshold
		item.requestFocusInWindow();
	}

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;

		//re-render the "quantity" column
		int col = Column.REMAINING.ordinal();
		for (int row = 0; row < table.model.getRowCount(); row++) {
			table.model.fireTableCellUpdated(row, col);
		}
	}

	public void refresh() {
		Collection<Inventory> inventory;
		try {
			inventory = dao.getInventory();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		table.setData(inventory);
		filterPanel.clear();
		table.filter(new FilterList(), ALL);
	}

	private static class Row {
		private final Inventory inventory;
		private boolean selected = false;
		private boolean idUnknown = false;
		private boolean justInserted = false;

		public Row(Inventory inventory) {
			this.inventory = inventory;
		}
	}

	private class InventoryTable extends JTable {
		private final Column columns[] = Column.values();
		private final InventoryTableModel model;
		private final TableRowSorter<InventoryTableModel> rowSorter;

		public InventoryTable() {
			getTableHeader().setReorderingAllowed(false);
			setRowHeight(24);

			setDefaultRenderer(Row.class, new InventoryTableRenderer());

			model = new InventoryTableModel();
			setModel(model);

			rowSorter = createRowSorter();
			setRowSorter(rowSorter);

			setColumns();

			setSelectionModel();
		}

		public void setSortKeys(RowSorter.SortKey... sortKeys) {
			rowSorter.setSortKeys(Arrays.asList(sortKeys));
		}

		public void resetJustInserted() {
			for (int i = 0; i < model.data.size(); i++) {
				Row row = model.data.get(i);
				if (row.justInserted) {
					row.justInserted = false;
					model.fireTableRowsUpdated(i, i);
				}
			}
		}

		public List<Row> getSelected() {
			int rows = getRowCount();
			List<Row> selected = new ArrayList<>();
			for (int i = 0; i < rows; i++) {
				int modelRow = convertRowIndexToModel(i);
				Row row = model.data.get(modelRow);
				if (row.selected) {
					selected.add(row);
				}
			}
			return selected;
		}

		public void addAll(List<Row> rows) {
			for (int i = 0; i < model.getRowCount(); i++) {
				Row row = model.data.get(i);
				if (row.justInserted) {
					row.justInserted = false;
					model.fireTableRowsUpdated(i, i);
				}
			}

			for (Row row : rows) {
				row.justInserted = true;
			}

			int from = (model.getRowCount() == 0) ? 0 : model.getRowCount() - 1;
			model.data.addAll(rows);
			int to = model.getRowCount() - 1;
			model.fireTableRowsInserted(from, to);
		}

		public void setData(Collection<Inventory> inventory) {
			model.setData(inventory);
		}

		private void filter(FilterList filterItems, final CategoryInfo filterCategory) {
			if (filterItems.isEmpty() && filterCategory == ALL) {
				rowSorter.setRowFilter(null);
				return;
			}

			for (Row row : model.data) {
				row.selected = false;
			}
			filterPanel.setDeleteEnabled(false);

			rowSorter.setRowFilter(new RowFilter<InventoryTableModel, Integer>() {
				@Override
				public boolean include(RowFilter.Entry<? extends InventoryTableModel, ? extends Integer> entry) {
					int row = entry.getIdentifier();
					Row rowObj = entry.getModel().data.get(row);
					String itemName = rowObj.inventory.getItem();

					if (!filterItems.isFiltered(itemName)) {
						return false;
					}

					if (filterCategory == ALL) {
						return true;
					}

					if (filterCategory == LOW) {
						return rowObj.inventory.getQuantity() <= rowObj.inventory.getLowInStockThreshold();
					}

					CategoryInfo categories[] = index.getItemCategories(itemName);
					if (filterCategory == MISC && categories.length == 0) {
						return true;
					}

					for (CategoryInfo c : categories) {
						if (c == filterCategory) {
							return true;
						}
					}

					return false;
				}
			});
		}

		private class InventoryTableModel extends AbstractTableModel {
			//Java 7 compiler wants this field to be public
			public final List<Row> data = new ArrayList<>();

			public void setData(Collection<Inventory> data) {
				this.data.clear();
				for (Inventory inv : data) {
					Row row = new Row(inv);
					this.data.add(row);
				}
				fireTableDataChanged();
			}

			@Override
			public int getColumnCount() {
				return columns.length;
			}

			@Override
			public String getColumnName(int index) {
				Column column = columns[index];
				return column.getName();
			}

			@Override
			public int getRowCount() {
				return data.size();
			}

			@Override
			public Object getValueAt(int row, int col) {
				return data.get(row);
			}

			@Override
			public Class<?> getColumnClass(int col) {
				return Row.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				Column column = columns[col];
				switch (column) {
				case REMAINING:
				case LOW_THRESHOLD:
					return true;
				default:
					return false;
				}
			}

			@Override
			public void setValueAt(Object value, int row, int col) {
				if (value == null) {
					return;
				}

				QuantityTextField textField = (QuantityTextField) value;
				Row rowObj = data.get(row);
				Inventory inv = rowObj.inventory;
				String item = inv.getItem();

				Column column = columns[col];
				switch (column) {
				case REMAINING:
					//get quantity value
					boolean add = textField.isAdd();
					int quantity;
					try {
						quantity = textField.getQuantity(index.getStackSize(item));
					} catch (NumberFormatException e) {
						DialogBuilder.error() //@formatter:off
							.parent(InventoryTab.this)
							.title("Error")
							.text("Invalid quantity value.")
						.show(); //@formatter:on
						return;
					}

					//value didn't change
					if (quantity == inv.getQuantity()) {
						return;
					}

					//update database
					try {
						dao.upsertInventory(item, quantity, add);
						dao.commit();
					} catch (SQLException e) {
						dao.rollback();
						throw new RuntimeException(e);
					}

					//update row with new data
					resetJustInserted();
					if (add) {
						quantity = inv.getQuantity() + quantity;
					}
					inv.setQuantity(quantity);
					rowObj.justInserted = true;
					break;

				case LOW_THRESHOLD:
					//get threshold
					int threshold;
					try {
						threshold = textField.getQuantity(index.getStackSize(item));
					} catch (NumberFormatException e) {
						DialogBuilder.error() //@formatter:off
							.parent(InventoryTab.this)
							.title("Error")
							.text("Invalid threshold value.")
						.show(); //@formatter:on
						return;
					}

					//value didn't change
					if (threshold == inv.getLowInStockThreshold()) {
						return;
					}

					//update database
					try {
						dao.updateInventoryLowThreshold(item, threshold);
						dao.commit();
					} catch (SQLException e) {
						dao.rollback();
						throw new RuntimeException(e);
					}

					//update row with new data
					resetJustInserted();
					inv.setLowInStockThreshold(threshold);
					rowObj.justInserted = true;
					break;

				default:
					break;
				}
			}

			@Override
			public void fireTableRowsUpdated(int firstRow, int lastRow) {
				super.fireTableRowsUpdated(firstRow, lastRow);
				filterPanel.setDeleteEnabled(isOneRowSelected());
			}

			@Override
			public void fireTableDataChanged() {
				super.fireTableDataChanged();
				filterPanel.setDeleteEnabled(isOneRowSelected());
			}
		}

		private class InventoryTableRenderer implements TableCellRenderer {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);
			private final Color insertedColor = new Color(255, 255, 192);
			private final Color lowInStockColor = new Color(255, 192, 192);

			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(4, 4, 4, 4));
			}
			private final JCheckBox checkbox = new JCheckBox();
			{
				checkbox.setOpaque(true);
			}

			private final QuantityFormatter qf = new QuantityFormatter();

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean hasFocus, int row, int col) {
				if (value == null) {
					return null;
				}

				Row rowObj = (Row) value;
				Inventory inv = rowObj.inventory;
				Column column = columns[col];
				resetComponents();

				JComponent component = null;
				switch (column) {
				case CHECKBOX:
					component = checkbox;

					checkbox.setSelected(rowObj.selected);
					break;

				case ITEM_NAME:
					component = label;

					if (rowObj.idUnknown) {
						label.setText("unknown ID: " + inv.getItem());
						if (!rowObj.selected) {
							label.setForeground(Color.RED);
						}
					} else {
						ImageIcon img = Images.getItemImage(inv.getItem());
						label.setText(inv.getItem());
						label.setIcon(img);
					}
					break;

				case REMAINING:
					component = label;

					int stacks = showQuantitiesInStacks ? index.getStackSize(inv.getItem()) : 1;
					String text = qf.format(inv.getQuantity(), stacks);
					label.setText(text);
					break;

				case LOW_THRESHOLD:
					component = label;

					stacks = showQuantitiesInStacks ? index.getStackSize(inv.getItem()) : 1;
					text = qf.format(inv.getLowInStockThreshold(), stacks);
					label.setText(text);
					break;
				}

				//set the background color of the row
				if (rowObj.selected) {
					UIDefaultsWrapper.assignListFormats(component, true);
				} else if (rowObj.justInserted) {
					component.setForeground(UIDefaultsWrapper.getLabelForeground());
					component.setBackground(insertedColor);
				} else if (inv.getQuantity() <= inv.getLowInStockThreshold()) {
					component.setForeground(UIDefaultsWrapper.getLabelForeground());
					component.setBackground(lowInStockColor);
				} else {
					Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
					component.setForeground(UIDefaultsWrapper.getLabelForeground());
					component.setBackground(color);
				}

				return component;
			}

			private void resetComponents() {
				label.setIcon(null);
			}
		}

		private TableRowSorter<InventoryTableModel> createRowSorter() {
			TableRowSorter<InventoryTableModel> rowSorter = new TableRowSorter<>(model);

			rowSorter.setSortable(Column.CHECKBOX.ordinal(), false);
			rowSorter.setComparator(Column.ITEM_NAME.ordinal(), (Row one, Row two) -> one.inventory.getItem().compareToIgnoreCase(two.inventory.getItem()));
			rowSorter.setComparator(Column.REMAINING.ordinal(), Comparator.comparing((Row r) -> r.inventory.getQuantity()));
			rowSorter.setComparator(Column.LOW_THRESHOLD.ordinal(), Comparator.comparing((Row r) -> r.inventory.getLowInStockThreshold()));
			rowSorter.setSortsOnUpdates(false);

			return rowSorter;
		}

		private void setColumns() {
			TableColumn checkboxColumn = columnModel.getColumn(Column.CHECKBOX.ordinal());
			checkboxColumn.setMaxWidth(30);
			checkboxColumn.setResizable(false);

			TableColumn itemNameColumn = columnModel.getColumn(Column.ITEM_NAME.ordinal());
			itemNameColumn.setPreferredWidth(200);

			TableColumn remainingColumn = columnModel.getColumn(Column.REMAINING.ordinal());
			remainingColumn.setPreferredWidth(50);
			remainingColumn.setCellEditor(new QuantityEditor());

			TableColumn lowThresholdColumn = columnModel.getColumn(Column.LOW_THRESHOLD.ordinal());
			lowThresholdColumn.setPreferredWidth(50);
			lowThresholdColumn.setCellEditor(new QuantityEditor());
		}

		private void clickCell(int rowView, int colView) {
			int col = convertColumnIndexToModel(colView);
			Column column = columns[col];
			if (column == Column.REMAINING || column == Column.LOW_THRESHOLD) {
				return;
			}

			int row = convertRowIndexToModel(rowView);
			Row rowObj = model.data.get(row);
			rowObj.selected = !rowObj.selected;

			//re-render table row
			model.fireTableRowsUpdated(row, row);
		}

		public boolean isOneRowSelected() {
			for (Row row : model.data) {
				if (row.selected) {
					return true;
				}
			}
			return false;
		}

		private void setSelectionModel() {
			addMouseListener(new MouseAdapter() {
				private int mousePressedCol, mousePressedRow;

				@Override
				public void mousePressed(MouseEvent event) {
					int colView = columnAtPoint(event.getPoint());
					int rowView = rowAtPoint(event.getPoint());
					if (colView < 0 || rowView < 0) {
						return;
					}

					mousePressedCol = colView;
					mousePressedRow = rowView;
				}

				@Override
				public void mouseReleased(MouseEvent event) {
					int colView = columnAtPoint(event.getPoint());
					int rowView = rowAtPoint(event.getPoint());
					if (colView < 0 || rowView < 0) {
						return;
					}

					if (colView == mousePressedCol && rowView == mousePressedRow) {
						clickCell(rowView, colView);
					}
				}
			});
		}

		private class QuantityEditor extends AbstractCellEditor implements TableCellEditor {
			private final QuantityTextField textField = new QuantityTextField();
			{
				textField.addActionListener(event -> fireEditingStopped());

				//select all the text when the textbox gains focus
				textField.addFocusListener(new FocusListener() {
					@Override
					public void focusGained(FocusEvent event) {
						textField.selectAll();
					}

					@Override
					public void focusLost(FocusEvent event) {
						//empty
					}
				});
			}

			@Override
			public Object getCellEditorValue() {
				return textField;
			}

			@Override
			public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int row, int col) {
				if (value == null) {
					return null;
				}

				Row rowObj = (Row) value;
				Inventory inv = rowObj.inventory;
				Integer stackSize = showQuantitiesInStacks ? index.getStackSize(inv.getItem()) : null;

				Column column = columns[col];
				switch (column) {
				case REMAINING:
					textField.setQuantity(inv.getQuantity(), stackSize);
					break;

				case LOW_THRESHOLD:
					textField.setQuantity(inv.getLowInStockThreshold(), stackSize);
					break;

				default:
					return null;
				}

				return textField;
			}
		}
	}

	@Override
	public String exportData(ExportType type) {
		int rows = table.getRowCount();
		List<Inventory> inventory = new ArrayList<>(rows);
		for (int i = 0; i < rows; i++) {
			int modelRow = table.convertRowIndexToModel(i);
			Row row = table.model.data.get(modelRow);
			inventory.add(row.inventory);
		}

		switch (type) {
		case BBCODE:
			return QueryExporter.generateInventoryBBCode(inventory);
		case CSV:
			return QueryExporter.generateInventoryCsv(inventory);
		}

		return null;
	}

	private class CategoryComboBox extends JComboBox<CategoryInfo> {
		public CategoryComboBox() {
			List<CategoryInfo> categories = new ArrayList<>(index.getCategories());
			categories.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

			//add "all" and "misc" items
			categories.add(0, ALL);
			categories.add(1, LOW);
			categories.add(MISC);

			setModel(new DefaultComboBoxModel<>(categories.toArray(new CategoryInfo[0])));
			setEditable(false);

			setRenderer(new ListCellRenderer<CategoryInfo>() {
				private final Font orig;
				private final Font bold;
				private final JLabel label = new JLabel();
				{
					label.setOpaque(true);
					label.setBorder(new EmptyBorder(4, 4, 4, 4));

					shrinkFont(label);
					orig = label.getFont();
					bold = new Font(orig.getName(), Font.BOLD, orig.getSize());
				}

				@Override
				public Component getListCellRendererComponent(JList<? extends CategoryInfo> list, CategoryInfo category, int index, boolean selected, boolean hasFocus) {
					if (category == null) {
						return null;
					}

					label.setText(category.getName());

					ImageIcon icon = category.getIcon();
					if (icon != null) {
						icon = Images.scale(icon, 16);
					}
					label.setIcon(icon);

					Font font = (category == ALL || category == MISC || category == LOW) ? bold : orig;
					label.setFont(font);

					UIDefaultsWrapper.assignListFormats(label, selected);

					return label;
				}
			});
		}

		@Override
		public CategoryInfo getSelectedItem() {
			return (CategoryInfo) super.getSelectedItem();
		}
	}

	private class FilterPanel extends JPanel {
		private final JButton delete;
		private final JLabel filterByItemLabel;
		private final FilterTextField filterByItem;
		private final JLabel categoryLabel;
		private final CategoryComboBox category;
		private final ExportButton export;

		private final List<FilterListener> filterListeners = new ArrayList<>();

		public FilterPanel(ExportListener exportListener) {
			delete = new JButton("Delete");

			filterByItemLabel = new HelpLabel("<html><font size=2>Filter by item(s):", "<b>Filters the table by item.</b>\n<b>Example</b>: <code>wool,\"book\"</code>\n\nMultiple item names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the item name(s), press [<code>Enter</code>] to perform the filtering operation.");
			filterByItem = new FilterTextField();
			filterByItem.addActionListener(event -> fireFilterListeners());

			categoryLabel = new JLabel("<html><font size=2>Category:");
			category = new CategoryComboBox();
			category.addItemListener(event -> fireFilterListeners());

			export = new ExportButton(owner, exportListener);

			///////////////////

			setLayout(new MigLayout("insets 0"));

			shrinkFont(delete);
			add(delete);

			add(filterByItemLabel);
			add(filterByItem, "w 120");

			add(categoryLabel);
			shrinkFont(category);
			add(category);

			add(export);
		}

		private void fireFilterListeners() {
			for (FilterListener listener : filterListeners) {
				listener.filterPerformed(filterByItem.getNames(), category.getSelectedItem());
			}
		}

		public void addFilterListener(FilterListener listener) {
			filterListeners.add(listener);
		}

		public void addDeleteListener(ActionListener listener) {
			delete.addActionListener(listener);
		}

		public void setDeleteEnabled(boolean enabled) {
			delete.setEnabled(enabled);
		}

		public void clear() {
			filterByItem.setText("");
			category.setSelectedItem(ALL);
		}
	}

	private interface FilterListener {
		void filterPerformed(FilterList items, CategoryInfo category);
	}

	private class ChesterDialog extends JDialog {
		private final InventoryTable table;
		private final JButton done, cancel, remove;
		private boolean cancelled = true;
		private final ChesterThread thread;

		public ChesterDialog() {
			super(owner, "Chester", true);

			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent event) {
					//fired when the user closes the window
					thread.stopMe();
					cancelled = true;
				}
			});

			thread = new ChesterThread();
			thread.setDaemon(true);

			table = new InventoryTable();

			done = new JButton("Done");
			done.addActionListener(event -> {
				thread.stopMe();
				cancelled = false;
				dispose();
			});

			cancel = new JButton("Cancel");
			cancel.addActionListener(event -> {
				thread.stopMe();
				cancelled = true;
				dispose();
			});

			remove = new JButton("Remove");
			remove.addActionListener(event -> {
				List<Row> selected = table.getSelected();
				if (selected.isEmpty()) {
					return;
				}

				table.model.data.removeAll(selected);
				table.model.fireTableDataChanged();
			});

			GuiUtils.onEscapeKeyPress(this, event -> cancel.doClick());

			/////////////////////////

			setLayout(new MigLayout("insets 0"));

			add(new JLabel("<html><h3>Listening . . .</h3></html>", Images.LOADING, SwingConstants.LEFT), "align center, wrap");

			add(new JLabel("You may now login to Minecraft and start opening your shop chests."), "align center, wrap");
			add(new JLabel("Do not open the same chest twice, as its contents will be recorded twice."), "align center, wrap");

			MyJScrollPane pane = new MyJScrollPane(table);
			table.setFillsViewportHeight(true);
			add(pane, "align center, w 100%, h 100%, grow, wrap");

			add(done, "split 3, align center");
			add(remove);
			add(cancel);

			setSize(500, 400);
			setLocationRelativeTo(owner);

			thread.start();
		}

		public Collection<Inventory> getItems() {
			Map<String, Inventory> map = new HashMap<>();

			for (Row row : table.model.data) {
				if (row.idUnknown) {
					//skip unknown items
					continue;
				}

				Inventory item = row.inventory;
				Inventory mapItem = map.get(item.getItem());
				if (mapItem == null) {
					mapItem = new Inventory();
					mapItem.setItem(item.getItem());
					mapItem.setQuantity(item.getQuantity());
					map.put(mapItem.getItem(), mapItem);
				} else {
					mapItem.setQuantity(mapItem.getQuantity() + item.getQuantity());
				}
			}

			return map.values();
		}

		private class ChesterThread extends Thread {
			private final Instant started = Instant.now();
			private volatile boolean running = true;

			public void stopMe() {
				running = false;
				try {
					join();
				} catch (InterruptedException ignore) {
				}
			}

			@Override
			public void run() {
				Path dir = FileUtils.getUserDirectory().toPath().resolve(".chester");
				ItemIndex index = ItemIndex.instance();
				Pattern idRegex = Pattern.compile("[\\d]+(:[\\d]+)?");

				Predicate<Path> modifiedSinceStartup = file -> {
					try {
						return Files.getLastModifiedTime(file).toInstant().isAfter(started);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				};
				Predicate<Path> isChesterFile = file -> file.getFileName().toString().endsWith(".chester");
				Function<Path, ChesterFile> parseFile = file -> {
					ChesterFile chesterFile;
					try {
						chesterFile = ChesterFile.parse(file);
					} catch (IllegalArgumentException e) {
						logger.log(Level.SEVERE, "Problem parsing Chester file.", e);
						chesterFile = null;
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}

					try {
						Files.delete(file);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}

					return chesterFile;
				};

				while (running) {
					if (!Files.exists(dir)) {
						continue;
					}

					List<ChesterFile> files;
					try {
						files = Files.list(dir) //@formatter:off
							.filter(modifiedSinceStartup)
							.filter(isChesterFile)
							.map(parseFile)
							.filter(file -> file != null)
						.collect(Collectors.toList()); //@formatter:on
					} catch (Exception e) {
						throw new RuntimeException("Problem getting list of Chester files.", e);
					}

					for (ChesterFile file : files) {
						List<Row> rows = new ArrayList<>();
						for (Map.Entry<String, Integer> entry : file.getItems().entrySet()) {
							String id = entry.getKey();
							Integer quantity = entry.getValue();

							String name;
							boolean idUnknown;
							if (idRegex.matcher(id).matches()) {
								name = index.getDisplayNameFromMinecraftId(id);
								idUnknown = (name == null);
								if (idUnknown) {
									name = id;
								}
							} else {
								//it's an EMC-exclusive item, like Zombie Virus
								name = id;
								idUnknown = false;
							}

							Inventory item = new Inventory();
							item.setItem(name);
							item.setQuantity(quantity);

							Row row = new Row(item);
							row.idUnknown = idUnknown;
							rows.add(row);
						}
						table.addAll(rows);
					}

					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						running = false;
					}
				}
			}
		}
	}
}
