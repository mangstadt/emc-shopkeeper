package ch.rakudave.suggest.matcher;


public class ContainsMatcher implements SuggestMatcher {
	@Override
	public boolean matches(String dataWord, String searchWord) {
		return dataWord.contains(searchWord);
	}
}