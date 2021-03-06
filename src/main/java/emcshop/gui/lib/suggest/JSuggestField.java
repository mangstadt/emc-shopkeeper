package emcshop.gui.lib.suggest;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * Provides a text-field that makes suggestions using a provided data-vector.
 * You might have seen this on Google (tm), this is the Java implementation.
 * 
 * @author David von Ah
 * @version 0.5
 * @see "http://www.rakudave.ch/jsuggestfield"
 */
public class JSuggestField extends JTextField {

	/** unique ID for serialization */
	private static final long serialVersionUID = 1756202080423312153L;

	/** Dialog used as the drop-down list. */
	private JDialog dropDownList;

	/** Location of said drop-down list. */
	private Point location;

	/** List contained in the drop-down dialog. */
	private JList<String> list;

	/**
	 * Vectors containing the original data and the filtered data for the
	 * suggestions.
	 */
	private Vector<String> data, suggestions;

	/**
	 * Separate matcher-thread, prevents the text-field from hanging while the
	 * suggestions are beeing prepared.
	 */
	private transient InterruptableMatcher matcher;

	/**
	 * Fonts used to indicate that the text-field is processing the request,
	 * i.e. looking for matches
	 */
	private Font busy, regular;

	/**
	 * Needed for the new narrowing search, so we know when to reset the list
	 */
	private String lastWord = "";

	/**
	 * The last chosen variable which exists. Needed if user continued to type
	 * but didn't press the enter key
	 */
	private String lastChosenExistingVariable;

	/**
	 * Hint that will be displayed if the field is empty
	 */
	private String hint;

	/** Listeners, fire event when a selection as occured */
	private LinkedList<ActionListener> listeners;

	private transient SuggestMatcher suggestMatcher = new ContainsMatcher();

	private boolean caseSensitive = false;

	/**
	 * Create a new JSuggestField.
	 * 
	 * @param owner Frame containing this JSuggestField
	 */
	public JSuggestField(Window owner) {
		super();
		data = new Vector<>();
		suggestions = new Vector<>();
		listeners = new LinkedList<>();
		owner.addComponentListener(new ComponentListener() {
			@Override
			public void componentShown(ComponentEvent e) {
				relocate();
			}

			@Override
			public void componentResized(ComponentEvent e) {
				relocate();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				relocate();
			}

			@Override
			public void componentHidden(ComponentEvent e) {
				relocate();
			}
		});
		owner.addWindowListener(new WindowAdapter() {
			@Override
			public void windowIconified(WindowEvent e) {
				dropDownList.setVisible(false);
			}

			@Override
			public void windowClosing(WindowEvent e) {
				dropDownList.dispose();
			}

			@Override
			public void windowClosed(WindowEvent e) {
				dropDownList.dispose();
			}
		});
		addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				dropDownList.setVisible(false);

				if (getText().equals("") && e.getOppositeComponent() != null && e.getOppositeComponent().getName() != null) {
					if (!e.getOppositeComponent().getName().equals("suggestFieldDropdownButton")) {
						setText(hint);
					}
				} else if (getText().equals("")) {
					setText(hint);
				}
			}

