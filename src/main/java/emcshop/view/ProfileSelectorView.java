package emcshop.view;

import java.awt.event.ActionListener;
import java.util.List;

public interface ProfileSelectorView {
	/**
	 * Adds a listener for when the user selects a profile.
	 * @param listener the listener
	 */
	void addProfileSelectedListener(ActionListener listener);

	/**
	 * Adds a listener for when the user cancels the dialog.
	 * @param listener the listener
	 */
	void addCancelListener(ActionListener listener);

	/**
	 * Sets the names of all existing profiles.
	 * @param profiles the existing profile names
	 */
	void setAvailableProfiles(List<String> profiles);

	/**
	 * Gets the profile that was selected by the user.
	 * @return the selected profile
	 */
	String getSelectedProfile();

	/**
	 * Shows a validation error.
	 * @param error the error to show
	 */
	void showValidationError(String error);

	/**
	 * Closes the dialog.
	 */
	void close();

	/**
	 * Displays the dialog.
	 */
	void display();
}
