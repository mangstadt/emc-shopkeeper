package emcshop.gui.lib;

import java.awt.Component;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

//@formatter:off

/**
 * A combo box which allows items to be disabled.
 *
 * @see "http://raginggoblin.wordpress.com/2010/05/04/jcombobox-with-disabled-items/"
 */
//@formatter:on
@SuppressWarnings("serial")
public class DisabledItemsComboBox extends JComboBox {
    private Set<Integer> disabledItemIndexes = new HashSet<Integer>();

    public DisabledItemsComboBox() {
        setRenderer(new DisabledItemsRenderer());
    }

    public void addItem(Object anObject, boolean disabled) {
        super.addItem(anObject);
        if (disabled) {
            disabledItemIndexes.add(getItemCount() - 1);
        }
    }

    @Override
    public void removeAllItems() {
        super.removeAllItems();
        disabledItemIndexes.clear();
    }

    @Override
    public void removeItemAt(final int anIndex) {
        super.removeItemAt(anIndex);
        disabledItemIndexes.remove(anIndex);
    }

    @Override
    public void removeItem(final Object anObject) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getItemAt(i) == anObject) {
                disabledItemIndexes.remove(i);
            }
        }
        super.removeItem(anObject);
    }

    @Override
    public void setSelectedIndex(int index) {
        if (!disabledItemIndexes.contains(index)) {
            super.setSelectedIndex(index);
        }
    }

    private class DisabledItemsRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            if (disabledItemIndexes.contains(index)) {
                setBackground(list.getBackground());
                setForeground(UIManager.getColor("Label.disabledForeground"));
            }
            setFont(list.getFont());
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }
}