			@Override
			public void focusGained(FocusEvent e) {
				if (getText().equals(hint)) {
					setText("");
				}

				//EDITED
				if (!getText().isEmpty()) {
					showSuggest();
				}
			}
		});
		dropDownList = new JDialog(owner);
		dropDownList.setUndecorated(true);
		dropDownList.setFocusableWindowState(false);
		dropDownList.setFocusable(false);
		list = new JList<>();
		list.addMouseListener(new MouseAdapter() {
			private int selected;

			@Override
			public void mouseReleased(MouseEvent e) {
				if (selected == list.getSelectedIndex()) {
					// provide double-click for selecting a suggestion
					setText(list.getSelectedValue());
					lastChosenExistingVariable = list.getSelectedValue();
					fireActionEvent();
					dropDownList.setVisible(false);
				}
				selected = list.getSelectedIndex();
			}
		});
		dropDownList.add(new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
		dropDownList.pack();
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				relocate();
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					dropDownList.setVisible(false);
					e.consume();
					return;
				}

				if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					if (dropDownList.isVisible()) {
						list.setSelectedIndex(list.getSelectedIndex() + 1);
						list.ensureIndexIsVisible(list.getSelectedIndex() + 1);
						return;
					}
					showSuggest();
				}

				if (e.getKeyCode() == KeyEvent.VK_UP) {
					list.setSelectedIndex(list.getSelectedIndex() - 1);
					list.ensureIndexIsVisible(list.getSelectedIndex() - 1);
					return;
				}

				if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedIndex() != -1 && suggestions.size() > 0) {
					setText(list.getSelectedValue());
					lastChosenExistingVariable = list.getSelectedValue();
					fireActionEvent();
					dropDownList.setVisible(false);
					return;
				}

				if (getText().isEmpty()) {
					hideSuggest();
				} else {
					showSuggest();
				}
			}
		});
		regular = getFont();
		busy = new Font(getFont().getName(), Font.ITALIC, getFont().getSize());
	}

	/**
	 * Create a new JSuggestField.
	 * 
	 * @param owner Frame containing this JSuggestField
	 * @param data Available suggestions
	 */
	public JSuggestField(Window owner, Vector<String> data) {
		this(owner);
		setSuggestData(data);
	}

	/**
	 * Sets new data used to suggest similar words.
	 * 
	 * @param data Vector containing available words
	 * @return success, true unless the data-vector was null
	 */
	public boolean setSuggestData(Vector<String> data) {
		if (data == null) {
			return false;
		}
		Collections.sort(data);
		this.data = data;
		list.setListData(data);
		return true;
	}

	public void setListCellRenderer(ListCellRenderer<String> renderer) {
		list.setCellRenderer(renderer);
	}

	/**
	 * Get all words that are available for suggestion.
	 * 
	 * @return Vector containing Strings
	 */
	@SuppressWarnings("unchecked")
	public Vector<String> getSuggestData() {
		return (Vector<String>) data.clone();
	}

	/**
	 * Set preferred size for the drop-down that will appear.
	 * 
	 * @param size Preferred size of the drop-down list
	 */
	public void setPreferredSuggestSize(Dimension size) {
		dropDownList.setPreferredSize(size);
	}

	/**
	 * Set minimum size for the drop-down that will appear.
	 * 
	 * @param size Minimum size of the drop-down list
	 */
	public void setMinimumSuggestSize(Dimension size) {
		dropDownList.setMinimumSize(size);
	}

	/**
	 * Set maximum size for the drop-down that will appear.
	 * 
	 * @param size Maximum size of the drop-down list
	 */
	public void setMaximumSuggestSize(Dimension size) {
		dropDownList.setMaximumSize(size);
	}

	/**
	 * Force the suggestions to be displayed (Useful for buttons e.g. for using
	 * JSuggestionField like a ComboBox)
	 */
	public void showSuggest() {
		if (!getText().toLowerCase().contains(lastWord.toLowerCase())) {
			suggestions.clear();
		}
		if (suggestions.isEmpty()) {
			suggestions.addAll(data);
		}
		if (matcher != null) {
			matcher.stop = true;
		}
		matcher = new InterruptableMatcher();
		//matcher.start();
		SwingUtilities.invokeLater(matcher);
		lastWord = getText();
		relocate();
	}

	/**
	 * Force the suggestions to be hidden (Useful for buttons, e.g. to use
	 * JSuggestionField like a ComboBox)
	 */
	public void hideSuggest() {
		dropDownList.setVisible(false);
	}

	/**
	 * @return boolean Visibility of the suggestion window
	 */
	public boolean isSuggestVisible() {
		return dropDownList.isVisible();
	}

	/**
	 * Place the suggestion window under the JTextField.
	 */
	private void relocate() {
		try {
			location = getLocationOnScreen();
			location.y += getHeight();
			dropDownList.setLocation(location);
		} catch (IllegalComponentStateException e) {
			// might happen on window creation
		}
	}

	/**
	 * Inner class providing the independent matcher-thread. This thread can be
	 * interrupted, so it won't process older requests while there's already a
	 * new one.
	 */
	private class InterruptableMatcher extends Thread {
		/** flag used to stop the thread */
		private volatile boolean stop;

		/**
		 * Standard run method used in threads responsible for the actual search
		 */
		@Override
		public void run() {
			try {
				setFont(busy);
				Iterator<String> it = suggestions.iterator();
				String word = getText();
				while (it.hasNext()) {
					if (stop) {
						return;
					}
					// rather than using the entire list, let's rather remove
					// the words that don't match, thus narrowing
					// the search and making it faster
					if (caseSensitive) {
						if (!suggestMatcher.matches(it.next(), word)) {
							it.remove();
						}
					} else {
						if (!suggestMatcher.matches(it.next().toLowerCase(), word.toLowerCase())) {
							it.remove();
						}
					}
				}
				setFont(regular);
				if (suggestions.size() > 0) {
					list.setListData(suggestions);
					list.setSelectedIndex(0);
					list.ensureIndexIsVisible(0);
					dropDownList.setVisible(true);
				} else {
					dropDownList.setVisible(false);
				}
			} catch (Exception e) {
				// Despite all precautions, external changes have occurred.
				// Let the new thread handle it...
				return;
			}
		}
	}

	/**
	 * Adds a listener that notifies when a selection has occured
	 * @param listener ActionListener to use
	 */
	public void addSelectionListener(ActionListener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	/**
	 * Removes the Listener
	 * @param listener ActionListener to remove
	 */
	public void removeSelectionListener(ActionListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Use ActionListener to notify on changes so we don't have to create an
	 * extra event
	 */
	private void fireActionEvent() {
		ActionEvent event = new ActionEvent(this, 0, getText());
		for (ActionListener listener : listeners) {
			listener.actionPerformed(event);
		}
	}

	/**
	 * Returns the selected value in the drop down list
	 * 
	 * @return selected value from the user or null if the entered value does
	 * not exist
	 */
	public String getLastChosenExistingVariable() {
		return lastChosenExistingVariable;
	}

	/**
	 * Get the hint that will be displayed when the field is empty
	 * @return The hint of null if none was defined
	 */
	public String getHint() {
		return hint;
	}

	/**
	 * Set a text that will be displayed when the field is empty
	 * @param hint Hint such as "Search..."
	 */
	public void setHint(String hint) {
		this.hint = hint;
	}

	/**
	 * Determine how the suggestions are generated. Default is the simple
	 * {@link ContainsMatcher}
	 * @param suggestMatcher matcher that determines if a data word may be
	 * suggested for the current search word.
	 */
	public void setSuggestMatcher(SuggestMatcher suggestMatcher) {
		this.suggestMatcher = suggestMatcher;
	}

	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}
}
