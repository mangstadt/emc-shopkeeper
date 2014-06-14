package emcshop.gui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import emcshop.ExportType;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class ExportButton extends JButton {
	private final JPopupMenu exportMenu;

	public ExportButton(final Window owner, final ExportListener listener) {
		setText("<html><font size=2>Export \u00bb");

		exportMenu = new JPopupMenu();
		for (final ExportType type : ExportType.values()) {
			AbstractAction action = new AbstractAction() {
				@Override
				public String toString() {
					return type.toString();
				}

				@Override
				public void actionPerformed(ActionEvent e) {
					String text = listener.exportData(type);
					GuiUtils.copyToClipboard(text);
					JOptionPane.showMessageDialog(owner, "Copied to clipboard.");
				}
			};
			action.putValue(Action.NAME, type.toString());
			exportMenu.add(action);
		}

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				exportMenu.show(ExportButton.this, event.getX(), event.getY());
			}
		});
	}

	public interface ExportListener {
		String exportData(ExportType type);
	}
}
