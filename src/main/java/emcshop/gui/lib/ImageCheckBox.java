package emcshop.gui.lib;

import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

public class ImageCheckBox {
	private final JCheckBox checkbox;
	private final JLabel label;

	public ImageCheckBox(String text, Icon image) {
		checkbox = new JCheckBox();
		label = new JLabel(text, image, SwingConstants.LEFT);
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				checkbox.doClick();
			}
		});
	}

	public void setEnabled(boolean enabled) {
		checkbox.setEnabled(enabled);
		label.setEnabled(enabled);
	}

	public JCheckBox getCheckbox() {
		return checkbox;
	}

	public JLabel getLabel() {
		return label;
	}

	public boolean isSelected() {
		return checkbox.isSelected();
	}

	public void addActionListener(ActionListener listener) {
		checkbox.addActionListener(listener);
	}
}
