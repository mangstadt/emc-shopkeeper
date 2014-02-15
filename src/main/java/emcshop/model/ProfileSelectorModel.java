package emcshop.model;

import java.util.List;

public interface ProfileSelectorModel {
	/**
	 * Gets the names of all existing profiles.
	 * @return the profile names
	 */
	List<String> getAvailableProfiles();

	/**
	 * Creates a profile.
	 * @param profile the profile name
	 * @return true if it was created or already exists, false if it could not
	 * be created
	 */
	boolean createProfile(String profile);
}
