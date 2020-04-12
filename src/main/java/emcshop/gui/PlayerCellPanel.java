package emcshop.gui;

import java.awt.Color;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.github.mangstadt.emc.net.EmcServer;

import emcshop.AppContext;
import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.gui.images.Images;
import emcshop.scraper.PlayerProfile;
import emcshop.util.UIDefaultsWrapper;
import net.miginfocom.swing.MigLayout;

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

	public void setPlayer(String playerName) {
		setPlayer(playerName, downloadedProfile -> profileLoader.getPortrait(playerName, playerLabel, 16));
	}

	public void setPlayer(String playerName, ProfileDownloadedListener listener) {
		playerLabel.setText(playerName);

		profileLoader.getPortrait(playerName, playerLabel, 16, listener);

		PlayerProfile profile = profileLoader.getProfile(playerName, listener);
		if (profile != null) {
			Color color = UIDefaultsWrapper.getLabelForeground();
			String rankColorStr = profile.getRankColor();
			if (rankColorStr != null) {
				try {
					color = Color.decode(rankColorStr);
				} catch (NumberFormatException e) {
					/*
					 * If the color string is not in the correct format, ignore
					 * it.
					 */
				}
			}
			playerLabel.setForeground(color);
		}

		EmcServer server = onlinePlayersMonitor.getPlayerServer(playerName);
		ImageIcon icon = (server == null) ? null : Images.getOnline(server, 12);
		serverLabel.setIcon(icon);
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
