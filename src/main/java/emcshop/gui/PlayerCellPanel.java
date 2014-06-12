package emcshop.gui;

import java.awt.Color;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;

import net.miginfocom.swing.MigLayout;
import emcshop.AppContext;
import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.gui.images.ImageManager;
import emcshop.scraper.EmcServer;
import emcshop.util.UIDefaultsWrapper;

/**
 * Used for rendering a player name in a JTable.
 */
@SuppressWarnings("serial")
public class PlayerCellPanel extends JPanel {
	private static final AppContext context = AppContext.instance();

	private final ProfileLoader profileLoader = context.get(ProfileLoader.class);
	private final OnlinePlayersMonitor onlinePlayersMonitor = context.get(OnlinePlayersMonitor.class);

	private final JLabel playerLabel = new JLabel();
	private final JLabel serverLabel = new JLabel();

	public PlayerCellPanel() {
		//the model can't be passed into the constructor because it becomes "null" when "ProfileDownloadedListener" is called somehow 

		setLayout(new MigLayout("insets 2"));
		setOpaque(true);

		add(playerLabel);
		add(serverLabel);
	}

	public void setPlayer(String playerName, final int row, final int col, final AbstractTableModel model) {
		playerLabel.setText(playerName);

		ImageIcon portrait = profileLoader.getPortraitFromCache(playerName);
		if (portrait == null) {
			portrait = ImageManager.getUnknown();
		}
		portrait = ImageManager.scale(portrait, 16);
		playerLabel.setIcon(portrait);

		Color rank = profileLoader.getRankColor(playerName);
		if (rank == null) {
			rank = UIDefaultsWrapper.getLabelForeground();
		}
		playerLabel.setForeground(rank);

		EmcServer server = onlinePlayersMonitor.getPlayerServer(playerName);
		ImageIcon icon = (server == null) ? null : ImageManager.getOnline(server, 12);
		serverLabel.setIcon(icon);

		if (!profileLoader.wasDownloaded(playerName)) {
			profileLoader.queueProfileForDownload(playerName, new ProfileDownloadedListener() {
				@Override
				public void onProfileDownloaded(JLabel label) {
					//re-render the cell when the profile is downloaded
					model.fireTableCellUpdated(row, col);
				}
			});
		}
	}

	@Override
	public void setForeground(Color color) {
		if (playerLabel != null) {
			//this method is called before the constructor is called!
			playerLabel.setForeground(color);
		}
		super.setForeground(color);
	}
}
