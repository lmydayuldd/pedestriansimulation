package ar.edu.itba.common.rand;

import java.util.List;

public class CollectionsRand {

	public static <T> T randomElement(List<T> list) {
		return list.get((int) (Math.random() * list.size()));
	}

}
