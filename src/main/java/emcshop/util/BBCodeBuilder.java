/*
 * Copyright(c) 2012 Siemens Medical Solutions Health
 * Services Corporation.  All rights reserved.  This software is
 * confidential, proprietary to Siemens, is protected by
 * copyright laws in the U.S. and abroad, and is licensed for use
 * by customers only in strict accordance with the license
 * agreement governing its use.
 */

package emcshop.util;

import java.util.Stack;

/**
 * Builds BB Code strings.
 * @author Michael Angstadt
 */
public class BBCodeBuilder {
	private final StringBuilder bbCode = new StringBuilder();
	private final Stack<String> openTags = new Stack<String>();

	public BBCodeBuilder u() {
		return open("u");
	}

	public BBCodeBuilder u(String text) {
		return u().text(text).close();
	}

	public BBCodeBuilder s() {
		return open("s");
	}

	public BBCodeBuilder s(String text) {
		return s().text(text).close();
	}

	public BBCodeBuilder b() {
		return open("b");
	}

	public BBCodeBuilder b(String text) {
		return b().text(text).close();
	}

	public BBCodeBuilder font(String font) {
		return open("font", font);
	}

	public BBCodeBuilder font(String font, String text) {
		return font(font).text(text).close();
	}

	public BBCodeBuilder color(String color) {
		return open("color", color);
	}

	public BBCodeBuilder color(String color, String text) {
		return color(color).text(text).close();
	}

	public BBCodeBuilder url(String url) {
		return open("url").text(url).close();
	}

	/**
	 * @param url the URL
	 * @param text the display text
	 * @return
	 */
	public BBCodeBuilder url(String url, String text) {
		return open("url", url).text(text).close();
	}

	/**
	 * Appends a newline.
	 * @return this
	 */
	public BBCodeBuilder nl() {
		return text(System.getProperty("line.separator"));
	}

	public BBCodeBuilder open(String tagName) {
		return open(tagName, null);
	}

	public BBCodeBuilder open(String tagName, String tagValue) {
		bbCode.append('[').append(tagName);
		if (tagValue != null) {
			bbCode.append('=').append(tagValue);
		}
		bbCode.append(']');
		openTags.push(tagName);
		return this;
	}

	public BBCodeBuilder text(CharSequence text) {
		bbCode.append(text.toString());
		return this;
	}

	public BBCodeBuilder text(char ch) {
		bbCode.append(ch);
		return this;
	}

	/**
	 * Closes the last tag that was opened. This has no effect if there are no
	 * open tags.
	 * @return this
	 */
	public BBCodeBuilder close() {
		if (!openTags.isEmpty()) {
			String tag = openTags.pop();
			bbCode.append("[/").append(tag).append(']');
		}
		return this;
	}

	/**
	 * Closes the last X number of tags that were opened. This has no effect if
	 * there are no open tags.
	 * @param numberOfTagsToClose the number of tags to close
	 * @return this
	 */
	public BBCodeBuilder close(int numberOfTagsToClose) {
		for (int i = 0; i < numberOfTagsToClose; i++) {
			close();
		}
		return this;
	}

	@Override
	public String toString() {
		//close all open tags without modifying the buffer
		StringBuilder sb = new StringBuilder(bbCode);
		for (int i = openTags.size() - 1; i >= 0; i--) {
			String tag = openTags.get(i);
			sb.append("[/").append(tag).append(']');
		}

		return sb.toString();
	}
}
