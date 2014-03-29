package emcshop.view;

import java.awt.event.ActionListener;
import java.util.Date;
import java.util.List;

public interface IBackupView {
	void addStartBackupListener(ActionListener listener);

	void addStartRestoreListener(ActionListener listener);

	void addDeleteBackupListener(ActionListener listener);

	void addSaveSettingsListener(ActionListener listener);

	void addCancelListener(ActionListener listener);

	void addExitListener(ActionListener listener);

	boolean getAutoBackupEnabled();

	Integer getBackupFrequency();

	Integer getMaxBackups();

	Date getSelectedBackup();

	void setAutoBackupEnabled(boolean enabled);

	void setBackupFrequency(Integer days);

	void setMaxBackups(Integer days);

	void setBackups(List<Date> backups);

	void setBackupPercentComplete(double percent);

	void backupComplete();

	void restoreComplete();

	void invalidFrequency();

	void invalidMax();

	void display();

	void close();
}
