package emcshop.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a list of filtered keywords.
 */
public class FilterList {
	private final List<String> keywords = new ArrayList<>();
	private final List<Boolean> wholeMatches = new ArrayList<>();

	/**
	 * Adds a keyword to the list
	 * @param keyword the keyword
	 * @param wholeMatch true for a whole match, false for a partial match
	 */
	public void add(String keyword, boolean wholeMatch) {
		keywords.add(keyword.toLowerCase());
		wholeMatches.add(wholeMatch);
	}

	/**
	 * Determines if the list is empty.
	 * @return true if it's empty, false if not
	 */
	public boolean isEmpty() {
		return keywords.isEmpty();
	}

	/**
	 * Determines if some text is matched by this filter list.
	 * @param text the text
	 * @return true if it matches or if the filter list is empty, false if not
	 */
	public boolean isFiltered(String text) {
		if (isEmpty()) {
			return true;
		}

		text = text.toLowerCase();
		for (int i = 0; i < keywords.size(); i++) {
			String keyword = keywords.get(i);
			Boolean wholeMatch = wholeMatches.get(i);

			if (wholeMatch) {
				if (text.equals(keyword)) {
					return true;
				}
			} else {
				if (text.contains(keyword)) {
					return true;
				}
			}
		}

		return false;
	}
}
