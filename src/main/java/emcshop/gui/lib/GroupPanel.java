package emcshop.gui.lib;

import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import net.miginfocom.swing.MigLayout;

/**
 * Creates a panel with a border around it and a title.
 */
@SuppressWarnings("serial")
public class GroupPanel extends JPanel {
    public GroupPanel(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        Font font = border.getTitleFont();
        if (font != null) {
            border.setTitleFont(new Font(font.getName(), Font.BOLD, font.getSize()));
        }

        setBorder(border);
        setLayout(new MigLayout("insets 5"));
    }
}
