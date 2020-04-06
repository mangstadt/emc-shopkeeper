package emcshop.gui.lib;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

//@formatter:off
/**
 * A table that contains groupable columns.
 * @author Nobuo Tamemasa
 * @author Michael Angstadt
 * @see <a href="http://www.java2s.com/Code/Java/Swing-Components/GroupableGroupHeaderExample.htm">http://www.java2s.com/Code/Java/Swing-Components/GroupableGroupHeaderExample.htm</a>
 */
//@formatter:on
@SuppressWarnings("serial")
public abstract class GroupableColumnsTable extends JTable {
	/**
	 * Sets the column groups. This must be called by the child class in order
	 * to define the column groupings.
	 * @param columnGroups the column groups
	 */
	protected void setColumnGroups(List<ColumnGroup> columnGroups) {
		GroupableTableHeader header = (GroupableTableHeader) getTableHeader();
		for (ColumnGroup columnGroup : columnGroups) {
			header.addColumnGroup(columnGroup);
		}
	}

	@Override
	protected JTableHeader createDefaultTableHeader() {
		return new GroupableTableHeader(columnModel);
	}

	/**
	 * A column group.
	 * @author Nobuo Tamemasa
	 * @author Michael Angstadt
	 */
	public static class ColumnGroup {
		private TableCellRenderer renderer;
		private List<Object> v = new ArrayList<>();
		private String text;
		private int margin = 0;

		public ColumnGroup(String text) {
			this(null, text);
		}

