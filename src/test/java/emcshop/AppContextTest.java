package emcshop;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

public class AppContextTest {
	@Test
	public void get_set() {
		AppContext context = new AppContext();

		ArrayList<Object> arrayList = new ArrayList<Object>();
		LinkedList<Object> linkedList = new LinkedList<Object>();
		context.add(arrayList);
		context.add(linkedList);

		assertTrue(context.get(ArrayList.class) == arrayList);
		assertTrue(context.get(LinkedList.class) == linkedList);
		assertTrue(context.get(List.class) == arrayList);
	}
}
