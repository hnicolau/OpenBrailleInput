// LICENSE: GPLv3. http://www.gnu.org/licenses/gpl-3.0.txt
package hugonicolau.openbrailleinput.wordcorrection.mafsa;

import java.io.IOException;

/**
 * Exception thrown when a MAFSA can't be loaded from a data file.
 */
public class DataFormatException extends IOException
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public DataFormatException (String message, Throwable cause)
	{
		super (message, cause);
	}
}
