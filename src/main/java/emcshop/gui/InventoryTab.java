package emcshop.gui;

import static emcshop.util.GuiUtils.toolTipText;
import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatStacks;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
import javax.swing.JOptionPane;
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

import net.miginfocom.swing.MigLayout;

import org.apache.commons.io.FileUtils;

import emcshop.AppContext;
import emcshop.ExportType;
import emcshop.ItemIndex;
import emcshop.ItemIndex.CategoryInfo;
import emcshop.QueryExporter;
import emcshop.Settings;
import emcshop.db.DbDao;
import emcshop.db.Inventory;
import emcshop.gui.FilterPanel.ExportListener;
import emcshop.gui.FilterPanel.FilterList;
import emcshop.gui.FilterPanel.FilterListener;
import emcshop.gui.images.ImageManager;
import emcshop.util.ChesterFile;
import emcshop.util.GuiUtils;
import emcshop.util.UIDefaultsWrapper;

@SuppressWarnings("serial")
public class InventoryTab extends JPanel {
	private static final Logger logger = Logger.getLogger(InventoryTab.class.getName());
	private static final AppContext context = AppContext.instance();

	private final CategoryInfo ALL = new CategoryInfo(-1, "ALL", null);
	private final CategoryInfo MISC = new CategoryInfo(-1, "misc", null);

	private final MainFrame owner;
	private final DbDao dao;
	private final ItemIndex index = ItemIndex.instance();

	private final FilterPanel filterPanel;
	private final CategoryComboBox category;
	private final JButton addEdit;
	private final JButton delete;
	private final JButton chester;
	private final ItemSuggestField item;
	private final JLabel quantityLabel;
	private final QuantityTextField quantity;

	private InventoryTable table;
	private boolean showQuantitiesInStacks;
	private MyJScrollPane tableScrollPane;

	/**
	 * Defines all of the columns in this table. The order in which the enums
	 * are defined is the order that they will appear in the table.
	 */
	private enum Column {
		CHECKBOX(""), ITEM_NAME("Item Name"), REMAINING("Remaining");

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
		dao = context.get(DbDao.class);
		showQuantitiesInStacks = context.get(Settings.class).isShowQuantitiesInStacks();

		category = new CategoryComboBox();

		filterPanel = new FilterPanel();
		filterPanel.setVisible(true, false, false);
		filterPanel.addFilterListener(new FilterListener() {
			@Override
			public void filterPerformed(FilterList items, FilterList players) {
				table.filter(items);
				tableScrollPane.scrollToTop();
			}
		});
		filterPanel.addExportListener(new ExportListener() {
			@Override
			public void exportPerformed(ExportType type) {
				String text = export(type);
				GuiUtils.copyToClipboard(text);
				JOptionPane.showMessageDialog(InventoryTab.this, "Copied to clipboard.");
			}
		});

