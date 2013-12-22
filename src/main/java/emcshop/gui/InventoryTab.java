package emcshop.gui;

import static emcshop.util.NumberFormatter.formatQuantity;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import emcshop.ItemIndex;
import emcshop.QueryExporter;
import emcshop.db.DbDao;
import emcshop.db.Inventory;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.CheckBoxColumn;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class InventoryTab extends JPanel {
	private final MainFrame owner;
	private final DbDao dao;

	private final JButton addEdit;
	private final JButton delete;
	private final JButton chester;
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
		CHECKBOX(""), ITEM_NAME("Item Name"), REMAINING("Total (stacks/remainder)");

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
				for (Row row : table.displayedRows) {
					if (row.selected) {
						Integer id = row.inventory.getId();
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

		chester = new JButton("Start Chester");
		chester.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				ChesterDialog dialog = new ChesterDialog();
				dialog.setVisible(true);
				if (dialog.cancelled) {
					return;
				}

				Collection<Inventory> items = compressItems(dialog.items);
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

		item = new ItemSuggestField(owner);

		quantityLabel = new HelpLabel("Qty:", "Tip: You can specify the quantity in \"stacks\" (groups of 64) instead of having to specify the exact number.\n\n<b>Example inputs</b>:\n\"5/23\" (5 stacks, plus 23 more)\n\"5/\" (5 stacks)\n\"5\" (5 items total)\n\nPrepending a \"+\" will add the quantity to the existing total.\n\n<b>Example inputs</b>:\n\"+1/\" (add 1 stack to the existing amount)");

		quantity = new JTextField();
		quantity.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				addItem();
			}
		});

		export = new ExportComboBoxImpl();

		filterByItemLabel = new HelpLabel("Filter by item(s):", "<b>Filters the table by item.</b>\n<b>Example</b>: <code>wool,\"book\"</code>\n\nMultiple item names can be entered, separated by commas.\n\nExact name matches will be peformed on names that are enclosed in double quotes.  Otherwise, partial name matches will be performed.\n\nAfter entering the item name(s), press [<code>Enter</code>] to perform the filtering operation.");

		filterByItem = new FilterTextField();
		filterByItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> filteredItems = filterByItem.getNames();
				table.filter(filteredItems);
				tableScrollPane.scrollToTop();
			}
		});

		List<Inventory> inventory = getInventory();
		Collections.sort(inventory, new Comparator<Inventory>() {
			@Override
			public int compare(Inventory a, Inventory b) {
				return a.getQuantity() - b.getQuantity();
			}
		});
		table = new InventoryTable(inventory);

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

	private Collection<Inventory> compressItems(List<Inventory> items) {
		Map<String, Inventory> map = new HashMap<String, Inventory>();

		for (Inventory item : items) {
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
		//e.g. "+1/" means "add 1 stack to the existing quantity"
		Integer quantityValue;
		boolean add;
		try {
			if (quantityStr.startsWith("+")) {
				add = true;
				quantityStr = quantityStr.substring(1);
			} else {
				add = false;
			}

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
			dao.upsertInventory(itemStr, quantityValue, add);
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

	private List<Inventory> getInventory() {
		List<Inventory> inventory;
		try {
			inventory = dao.getInventory();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return inventory;
	}

	public void refresh() {
		List<Inventory> inventory = getInventory();
		table.refresh(inventory);
		filterByItem.setText("");
	}

	private class Row {
		private final Inventory inventory;
		private boolean selected = false;

		public Row(Inventory inventory) {
			this.inventory = inventory;
		}
	}

	private class InventoryTable extends JTable {
		private Column prevColumnClicked;
		private boolean ascending;
		private CheckBoxColumn checkboxes;
		private List<Row> rows = new ArrayList<Row>();
		private List<Row> displayedRows;

		public InventoryTable(List<Inventory> inventory) {
			prevColumnClicked = Column.REMAINING;
			ascending = true;

			for (Inventory inv : inventory) {
				rows.add(new Row(inv));
			}
			displayedRows = rows;

			setModel();
			setColumns();

			getTableHeader().setReorderingAllowed(false);
			setColumnSelectionAllowed(false);
			setRowSelectionAllowed(false);
			setCellSelectionEnabled(false);
			setRowHeight(24);

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

					if (column == prevColumnClicked) {
						ascending = !ascending;
					} else {
						prevColumnClicked = column;
						ascending = true;
					}

					//select all checkboxes
					if (column == Column.CHECKBOX) {
						AbstractTableModel model = (AbstractTableModel) getModel();
						for (int i = 0; i < displayedRows.size(); i++) {
							checkboxes.setCheckboxSelected(i, ascending);
							displayedRows.get(i).selected = ascending;
							model.fireTableCellUpdated(i, Column.CHECKBOX.ordinal());
						}
						return;
					}

					sortData();
				}
			});

			setDefaultRenderer(Row.class, new TableCellRenderer() {
				private final Color evenRowColor = new Color(255, 255, 255);
				private final Color oddRowColor = new Color(240, 240, 240);

				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
					final Row rowObj = (Row) value;

					JLabel label = null;
					Inventory inv = rowObj.inventory;

					if (col == Column.ITEM_NAME.ordinal()) {
						ImageIcon img = ImageManager.getItemImage(inv.getItem());
						label = new JLabel(inv.getItem(), img, SwingConstants.LEFT);
					} else if (col == Column.REMAINING.ordinal()) {
						int quantity = inv.getQuantity();
						int stacks = quantity / 64;
						int remainder = quantity % 64;

						label = new JLabel(formatQuantity(quantity, false) + " (" + stacks + "/" + remainder + ")");
					}

					//set the background color of the row
					Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
					label.setOpaque(true);
					label.setBackground(color);

					return label;
				}
			});
		}

		public void refresh(List<Inventory> inventory) {
			Set<String> selectedItems = new HashSet<String>();
			for (Row row : rows) {
				if (row.selected) {
					selectedItems.add(row.inventory.getItem());
				}
			}

			rows.clear();
			for (Inventory inv : inventory) {
				Row row = new Row(inv);
				if (selectedItems.contains(inv.getItem())) {
					row.selected = true;
				}
				rows.add(row);
			}
			displayedRows = rows;

			sortData();
			setModel();
			setColumns();
		}

		public void filter(List<String> filteredItemNames) {
			if (filteredItemNames.isEmpty()) {
				displayedRows = rows;
				sortData();
			} else {
				displayedRows = new ArrayList<Row>();
				for (Row row : rows) {
					String itemName = row.inventory.getItem().toLowerCase();
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
							displayedRows.add(row);
							break;
						}
					}
				}
				refresh();
			}
		}

		private void sortData() {
			if (prevColumnClicked == Column.CHECKBOX) {
				return;
			}

			Collections.sort(displayedRows, new Comparator<Row>() {
				@Override
				public int compare(Row a, Row b) {
					if (!ascending) {
						Row temp = a;
						a = b;
						b = temp;
					}

					Inventory invA = a.inventory;
					Inventory invB = b.inventory;

					switch (prevColumnClicked) {
					case ITEM_NAME:
						return invA.getItem().compareToIgnoreCase(invB.getItem());
					case REMAINING:
						return invA.getQuantity() - invB.getQuantity();
					default:
						return 0;
					}
				}
			});

			refresh();
		}

		private void refresh() {
			AbstractTableModel model = (AbstractTableModel) getModel();
			model.fireTableStructureChanged();

			setColumns();
		}

		private void setColumns() {
			checkboxes = new CheckBoxColumn(this, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent event) {
					String split[] = event.getActionCommand().split(" "); //e.g. "2 true"
					int row = Integer.valueOf(split[0]);
					boolean selected = Boolean.valueOf(split[1]);

					displayedRows.get(row).selected = selected;

					AbstractTableModel model = (AbstractTableModel) getModel();
					model.fireTableCellUpdated(row, Column.CHECKBOX.ordinal());
				}
			}, Column.CHECKBOX.ordinal());

			//reset the values of the checkboxes
			for (int i = 0; i < displayedRows.size(); i++) {
				Row row = displayedRows.get(i);
				checkboxes.setCheckboxSelected(i, row.selected);

				AbstractTableModel model = (AbstractTableModel) getModel();
				model.fireTableCellUpdated(i, Column.CHECKBOX.ordinal());
			}

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
					if (prevColumnClicked == column && prevColumnClicked != Column.CHECKBOX) {
						String arrow = (ascending) ? "\u25bc" : "\u25b2";
						text = arrow + " " + text;
					}
					return text;
				}

				@Override
				public int getRowCount() {
					return displayedRows.size();
				}

				@Override
				public Object getValueAt(int row, int col) {
					if (col == Column.CHECKBOX.ordinal()) {
						return "";
					}
					return displayedRows.get(row);
				}

				public Class<?> getColumnClass(int col) {
					if (col == Column.CHECKBOX.ordinal()) {
						return String.class;
					}
					return Row.class;
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

	private class ExportComboBoxImpl extends ExportComboBox {
		public ExportComboBoxImpl() {
			super(owner);
		}

		@Override
		public String bbCode() {
			return QueryExporter.generateInventoryBBCode(getInventoryObjects());
		}

		@Override
		public String csv() {
			return QueryExporter.generateInventoryCsv(getInventoryObjects());
		}

		private List<Inventory> getInventoryObjects() {
			List<Inventory> inventory = new ArrayList<Inventory>();
			for (Row row : table.displayedRows) {
				inventory.add(row.inventory);
			}
			return inventory;
		}
	}

	private class ChesterDialog extends JDialog {
		private final InventoryTable table;
		private final JButton ok, cancel;
		private final List<Inventory> items = new ArrayList<Inventory>();
		private boolean cancelled = false;
		private final ChesterThread thread;

		public ChesterDialog() {
			super(owner, "Listening to Chester", true);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			setResizable(false);
			GuiUtils.onEscapeKeyPress(this, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					cancel.doClick();
				}
			});

			ok = new JButton("Ok");
			ok.addActionListener(new ActionListener() {
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

			table = new InventoryTable(new ArrayList<Inventory>());

			/////////////////////////

			setLayout(new MigLayout("insets 0"));

			add(new JLabel("<html><h3>Listenering to Chester...</h3></html>", ImageManager.getLoading(), SwingConstants.LEFT), "align center, wrap");

			MyJScrollPane pane = new MyJScrollPane(table);
			table.setFillsViewportHeight(true);
			add(pane, "align center, wrap");

			add(ok, "split 2, align center");
			add(cancel);

			pack();
			setSize(getWidth(), getHeight() + 200);
			setLocationRelativeTo(owner);

			thread = new ChesterThread();
			thread.setDaemon(true);
			thread.start();
		}

		private class ChesterThread extends Thread {
			private final long started = System.currentTimeMillis();
			private boolean running = true;

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
				while (running) {
					try {
						if (!dir.exists()) {
							Thread.sleep(100);
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

							ChesterFile chesterFile = ChesterFile.parse(file);
							items.addAll(chesterFile.items);
							file.delete();
						}

						Thread.sleep(100);
					} catch (InterruptedException e) {
						running = false;
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
	}

	private static class ChesterFile {
		private static final ItemIndex index = ItemIndex.instance();

		private final String version;
		private final double playerX, playerY, playerZ;
		private final List<Inventory> items;

		public static ChesterFile parse(File file) throws IOException {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			try {
				String version = reader.readLine();

				String split[] = reader.readLine().split(" ", 3);
				double playerX = Double.parseDouble(split[0]);
				double playerY = Double.parseDouble(split[1]);
				double playerZ = Double.parseDouble(split[2]);

				List<Inventory> items = new ArrayList<Inventory>();
				String line;
				while ((line = reader.readLine()) != null) {
					split = line.split(" ", 2);
					String id = split[0];
					int quantity = Integer.parseInt(split[1]);
					String itemName = index.getDisplayNameFromMinecraftId(id);

					Inventory inv = new Inventory();
					inv.setItem(itemName);
					inv.setQuantity(quantity);
					items.add(inv);
				}

				return new ChesterFile(version, playerX, playerY, playerZ, items);
			} finally {
				IOUtils.closeQuietly(reader);
			}
		}

		public ChesterFile(String version, double playerX, double playerY, double playerZ, List<Inventory> items) {
			this.version = version;
			this.playerX = playerX;
			this.playerY = playerY;
			this.playerZ = playerZ;
			this.items = items;
		}
	}
}
