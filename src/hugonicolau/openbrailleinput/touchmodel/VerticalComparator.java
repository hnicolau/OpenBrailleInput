package hugonicolau.openbrailleinput.touchmodel;

import java.util.Comparator;

/**
 * Helper to compare vertical positions
 */
public class VerticalComparator implements Comparator<Pointer>{
	
	@Override
	public int compare(Pointer arg0, Pointer arg1) 
	{
		return arg0.getLastKnownPosition().y - arg1.getLastKnownPosition().y;
	}
}
