package emcshop.gui.lib;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 * Adapted from {@link ButtonColumn}
 */
@SuppressWarnings("serial")
public class CheckBoxColumn extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener, MouseListener {
	private final JTable table;
	private final Action action;
	private final Map<Integer, JCheckBox> editCheckboxes = new HashMap<Integer, JCheckBox>();
	private final Map<Integer, JCheckBox> renderCheckboxes = new HashMap<Integer, JCheckBox>();
	private final Border originalBorder;

	private Border focusBorder;
	private Object editorValue;
	private boolean isButtonColumnEditor;

	/**
	 * Create the ButtonColumn to be used as a renderer and editor. The renderer
	 * and editor will automatically be installed on the TableColumn of the
	 * specified column.
	 * 
	 * @param table the table containing the button renderer/editor
	 * @param action the Action to be invoked when the button is invoked
	 * @param column the column to which the button renderer/editor is added
	 */
	public CheckBoxColumn(JTable table, Action action, int column) {
		this.table = table;
		this.action = action;

		originalBorder = new JCheckBox().getBorder();
		setFocusBorder(new LineBorder(Color.BLUE));

		TableColumnModel columnModel = table.getColumnModel();
		columnModel.getColumn(column).setCellRenderer(this);
		columnModel.getColumn(column).setCellEditor(this);
		table.addMouseListener(this);
	}

	public void setCheckboxSelected(int index, boolean selected) {
		JCheckBox edit = getEditCheckbox(index);
		edit.setSelected(selected);

		JCheckBox render = getRenderCheckbox(index);
		render.setSelected(selected);
	}

	/**
	 * Get foreground color of the button when the cell has focus
	 * 
	 * @return the foreground color
	 */
	public Border getFocusBorder() {
		return focusBorder;
	}

	/**
	 * The foreground color of the button when the cell has focus
	 * 
	 * @param focusBorder the foreground color
	 */
	public void setFocusBorder(Border focusBorder) {
		this.focusBorder = focusBorder;
		for (JCheckBox cb : editCheckboxes.values()) {
			cb.setBorder(focusBorder);
		}
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		JCheckBox editButton = getEditCheckbox(row);
		this.editorValue = value;
		return editButton;
	}

	@Override
	public Object getCellEditorValue() {
		return editorValue;
	}

	//
	//  Implement TableCellRenderer interface
	//
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		JCheckBox renderButton = getRenderCheckbox(row);

		if (isSelected) {
			renderButton.setForeground(table.getSelectionForeground());
			renderButton.setBackground(table.getSelectionBackground());
		} else {
			renderButton.setForeground(table.getForeground());
			renderButton.setBackground(UIManager.getColor("Button.background"));
		}

		if (hasFocus) {
			renderButton.setBorder(focusBorder);
		} else {
			renderButton.setBorder(originalBorder);
		}

		return renderButton;
	}

	private JCheckBox getEditCheckbox(int row) {
		JCheckBox editButton = editCheckboxes.get(row);
		if (editButton == null) {
			editButton = new JCheckBox();
			editButton.setFocusPainted(false);
			editButton.addActionListener(this);
			editCheckboxes.put(row, editButton);
		}
		return editButton;
	}

	private JCheckBox getRenderCheckbox(int row) {
		JCheckBox renderButton = renderCheckboxes.get(row);
		if (renderButton == null) {
			renderButton = new JCheckBox();
			renderCheckboxes.put(row, renderButton);
		}
		return renderButton;
	}

	//
	//  Implement ActionListener interface
	//
	/*
	 *	The button has been pressed. Stop editing and invoke the custom Action
	 */
	public void actionPerformed(ActionEvent e) {
		int row = table.convertRowIndexToModel(table.getEditingRow());
		fireEditingStopped();

		//  Invoke the Action

		JCheckBox editButton = getEditCheckbox(row);
		JCheckBox renderButton = getRenderCheckbox(row);

		renderButton.setSelected(editButton.isSelected());

		//re-aligns the checkboxes in their cells
		AbstractTableModel model = (AbstractTableModel) table.getModel();
		model.fireTableDataChanged();

		ActionEvent event = new ActionEvent(table, ActionEvent.ACTION_PERFORMED, row + " " + editButton.isSelected());
		action.actionPerformed(event);
	}

	//
	//  Implement MouseListener interface
	//
	/*
	 *  When the mouse is pressed the editor is invoked. If you then then drag
	 *  the mouse to another cell before releasing it, the editor is still
	 *  active. Make sure editing is stopped when the mouse is released.
	 */
	public void mousePressed(MouseEvent e) {
		if (table.isEditing() && table.getCellEditor() == this) isButtonColumnEditor = true;
	}

	public void mouseReleased(MouseEvent e) {
		if (isButtonColumnEditor && table.isEditing()) table.getCellEditor().stopCellEditing();

		isButtonColumnEditor = false;
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}
}