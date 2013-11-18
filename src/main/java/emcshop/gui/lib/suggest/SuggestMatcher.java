package emcshop.gui.lib.suggest;

public interface SuggestMatcher {
	boolean matches(String dataWord, String searchWord);
}