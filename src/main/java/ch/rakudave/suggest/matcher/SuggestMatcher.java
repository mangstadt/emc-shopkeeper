package ch.rakudave.suggest.matcher;

public interface SuggestMatcher {
	boolean matches(String dataWord, String searchWord);
}