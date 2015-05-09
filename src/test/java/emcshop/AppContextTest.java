package emcshop;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class AppContextTest {
	@Test
	public void test() {
		AppContext context = new AppContext();

		ArrayList<Object> arrayList = new ArrayList<Object>();
		LinkedList<Object> linkedList = new LinkedList<Object>();
		context.add(arrayList);
		context.add(linkedList);

		assertSame(arrayList, context.get(ArrayList.class));
		assertSame(linkedList, context.get(LinkedList.class));
		assertSame(arrayList, context.get(List.class));
		assertNull(context.get(String.class));

		assertSame(arrayList, context.remove(ArrayList.class));
		assertNull(context.get(ArrayList.class));
		assertNull(context.remove(ArrayList.class));
		assertNull(context.remove(String.class));
	}
}