		addEdit = new JButton("Add/Edit");
		addEdit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				addItem();
				filterPanel.clear();
			}
		});

		delete = new JButton("Delete");
		delete.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				List<Row> selected = table.getSelected();
				if (selected.isEmpty()) {
					return;
				}

				List<Integer> selectedIds = new ArrayList<Integer>(selected.size());
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
			}
		});

		chester = new JButton("Start Chester");
		chester.setToolTipText(toolTipText("Allows you to record your shop's inventory by simply opening your shop chests in-game.  Requires the \"Chester\" mod to be installed on your Minecraft client."));
		chester.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
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
			}
		});

		//ClickableLabel chesterUrl = new ClickableLabel("<html><font color=navy><u>Download Chester</u></font></html>", "http://github.com/mangstadt/chester");

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

		table = new InventoryTable();
		table.setSortKeys(new RowSorter.SortKey(Column.REMAINING.ordinal(), SortOrder.ASCENDING), new RowSorter.SortKey(Column.ITEM_NAME.ordinal(), SortOrder.ASCENDING));

		///////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		JPanel leftTop = new JPanel(new MigLayout());

		leftTop.add(new JLabel("Item Name:"));
		leftTop.add(quantityLabel, "wrap");
		leftTop.add(item, "w 300");
		leftTop.add(quantity, "w 150, wrap");
		leftTop.add(addEdit, "span 2, split 2");
		leftTop.add(delete, "wrap");

		add(leftTop, "span 1 2, w 300!, growy");

		filterPanel.add(new JLabel("<html><font size=2>Category:"), filterPanel.getComponentCount() - 1);
		filterPanel.add(category, "w 150", filterPanel.getComponentCount() - 1);
		add(filterPanel, "wrap");

		tableScrollPane = new MyJScrollPane(table);
		add(tableScrollPane, "grow, w 100%, h 100%, wrap");

		refresh();
	}

	private void addItem() {
		String itemStr = item.getText();
		String quantityStr = quantity.getText();
		if (itemStr.isEmpty() || quantityStr.isEmpty()) {
			return;
		}

		try {
			upsertItem(itemStr, quantity);
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Invalid quantity value.", "Error", JOptionPane.ERROR_MESSAGE);
			quantity.requestFocusInWindow();
			return;
		}

		item.setText("");
		quantity.setText("");
		item.requestFocusInWindow();
		refresh();

		for (int i = 0; i < table.model.getRowCount(); i++) {
			Row row = table.model.data.get(i);
			if (row.inventory.getItem().equalsIgnoreCase(itemStr)) {
				row.justInserted = true;
				table.model.fireTableRowsUpdated(i, i);
				break;
			}
		}
	}

	private void upsertItem(String item, QuantityTextField textField) {
		boolean add = quantity.isAdd();
		int quantity = textField.getQuantity(index.getStackSize(item));

		try {
			dao.upsertInventory(item, quantity, add);
			dao.commit();
		} catch (SQLException e) {
			dao.rollback();
			throw new RuntimeException(e);
		}
	}

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;

		//re-render the "quantity" column
		for (int row = 0; row < table.model.getRowCount(); row++) {
			table.model.fireTableCellUpdated(row, Column.REMAINING.ordinal());
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
		category.setSelectedItem(ALL);

		table.filterItems = new FilterList();
		table.filterCategory = ALL;
		table.filter();
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

		private FilterList filterItems = new FilterList();
		private CategoryInfo filterCategory = ALL;

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

		public List<Row> getSelected() {
			int rows = getRowCount();
			List<Row> selected = new ArrayList<Row>();
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

		public void filter(FilterList filterList) {
			filterItems = filterList;
			filter();
		}

		public void filter(CategoryInfo category) {
			filterCategory = category;
			filter();
		}

		private void filter() {
			if (filterItems.isEmpty() && filterCategory == ALL) {
				rowSorter.setRowFilter(null);
				return;
			}
			
			for (Row row : model.data){
				row.selected = false;
			}

			RowFilter<InventoryTableModel, Integer> filter = new RowFilter<InventoryTableModel, Integer>() {
				@Override
				public boolean include(RowFilter.Entry<? extends InventoryTableModel, ? extends Integer> entry) {
					int row = entry.getIdentifier();
					Row rowObj = entry.getModel().data.get(row);
					String itemName = rowObj.inventory.getItem();

					boolean item = (filterItems.isEmpty() || filterItems.contains(itemName));
					if (!item) {
						return false;
					}

					if (filterCategory == ALL) {
						return true;
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

			};
			rowSorter.setRowFilter(filter);
		}

		private class InventoryTableModel extends AbstractTableModel {
			private final List<Row> data = new ArrayList<Row>();

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
					return true;
				default:
					return false;
				}
			}

			@Override
			public void setValueAt(Object value, int row, int col) {
				Column column = columns[col];
				if (column != Column.REMAINING) {
					return;
				}

				QuantityTextField textField = (QuantityTextField) value;
				Row rowObj = data.get(row);
				Inventory inv = rowObj.inventory;

				//update database
				String item = inv.getItem();
				try {
					upsertItem(item, textField);
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(InventoryTab.this, "Invalid quantity value.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}

				//update Inventory object
				int quantity = textField.getQuantity(index.getStackSize(item));
				boolean add = textField.isAdd();
				if (add) {
					quantity = inv.getQuantity() + quantity;
				}
				inv.setQuantity(quantity);

				rowObj.justInserted = true;
				for (int i = 0; i < getRowCount(); i++) {
					Row r = data.get(i);
					if (r.justInserted) {
						r.justInserted = false;
						fireTableRowsUpdated(i, i);
					}
				}
			}
		}

		private class InventoryTableRenderer implements TableCellRenderer {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);
			private final Color insertedColor = new Color(255, 255, 192);
			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(4, 4, 4, 4));
			}
			private final JCheckBox checkbox = new JCheckBox();
			{
				checkbox.setOpaque(true);
			}

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
						ImageIcon img = ImageManager.getItemImage(inv.getItem());
						label.setText(inv.getItem());
						label.setIcon(img);
					}
					break;

				case REMAINING:
					component = label;

					String text = showQuantitiesInStacks ? formatStacks(inv.getQuantity(), index.getStackSize(inv.getItem()), false) : formatQuantity(inv.getQuantity(), false);
					label.setText(text);
					break;
				}

				//set the background color of the row
				if (rowObj.selected) {
					UIDefaultsWrapper.assignListFormats(component, true);
				} else if (rowObj.justInserted) {
					component.setForeground(UIDefaultsWrapper.getLabelForeground());
					component.setBackground(insertedColor);
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
			TableRowSorter<InventoryTableModel> rowSorter = new TableRowSorter<InventoryTableModel>(model);

			rowSorter.setSortable(Column.CHECKBOX.ordinal(), false);
			rowSorter.setComparator(Column.ITEM_NAME.ordinal(), new Comparator<Row>() {
				@Override
				public int compare(Row one, Row two) {
					return one.inventory.getItem().compareToIgnoreCase(two.inventory.getItem());
				}
			});
			rowSorter.setComparator(Column.REMAINING.ordinal(), new Comparator<Row>() {
				@Override
				public int compare(Row one, Row two) {
					return one.inventory.getQuantity().compareTo(two.inventory.getQuantity());
				}
			});
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
			remainingColumn.setCellEditor(new RemainingEditor());
		}

		private void clickCell(int rowView, int colView) {
			int col = convertColumnIndexToModel(colView);
			Column column = columns[col];
			if (column == Column.REMAINING) {
				return;
			}

			int row = convertRowIndexToModel(rowView);
			Row rowObj = model.data.get(row);
			rowObj.selected = !rowObj.selected;

			//re-render table row
			model.fireTableRowsUpdated(row, row);
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

		private class RemainingEditor extends AbstractCellEditor implements TableCellEditor {
			private final QuantityTextField textField = new QuantityTextField();
			{
				textField.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent event) {
						fireEditingStopped();
					}
				});

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

				Column column = columns[col];
				if (column != Column.REMAINING) {
					return null;
				}

				Row rowObj = (Row) value;
				Inventory inv = rowObj.inventory;
				Integer stackSize = showQuantitiesInStacks ? index.getStackSize(inv.getItem()) : null;
				textField.setQuantity(inv.getQuantity(), stackSize);

				return textField;
			}
		}
	}

	public String export(ExportType type) {
		int rows = table.getRowCount();
		List<Inventory> inventory = new ArrayList<Inventory>(rows);
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

	private class CategoryComboBox extends JComboBox implements ItemListener {
		public CategoryComboBox() {
			Set<CategoryInfo> categoriesSet = index.getCategories();

			List<CategoryInfo> categories = new ArrayList<CategoryInfo>(categoriesSet);
			Collections.sort(categories, new Comparator<CategoryInfo>() {
				@Override
				public int compare(CategoryInfo a, CategoryInfo b) {
					return a.getName().compareToIgnoreCase(b.getName());
				}
			});
			categories.add(0, ALL);
			categories.add(MISC);

			setModel(new DefaultComboBoxModel(categories.toArray()));
			setEditable(false);

			addItemListener(this);

			Font orig = getFont();
			setFont(new Font(orig.getName(), orig.getStyle(), orig.getSize() - 2));

			setRenderer(new ListCellRenderer() {
				private final Font orig;
				private final Font bold;
				private final JLabel label = new JLabel();
				{
					label.setOpaque(true);
					label.setBorder(new EmptyBorder(4, 4, 4, 4));

					Font font = label.getFont();
					orig = new Font(font.getName(), font.getStyle(), font.getSize() - 2);
					label.setFont(orig);

					bold = new Font(orig.getName(), Font.BOLD, orig.getSize());
				}

				@Override
				public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
					if (value == null) {
						return null;
					}

					CategoryInfo category = (CategoryInfo) value;
					label.setText(category.getName());

					ImageIcon icon = category.getIcon();
					if (icon != null) {
						icon = ImageManager.scale(icon, 16);
					}
					label.setIcon(icon);

					Font font = (category == ALL || category == MISC) ? bold : orig;
					label.setFont(font);

					UIDefaultsWrapper.assignListFormats(label, selected);

					return label;
				}
			});
		}

		@Override
		public void itemStateChanged(ItemEvent event) {
			CategoryInfo selected = getSelectedItem();
			table.filter(selected);
		}

		@Override
		public CategoryInfo getSelectedItem() {
			return (CategoryInfo) super.getSelectedItem();
		}
	}

	private class ChesterDialog extends JDialog {
		private final InventoryTable table;
		private final JButton done, cancel, remove;
		private boolean cancelled = true;
		private final ChesterThread thread;

		public ChesterDialog() {
			super(owner, "Chester", true);

			GuiUtils.onEscapeKeyPress(this, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					cancel.doClick();
				}
			});

			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent arg0) {
					//fired when the user closes the window
					thread.stopMe();
					cancelled = true;
				}
			});

			done = new JButton("Done");
			done.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					thread.stopMe();
					cancelled = false;
					dispose();
				}
			});

			cancel = new JButton("Cancel");
			cancel.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					thread.stopMe();
					cancelled = true;
					dispose();
				}
			});

			remove = new JButton("Remove");
			remove.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					List<Row> selected = table.getSelected();
					if (selected.isEmpty()) {
						return;
					}

					table.model.data.removeAll(selected);
					table.model.fireTableDataChanged();
				}
			});

			table = new InventoryTable();

			/////////////////////////

			setLayout(new MigLayout("insets 0"));

			add(new JLabel("<html><h3>Listening . . .</h3></html>", ImageManager.getLoading(), SwingConstants.LEFT), "align center, wrap");

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

			thread = new ChesterThread();
			thread.setDaemon(true);
			thread.start();
		}

		public Collection<Inventory> getItems() {
			Map<String, Inventory> map = new HashMap<String, Inventory>();

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
			private final long started = System.currentTimeMillis();
			private volatile boolean running = true;

			public void stopMe() {
				running = false;
				try {
					join();
				} catch (InterruptedException e) {
					//ignore
				}
			}

			@Override
			public void run() {
				File dir = new File(FileUtils.getUserDirectory(), ".chester");
				ItemIndex index = ItemIndex.instance();
				Pattern idRegex = Pattern.compile("[\\d]+(:[\\d]+)?");
				while (running) {
					try {
						Thread.sleep(100);

						if (!dir.exists()) {
							continue;
						}

						for (File file : dir.listFiles()) {
							if (file.lastModified() < started) {
								//file was there before EMC Shopkeeper started listening for files
								continue;
							}

							if (!file.getName().endsWith(".chester")) {
								//file is not a Chester file
								continue;
							}

							try {
								ChesterFile chesterFile = ChesterFile.parse(file);
								List<Row> rows = new ArrayList<Row>();
								for (Map.Entry<String, Integer> entry : chesterFile.getItems().entrySet()) {
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
							} catch (Throwable t) {
								logger.log(Level.SEVERE, "Problem reading Chester file.", t);
							}
							file.delete();
						}
					} catch (InterruptedException e) {
						running = false;
					}
				}
			}
		}
	}
}
