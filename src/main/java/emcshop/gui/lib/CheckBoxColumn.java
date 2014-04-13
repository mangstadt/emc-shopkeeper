package emcshop.gui.lib;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * Adapted from {@link ButtonColumn}.
 */
@SuppressWarnings("serial")
public class CheckBoxColumn extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {
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
	public CheckBoxColumn(final JTable table, Action action, int column) {
		this.table = table;
		this.action = action;

		originalBorder = new JCheckBox().getBorder();
		setFocusBorder(new LineBorder(Color.BLUE));

		TableColumnModel columnModel = table.getColumnModel();
		columnModel.getColumn(column).setCellRenderer(this);
		columnModel.getColumn(column).setCellEditor(this);
		table.addMouseListener(new MouseAdapter() {
			/*
			 * When the mouse is pressed the editor is invoked. If you then then
			 * drag the mouse to another cell before releasing it, the editor is
			 * still active. Make sure editing is stopped when the mouse is
			 * released.
			 */
			@Override
			public void mousePressed(MouseEvent e) {
				if (table.isEditing() && table.getCellEditor() == CheckBoxColumn.this) {
					isButtonColumnEditor = true;
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (isButtonColumnEditor && table.isEditing()) {
					table.getCellEditor().stopCellEditing();
				}

				isButtonColumnEditor = false;
			}
		});
	}

	/**
	 * Gets the states of all of the checkboxes in this column.
	 * @return the checkbox states
	 */
	public Map<Integer, Boolean> getStates() {
		Map<Integer, Boolean> states = new HashMap<Integer, Boolean>();
		for (Map.Entry<Integer, JCheckBox> entry : editCheckboxes.entrySet()) {
			Integer row = entry.getKey();
			JCheckBox checkbox = entry.getValue();

			states.put(row, checkbox.isSelected());
		}

		return states;
	}

	/**
	 * Get the indexes of the rows whose checkboxes are selected.
	 * @return the selected rows
	 */
	public List<Integer> getSelectedRows() {
		List<Integer> selectedRows = new ArrayList<Integer>();

		for (Map.Entry<Integer, JCheckBox> entry : editCheckboxes.entrySet()) {
			JCheckBox checkbox = entry.getValue();
			if (checkbox.isSelected()) {
				Integer row = entry.getKey();
				selectedRows.add(row);
			}
		}

		return selectedRows;
	}

	/**
	 * Sets the selected state of a checkbox
	 * @param row the row the checkbox is on
	 * @param selected true to selected, false to deselected it
	 */
	public void setCheckboxSelected(int row, boolean selected) {
		JCheckBox edit = getEditCheckbox(row);
		edit.setSelected(selected);

		JCheckBox render = getRenderCheckbox(row);
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
	@Override
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
	 * The button has been pressed. Stop editing and invoke the custom Action
	 */
	@Override
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
}