		public ColumnGroup(TableCellRenderer renderer, String text) {
			if (renderer == null) {
				this.renderer = new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
						JTableHeader header = table.getTableHeader();
						if (header != null) {
							setForeground(header.getForeground());
							setBackground(header.getBackground());
							setFont(header.getFont());
						}
						setHorizontalAlignment(SwingConstants.CENTER);
						setText((value == null) ? "" : value.toString());
						setBorder(UIManager.getBorder("TableHeader.cellBorder"));
						return this;
					}
				};
			} else {
				this.renderer = renderer;
			}
			this.text = text;
		}

		/**
		 * @param obj TableColumn or ColumnGroup
		 */
		public void add(Object obj) {
			if (obj == null) {
				return;
			}
			v.add(obj);
		}

		/**
		 * @param c TableColumn
		 * @param g ColumnGroups
		 * @return the column groups
		 */
		public List<ColumnGroup> getColumnGroups(TableColumn c, List<ColumnGroup> g) {
			g.add(this);
			if (v.contains(c)) {
				return g;
			}

			for (Object obj : v) {
				if (obj instanceof ColumnGroup) {
					List<ColumnGroup> groups = ((ColumnGroup) obj).getColumnGroups(c, new ArrayList<ColumnGroup>(g));
					if (groups != null) {
						return groups;
					}
				}
			}
			return null;
		}

		public TableCellRenderer getHeaderRenderer() {
			return renderer;
		}

		public void setHeaderRenderer(TableCellRenderer renderer) {
			if (renderer != null) {
				this.renderer = renderer;
			}
		}

		public Object getHeaderValue() {
			return text;
		}

		public Dimension getSize(JTable table) {
			Component comp = renderer.getTableCellRendererComponent(table, getHeaderValue(), false, false, -1, -1);
			int height = comp.getPreferredSize().height;
			int width = 0;
			for (Object obj : v) {
				if (obj instanceof TableColumn) {
					TableColumn aColumn = (TableColumn) obj;
					width += aColumn.getWidth();
					width += margin;
				} else {
					width += ((ColumnGroup) obj).getSize(table).width;
				}
			}
			return new Dimension(width, height);
		}

		public void setColumnMargin(int margin) {
			this.margin = margin;
			for (Object obj : v) {
				if (obj instanceof ColumnGroup) {
					((ColumnGroup) obj).setColumnMargin(margin);
				}
			}
		}
	}

	private static class GroupableTableHeader extends JTableHeader {
		protected List<ColumnGroup> columnGroups = null;

		public GroupableTableHeader(TableColumnModel model) {
			super(model);
			setUI(new GroupableTableHeaderUI());
			setReorderingAllowed(false);
		}

		@Override
		public void updateUI() {
			setUI(new GroupableTableHeaderUI());
		}

		@Override
		public void setReorderingAllowed(boolean b) {
			reorderingAllowed = false;
		}

		public void addColumnGroup(ColumnGroup g) {
			if (columnGroups == null) {
				columnGroups = new ArrayList<ColumnGroup>();
			}
			columnGroups.add(g);
		}

		public List<ColumnGroup> getColumnGroups(TableColumn col) {
			if (columnGroups == null) {
				return null;
			}

			for (ColumnGroup cGroup : columnGroups) {
				List<ColumnGroup> v_ret = cGroup.getColumnGroups(col, new ArrayList<ColumnGroup>());
				if (v_ret != null) {
					return v_ret;
				}
			}
			return null;
		}

		public void setColumnMargin() {
			if (columnGroups == null) {
				return;
			}

			int columnMargin = getColumnModel().getColumnMargin();
			for (ColumnGroup cGroup : columnGroups) {
				cGroup.setColumnMargin(columnMargin);
			}
		}

	}

	private static class GroupableTableHeaderUI extends BasicTableHeaderUI {
		@Override
		public void paint(Graphics g, JComponent c) {
			if (header.getColumnModel() == null) {
				return;
			}

			Rectangle clipBounds = g.getClipBounds();
			((GroupableTableHeader) header).setColumnMargin();
			int column = 0;
			Dimension size = header.getSize();
			Rectangle cellRect = new Rectangle(0, 0, size.width, size.height);
			Map<ColumnGroup, Rectangle> h = new HashMap<>();
			int columnMargin = header.getColumnModel().getColumnMargin();

			Enumeration<TableColumn> enumeration = header.getColumnModel().getColumns();
			while (enumeration.hasMoreElements()) {
				cellRect.height = size.height;
				cellRect.y = 0;
				TableColumn aColumn = enumeration.nextElement();
				List<ColumnGroup> cGroups = ((GroupableTableHeader) header).getColumnGroups(aColumn);
				if (cGroups != null) {
					int groupHeight = 0;
					for (ColumnGroup cGroup : cGroups) {
						Rectangle groupRect = h.get(cGroup);
						if (groupRect == null) {
							groupRect = new Rectangle(cellRect);
							Dimension d = cGroup.getSize(header.getTable());
							groupRect.width = d.width;
							groupRect.height = d.height;
							h.put(cGroup, groupRect);
						}
						paintCell(g, groupRect, cGroup);
						groupHeight += groupRect.height;
						cellRect.height = size.height - groupHeight;
						cellRect.y = groupHeight;
					}
				}
				cellRect.width = aColumn.getWidth() + columnMargin;
				if (cellRect.intersects(clipBounds)) {
					paintCell(g, cellRect, column);
				}
				cellRect.x += cellRect.width;
				column++;
			}
		}

		private void paintCell(Graphics g, Rectangle cellRect, int columnIndex) {
			TableColumn aColumn = header.getColumnModel().getColumn(columnIndex);
			TableCellRenderer renderer = aColumn.getHeaderRenderer();
			//revised by Java2s.com
			renderer = new DefaultTableCellRenderer() {
				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
					JLabel header = new JLabel();
					header.setForeground(table.getTableHeader().getForeground());
					header.setBackground(table.getTableHeader().getBackground());
					header.setFont(table.getTableHeader().getFont());

					header.setHorizontalAlignment(SwingConstants.CENTER);
					header.setText(value.toString());
					header.setBorder(UIManager.getBorder("TableHeader.cellBorder"));
					return header;
				}

			};
			Component c = renderer.getTableCellRendererComponent(header.getTable(), aColumn.getHeaderValue(), false, false, -1, columnIndex);

			c.setBackground(UIManager.getColor("control"));

			rendererPane.add(c);
			rendererPane.paintComponent(g, c, header, cellRect.x, cellRect.y, cellRect.width, cellRect.height, true);
		}

		private void paintCell(Graphics g, Rectangle cellRect, ColumnGroup cGroup) {
			TableCellRenderer renderer = cGroup.getHeaderRenderer();
			//revised by Java2s.com
			// if(renderer == null){
			//      return ;
			//    }

			Component component = renderer.getTableCellRendererComponent(header.getTable(), cGroup.getHeaderValue(), false, false, -1, -1);
			rendererPane.add(component);
			rendererPane.paintComponent(g, component, header, cellRect.x, cellRect.y, cellRect.width, cellRect.height, true);
		}

		private int getHeaderHeight() {
			int height = 0;
			TableColumnModel columnModel = header.getColumnModel();
			for (int column = 0; column < columnModel.getColumnCount(); column++) {
				TableColumn aColumn = columnModel.getColumn(column);
				TableCellRenderer renderer = aColumn.getHeaderRenderer();
				//revised by Java2s.com
				if (renderer == null) {
					return 60;
				}

				Component comp = renderer.getTableCellRendererComponent(header.getTable(), aColumn.getHeaderValue(), false, false, -1, column);
				int cHeight = comp.getPreferredSize().height;
				List<ColumnGroup> e = ((GroupableTableHeader) header).getColumnGroups(aColumn);
				if (e != null) {
					for (ColumnGroup cGroup : e) {
						cHeight += cGroup.getSize(header.getTable()).height;
					}
				}
				height = Math.max(height, cHeight);
			}
			return height;
		}

		private Dimension createHeaderSize(long width) {
			TableColumnModel columnModel = header.getColumnModel();
			width += columnModel.getColumnMargin() * columnModel.getColumnCount();
			if (width > Integer.MAX_VALUE) {
				width = Integer.MAX_VALUE;
			}
			return new Dimension((int) width, getHeaderHeight());
		}

		@Override
		public Dimension getPreferredSize(JComponent c) {
			long width = 0;
			Enumeration<TableColumn> enumeration = header.getColumnModel().getColumns();
			while (enumeration.hasMoreElements()) {
				TableColumn aColumn = enumeration.nextElement();
				width = width + aColumn.getPreferredWidth();
			}
			return createHeaderSize(width);
		}
	}
}
