package emcshop.gui;

import static emcshop.util.GuiUtils.toolTipText;
import static emcshop.util.NumberFormatter.formatQuantity;
import static emcshop.util.NumberFormatter.formatStacks;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

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
import emcshop.gui.lib.CheckBoxColumn;
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
					filterPanel.clear();
				} catch (SQLException e) {
					dao.rollback();
					throw new RuntimeException(e);
				}
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

		table = new InventoryTable(Column.REMAINING, true);

		///////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		JPanel leftTop = new JPanel(new MigLayout());

		leftTop.add(new JLabel("Item Name:"));
		leftTop.add(quantityLabel, "wrap");
		leftTop.add(item, "w 300");
		leftTop.add(quantity, "w 150, wrap");
		leftTop.add(addEdit, "span 2, split 2");
		leftTop.add(delete, "wrap");
		//leftTop.add(chester, "span 2, split 2");
		//leftTop.add(chesterUrl);

		add(leftTop, "span 1 2, w 300!, growy");

		add(filterPanel, "wrap");

		tableScrollPane = new MyJScrollPane(table);
		add(tableScrollPane, "grow, w 100%, h 100%, wrap");
		table.setColumns();

		refresh();
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
			add = quantity.isAdd();
			quantityValue = quantity.getQuantity(index.getStackSize(itemStr));
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

	public void setShowQuantitiesInStacks(boolean enable) {
		showQuantitiesInStacks = enable;
		table.redraw();
	}

	public void refresh() {
		Collection<Inventory> inventory;
		try {
			inventory = dao.getInventory();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		table.refresh(inventory);
		filterPanel.clear();
	}

	private class Row {
		private final Inventory inventory;
		private boolean selected = false;
		private boolean idUnknown = false;

		public Row(Inventory inventory) {
			this.inventory = inventory;
		}
	}

	private class InventoryTable extends JTable {
		private final Column columns[] = Column.values();

		private Column prevColumnClicked;
		private boolean ascending;
		private CheckBoxColumn checkboxes;
		private List<Row> rows = new ArrayList<Row>();
		private List<Row> displayedRows = rows;

		public InventoryTable(Column sortBy, boolean ascending) {
			prevColumnClicked = sortBy;
			this.ascending = ascending;

			getTableHeader().setReorderingAllowed(false);
			setColumnSelectionAllowed(false);
			setRowSelectionAllowed(false);
			setCellSelectionEnabled(false);
			setRowHeight(24);

			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					int col = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
					int row = convertRowIndexToModel(rowAtPoint(e.getPoint()));
					if (col < 0 || row < 0) {
						return;
					}

					Column column = columns[col];
					if (column == Column.REMAINING || column == Column.CHECKBOX) {
						return;
					}

					boolean checked = !displayedRows.get(row).selected;
					checkboxes.setCheckboxSelected(row, checked);
					displayedRows.get(row).selected = checked;

					AbstractTableModel model = (AbstractTableModel) getModel();
					model.fireTableRowsUpdated(row, row);

					redraw();
				}
			});

			//allow columns to be sorted by clicking on the headers
			getTableHeader().addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					int index = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
					if (index < 0) {
						return;
					}

					Column column = columns[index];

					if (column == prevColumnClicked) {
						InventoryTable.this.ascending = !InventoryTable.this.ascending;
					} else {
						prevColumnClicked = column;
						InventoryTable.this.ascending = true;
					}

					//select all checkboxes
					if (column == Column.CHECKBOX) {
						AbstractTableModel model = (AbstractTableModel) getModel();
						for (int i = 0; i < displayedRows.size(); i++) {
							checkboxes.setCheckboxSelected(i, InventoryTable.this.ascending);
							displayedRows.get(i).selected = InventoryTable.this.ascending;
							model.fireTableCellUpdated(i, Column.CHECKBOX.ordinal());
						}
						return;
					}

					sortData();
					redraw();
				}
			});

			setDefaultRenderer(Row.class, new TableCellRenderer() {
				private final Color evenRowColor = new Color(255, 255, 255);
				private final Color oddRowColor = new Color(240, 240, 240);
				private final JLabel label = new JLabel();
				{
					label.setOpaque(true);
					label.setBorder(new EmptyBorder(4, 4, 4, 4));
				}

				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
					if (value == null) {
						return null;
					}

					Row rowObj = (Row) value;
					Inventory inv = rowObj.inventory;
					Column column = columns[col];
					resetComponent();

					switch (column) {
					case CHECKBOX:
						//shouldn't be called
						return null;

					case ITEM_NAME:
						if (rowObj.idUnknown) {
							label.setText("<html><font color=red>unknown ID: " + inv.getItem() + "</font></html>");
						} else {
							ImageIcon img = ImageManager.getItemImage(inv.getItem());
							label.setText(inv.getItem());
							label.setIcon(img);
						}
						break;

					case REMAINING:
						String text = showQuantitiesInStacks ? formatStacks(inv.getQuantity(), index.getStackSize(inv.getItem()), false) : formatQuantity(inv.getQuantity(), false);
						label.setText(text);
						break;
					}

					//set the background color of the row
					if (rowObj.selected) {
						UIDefaultsWrapper.assignListFormats(label, rowObj.selected);
					} else {
						Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
						label.setForeground(UIDefaultsWrapper.getLabelForeground());
						label.setBackground(color);
					}

					return label;
				}

				private void resetComponent() {
					label.setIcon(null);
				}
			});

			setModel();
			setColumns();
		}

		public void refresh(Collection<Inventory> inventory) {
			setData(inventory);
			redraw();
		}

		public void add(Inventory inventory, boolean idUnknown) {
			Row row = new Row(inventory);
			row.idUnknown = idUnknown;
			rows.add(row);
		}

		public void setData(Collection<Inventory> inventory) {
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
		}

		public void filter(FilterList filterList) {
			if (filterList.isEmpty()) {
				displayedRows = rows;
				sortData();
			} else {
				displayedRows = new ArrayList<Row>();
				for (Row row : rows) {
					String itemName = row.inventory.getItem();
					if (filterList.contains(itemName)) {
						displayedRows.add(row);
					}
				}
			}
			redraw();
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
		}

		private void redraw() {
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

			TableColumn checkboxColumn = columnModel.getColumn(Column.CHECKBOX.ordinal());
			checkboxColumn.setMinWidth(30);
			checkboxColumn.setMaxWidth(30);
			checkboxColumn.setResizable(false);

			TableColumn itemNameColumn = columnModel.getColumn(Column.ITEM_NAME.ordinal());
			itemNameColumn.setPreferredWidth(200);

			TableColumn remainingColumn = columnModel.getColumn(Column.REMAINING.ordinal());
			remainingColumn.setPreferredWidth(50);
		}

		private void setModel() {
			setModel(new AbstractTableModel() {
				@Override
				public int getColumnCount() {
					return columns.length;
				}

				@Override
				public String getColumnName(int index) {
					Column column = columns[index];

					String text = column.getName();
					if (prevColumnClicked != null && prevColumnClicked == column && prevColumnClicked != Column.CHECKBOX) {
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
					Column column = columns[col];
					switch (column) {
					case CHECKBOX:
						return "";
					default:
						return displayedRows.get(row);
					}
				}

				@Override
				public Class<?> getColumnClass(int col) {
					Column column = columns[col];
					switch (column) {
					case CHECKBOX:
						return String.class;
					default:
						return Row.class;
					}
				}

				@Override
				public boolean isCellEditable(int row, int col) {
					Column column = columns[col];
					switch (column) {
					case CHECKBOX:
						return true;
					default:
						return false;
					}
				}

				//				@Override
				//				public void setValueAt(Object value, int row, int col) {
				//					Column column = columns[col];
				//					if (column == Column.REMAINING) {
				//						int quantity = Integer.parseInt(value.toString());
				//						displayedRows.get(row).inventory.setQuantity(quantity);
				//						fireTableCellUpdated(row, col);
				//					}
				//				}
			});

			//			getModel().addTableModelListener(new TableModelListener() {
			//				@Override
			//				public void tableChanged(TableModelEvent event) {
			//					int col = event.getColumn();
			//					if (col < 0) {
			//						return;
			//					}
			//
			//					Column column = columns[event.getColumn()];
			//					if (column == Column.REMAINING) {
			//						int row = event.getFirstRow();
			//						AbstractTableModel model = (AbstractTableModel) getModel();
			//						Row rowObj = (Row) model.getValueAt(row, col);
			//						//TODO update database because user changed value
			//					}
			//				}
			//			});
		}
	}

	public String export(ExportType type) {
		List<Inventory> inventory = new ArrayList<Inventory>();
		for (Row row : table.displayedRows) {
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
					for (int i = 0; i < table.displayedRows.size(); i++) {
						Row row = table.displayedRows.get(i);
						if (row.selected) {
							table.displayedRows.remove(i);
							i--;
						}
					}

					table.redraw();
				}
			});

			table = new InventoryTable(null, true);

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

			for (Row row : table.displayedRows) {
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
								table.redraw();
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
