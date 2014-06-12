package emcshop.gui;

import static emcshop.util.GuiUtils.toolTipText;
import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatStacks;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
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

	private final MainFrame owner;
	private final DbDao dao;
	private final ItemIndex index = ItemIndex.instance();

	private final FilterPanel filterPanel;
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
		table.filter(new FilterList());
	}

	private static class Row {
		private final Inventory inventory;
		private boolean selected = false;
		private boolean idUnknown = false;

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

		public void add(Inventory inventory, boolean idUnknown) {
			Row row = new Row(inventory);
			row.idUnknown = idUnknown;
			model.data.add(row);
			model.fireTableRowsInserted(model.getRowCount() - 1, model.getRowCount() - 1);
		}

		public void setData(Collection<Inventory> inventory) {
			model.setData(inventory);
		}

		public void filter(final FilterList filterList) {
			if (filterList.isEmpty()) {
				rowSorter.setRowFilter(null);
				return;
			}

			RowFilter<InventoryTableModel, Integer> filter = new RowFilter<InventoryTableModel, Integer>() {
				@Override
				public boolean include(RowFilter.Entry<? extends InventoryTableModel, ? extends Integer> entry) {
					int row = entry.getIdentifier();
					Row rowObj = entry.getModel().data.get(row);

					String itemName = rowObj.inventory.getItem();
					return filterList.contains(itemName);
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
			}
		}

		private class InventoryTableRenderer implements TableCellRenderer {
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);
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
						label.setText("<html><font color=red>unknown ID: " + inv.getItem() + "</font></html>");
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
			rowSorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(Column.REMAINING.ordinal(), SortOrder.ASCENDING), new RowSorter.SortKey(Column.ITEM_NAME.ordinal(), SortOrder.ASCENDING)));

			return rowSorter;
		}

		private void setColumns() {
			TableColumn checkboxColumn = columnModel.getColumn(Column.CHECKBOX.ordinal());
			checkboxColumn.setMinWidth(30);
			checkboxColumn.setMaxWidth(30);
			checkboxColumn.setResizable(false);

			TableColumn itemNameColumn = columnModel.getColumn(Column.ITEM_NAME.ordinal());
			itemNameColumn.setPreferredWidth(200);

			TableColumn remainingColumn = columnModel.getColumn(Column.REMAINING.ordinal());
			remainingColumn.setPreferredWidth(50);
			remainingColumn.setCellEditor(new RemainingEditor());
		}

		private void setSelectionModel() {
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					int col = convertColumnIndexToModel(columnAtPoint(event.getPoint()));
					int row = convertRowIndexToModel(rowAtPoint(event.getPoint()));
					if (col < 0 || row < 0) {
						return;
					}

					Row rowObj = model.data.get(row);
					rowObj.selected = !rowObj.selected;

					//re-render table row
					model.fireTableRowsUpdated(row, row);
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
								for (Map.Entry<String, Integer> entry : chesterFile.getItems().entrySet()) {
									String id = entry.getKey();
									Integer quantity = entry.getValue();

									String name;
									boolean idUnknown;
									if (id.matches("[\\d:]+")) {
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
									table.add(item, idUnknown);
								}
								table.model.fireTableDataChanged();
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
