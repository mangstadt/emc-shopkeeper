package emcshop.util;

import java.util.Stack;

/**
 * Builds BB Code strings.
 *
 * @author Michael Angstadt
 */
public class BBCodeBuilder implements CharSequence {
    private static final String NEWLINE = System.getProperty("line.separator");
    private final StringBuilder bbCode = new StringBuilder();
    private final Stack<String> openTags = new Stack<String>();

    /**
     * Opens an underline tag.
     *
     * @return this
     */
    public BBCodeBuilder u() {
        return open("u");
    }

    /**
     * Appends underlined text.
     *
     * @param text the text to append
     * @return this
     */
    public BBCodeBuilder u(String text) {
        return u().text(text).close();
    }

    /**
     * Opens a strike-through tag.
     *
     * @return this
     */
    public BBCodeBuilder s() {
        return open("s");
    }

    /**
     * Appends strike-through text.
     *
     * @param text the text to append
     * @return this
     */
    public BBCodeBuilder s(String text) {
        return s().text(text).close();
    }

    /**
     * Opens a bold tag.
     *
     * @return this
     */
    public BBCodeBuilder b() {
        return open("b");
    }

    /**
     * Appends bolded text.
     *
     * @param text the text to append
     * @return this
     */
    public BBCodeBuilder b(String text) {
        return b().text(text).close();
    }

    /**
     * Opens a font tag.
     *
     * @param font the font (e.g. "courier new")
     * @return this
     */
    public BBCodeBuilder font(String font) {
        return open("font", font);
    }

    /**
     * Appends text with a certain font.
     *
     * @param font the font
     * @param text the text to append
     * @return this
     */
    public BBCodeBuilder font(String font, String text) {
        return font(font).text(text).close();
    }

    /**
     * Opens a color tag.
     *
     * @param color the color (e.g. "green")
     * @return this
     */
    public BBCodeBuilder color(String color) {
        return open("color", color);
    }

    /**
     * Appends colored text.
     *
     * @param color the color (e.g. "green")
     * @param text  the text to append
     * @return this
     */
    public BBCodeBuilder color(String color, String text) {
        return color(color).text(text).close();
    }

    /**
     * Appends a URL.
     *
     * @param url the URL (e.g. "http://example.com")
     * @return this
     */
    public BBCodeBuilder url(String url) {
        return open("url").text(url).close();
    }

    /**
     * Appends a URL.
     *
     * @param url  the URL (e.g. "http://example.com")
     * @param text the display text
     * @return this
     */
    public BBCodeBuilder url(String url, String text) {
        return open("url", url).text(text).close();
    }

    /**
     * Appends a newline.
     *
     * @return this
     */
    public BBCodeBuilder nl() {
        return text(NEWLINE);
    }

    /**
     * Opens a tag.
     *
     * @param tagName the tag name (e.g. "b")
     * @return this
     */
    public BBCodeBuilder open(String tagName) {
        return open(tagName, null);
    }

    /**
     * Opens a tag.
     *
     * @param tagName  the tag name (e.g. "color")
     * @param tagValue the tag value (e.g. "green")
     * @return this
     */
    public BBCodeBuilder open(String tagName, String tagValue) {
        bbCode.append('[').append(tagName);
        if (tagValue != null) {
            bbCode.append('=').append(tagValue);
        }
        bbCode.append(']');
        openTags.push(tagName);
        return this;
    }

    /**
     * Appends text.
     *
     * @param text the text to append
     * @return this
     */
    public BBCodeBuilder text(CharSequence text) {
        bbCode.append(text);
        return this;
    }

    /**
     * Appends a character.
     *
     * @param ch the character to append
     * @return this
     */
    public BBCodeBuilder text(char ch) {
        bbCode.append(ch);
        return this;
    }

    /**
     * Closes the last tag that was opened. This has no effect if there are no
     * open tags.
     *
     * @return this
     */
    public BBCodeBuilder close() {
        if (!openTags.isEmpty()) {
            String tag = openTags.pop();
            closeTag(tag, bbCode);
        }
        return this;
    }

    /**
     * Closes the last X number of tags that were opened. This has no effect if
     * there are no open tags.
     *
     * @param tagsToClose the number of tags to close
     * @return this
     */
    public BBCodeBuilder close(int tagsToClose) {
        for (int i = 0; i < tagsToClose; i++) {
            close();
        }
        return this;
    }

    /**
     * Generates the BBCode string. All open tags are automatically closed.
     *
     * @return the BBCode string
     */
    @Override
    public String toString() {
        //close all open tags without modifying the buffer
        StringBuilder sb = new StringBuilder(bbCode);
        for (int i = openTags.size() - 1; i >= 0; i--) {
            String tag = openTags.get(i);
            closeTag(tag, sb);
        }

        return sb.toString();
    }

    private static void closeTag(String tag, StringBuilder sb) {
        sb.append("[/").append(tag).append(']');
    }

    @Override
    public int length() {
        return bbCode.length();
    }

    @Override
    public char charAt(int index) {
        return bbCode.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return bbCode.subSequence(start, end);
    }
}
