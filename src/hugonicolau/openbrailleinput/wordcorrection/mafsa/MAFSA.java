// LICENSE: GPLv3. http://www.gnu.org/licenses/gpl-3.0.txt
package hugonicolau.openbrailleinput.wordcorrection.mafsa;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.io.output.WriterOutputStream;

import android.annotation.SuppressLint;
import hugonicolau.openbrailleinput.wordcorrection.ChordDistances;
import hugonicolau.openbrailleinput.wordcorrection.Distance;

import java.io.*;
import java.util.*;

/**
 * An implementation of a Directed Acycilic Word Graph (DAWG), also known as 
 * Minimal Acyclic Finite State Automaton (MA-FSA). 
 * Implementation inspired by icantrap (https://github.com/icantrap/android-dawg) 
 */

@SuppressLint("DefaultLocale")
public class MAFSA
{
	protected long[] nodes;

	protected MAFSA () {}

	/**
	 * Used by DawgBuilder to create a new Dawg instance from the backing int array.  Not for general use.  Use one of the
	 * factory methods of Dawg to created your Dawg.
	 *
	 * @param ints the integer array that this instance will use.
	 */
	protected MAFSA (long[] longs)
	{
		nodes = longs.clone ();
	}

	/**
	 * Writes an instance of a dawg to a Writer.  Once the data is written to the Writer, it is flushed, but the writer is
	 * not closed.
	 *
	 * @param writer the Writer to write the dawg to
	 * @throws IOException if writing the dawg to the writer causes an IOException
	 */
	public void store (Writer writer) throws IOException
	{
		store (new WriterOutputStream (writer));
	}

	/**
	 * Writes an instance of a dawg to an OutputStream.  Once the data is written to the OutputStream, it is flushed, but
	 * the stream is not closed.
	 *
	 * @param os the OutputStream to write the dawg to
	 * @throws IOException if writing the dawg to the stream causes an IOException
	 */
	public void store (OutputStream os) throws IOException
	{
		BufferedOutputStream bos = new BufferedOutputStream (os, 8 * 1024);
		ObjectOutputStream oos = new ObjectOutputStream (bos);
    
		oos.writeObject (nodes);
		oos.flush ();
	}

	/**
	 * Factory method.  Creates a new Dawg entry by reading in data from the given Reader.  Once the data is read, the
	 * reader remains open.
	 *
	 * @param reader the reader with the data to create the Dawg instance
	 * @return a new Dawg instance with the data loaded
	 * @throws DataFormatException if the Reader doesn't contain the proper data format for loading a Dawg instance
	 * @throws IOException if reading from the Reader causes an IOException
	 */
	public static MAFSA load (Reader reader) throws IOException
	{
		return load (new ReaderInputStream (reader));
	}

 	/**
 	 * Factory method.  Creates a new Dawg entry by reading in data from the given InputStream.  Once the data is read,
 	 * the stream remains open.
 	 *
 	 * @param is the stream with the data to create the Dawg instance.
 	 * @return a new Dawg instance with the data loaded
 	 * @throws DataFormatException if the InputStream doesn't contain the proper data format for loading a Dawg instance
 	 * @throws IOException if reading from the stream casues an IOException.
 	 */
	public static MAFSA load (InputStream is) throws IOException
	{
	    BufferedInputStream bis = new BufferedInputStream (is, 8 * 1024);
	    ObjectInputStream ois = new ObjectInputStream (bis);

	    long[] longs;
	
	    try
	    {
	      longs = (long[]) ois.readObject ();
	    }
	    catch (ClassNotFoundException cnfe)
	    {
	      throw new DataFormatException ("Bad file.  Not valid for loading com.icantrap.collections.dawg.Dawg", cnfe);
	    }
	
	    return new MAFSA (longs);
	}

	/**
	 * Returns the number of nodes in this dawg.
	 *
	 * @return the number of nodes in this dawg
	 */
	public int nodeCount ()
	{
		return nodes.length;
	}
	
	public int wordCount()
	{
		return getMPH(nodes[0]);
	}

	/**
	 * Is the given word in the dawg?
	 *
	 * @param word the word to check
	 * @return true, if it's in the dawg.  false, otherwise.
	 */
	public boolean contains (String word)
	{
		if ((null == word))// || (word.length () < 2))
			return false;

		char[] letters = word.toUpperCase ().toCharArray ();

		long ptr = nodes[0];

		for (char c: letters)
		{
			ptr = findChild (ptr, c);
			if (-1 == ptr)
				return false;
		}

		return canTerminate (ptr);
	}
	
	public int wordToHash(String word)
	{
		int mph = 0;
		
		char[] letters = word.toUpperCase ().toCharArray ();
		
		long current = nodes[0];
		long next = -1;
		
		for (char c: letters)
		{
			// get next child
			next = findChild (current, c);
			
			if (-1 == next)
			{
				// word does not exist
				return -1;
			}
			else
			{
				// go through all previous siblings to calculate mph
				for (Iterator<Long> iter = childIterator (current); iter.hasNext ();)
				{
					long child = iter.next();
					if(getChar(child) < c) mph += getMPH(child);
					else break;
					
				}
				
				current = next;
				if(canTerminate(current))
				{
					mph += 1;
				}
			}
		}
		
		if(canTerminate(current))
			return mph;
		else 
			return -1;
	}
  
  public String hashToWord(int number)
  {
	  
	  long current = nodes[0];
	  
	  // word does not exist
	  if(number < 0 || number > getMPH(current)) return null;
	  
	  String word = "";
	  int count = number;
	  
	  do
	  {
		  // go through all children
		  for (Iterator<Long> iter = childIterator (current); iter.hasNext ();)
		  {
			  long child = iter.next();
			  int childMPH = getMPH(child);
			  
			  if( childMPH < count)
			  {
				  count -= childMPH; 
			  }
			  else
			  {
				  // this is the correct child
				  word += getChar(child);
				  
				  current = child;
				  if(canTerminate(current)) count -= 1;
				  
				  break;
			  }
		  }
	  }
	  while(count > 0);
	  
	  return word;
  }
	
	/* ************* */
	/* SPELLING DAWG */
	/* ************* */
	
	public String[] searchPrefix(String prefix)
	{
		
	  Set<String> results = new HashSet<String>();
	  if((prefix == null) || (prefix.length() < 2)) return results.toArray(new String[results.size()]);
	  
	  char[] letters = prefix.toUpperCase ().toCharArray ();
	
	  long ptr = nodes[0];
	
	  for (char c: letters)
	  {
		  ptr = findChild (ptr, c);
	      if (-1 == ptr)
	        return results.toArray(new String[results.size()]);
	  }
	  
	  // iteratively (to prevent stack overflow) search each branch of the graph
	  Stack<StackEntry> stack = new Stack<StackEntry> ();  // a stack of paths to traverse. This prevents the StackOverflowException.
	  stack.push (new StackEntry (ptr, prefix.toUpperCase().toCharArray (), ""));
	  
	  while (!stack.empty ())
	  {
		  StackEntry entry = stack.pop ();
	
		  // if current node is a valid word
		  if (canTerminate(entry.node)) {
	          results.add(String.valueOf(entry.chars) + entry.subword);
	      }
	
		  for (Iterator<Long> iter = childIterator (entry.node); iter.hasNext ();)
		  {
			  long child = iter.next ();
			  stack.push(new StackEntry(child, entry.chars, entry.subword + getChar(child)));
		  }
	  }
	  
	  return results.toArray(new String[results.size()]);
	}
	
	
	/**
	 * Given a sequence of characters return the most probable valid words
	 * 
	 * 
	 */
	private boolean mAreTimeAvailable = true;
	private TimerTask mTimerTask = null;
	private Timer mTimer = null;
	public Set<SuggestionResult> searchMSD(String transWord, int maxCost, float insertionCost, float substitutionCost, 
			  float omissionCost, Distance distance)
	{
		// build first row
		List<Float> row = range(transWord.length() + 1);
	  
		// results
		Set<SuggestionResult> results = new HashSet<SuggestionResult>();
	  
		// iteratively (to prevent stack overflow) search each branch of the graph
		// a stack of paths to traverse. This prevents the StackOverflowException.
		Stack<StackLevenshteinEntry> stack = new Stack<StackLevenshteinEntry> (); 
		for (Iterator<Long> iter = childIterator (nodes[0]); iter.hasNext ();)
		{
			long child = iter.next ();
			char c = getChar(child);
		  
			StackLevenshteinEntry entry = new StackLevenshteinEntry (child, transWord.toUpperCase().toCharArray(), 
				  String.valueOf(c), row);
			stack.push(entry);
		}
		
		// thread to control time to search for suggestions
		mAreTimeAvailable = true;
		mTimerTask = new TimerTask() {
			
			@Override
			public void run() {
				//Log.v(BrailleSpellCheckerService.TAG, "Search Interrupted!");
				mAreTimeAvailable = false;
			}
		};
		mTimer = new Timer();
		mTimer.schedule(mTimerTask, 500); //500 ms to find all suggestions
	  
		while (!stack.empty () && mAreTimeAvailable)
		{
			StackLevenshteinEntry entry = stack.pop ();
			List<Float> previousRow = entry.previousRow;
		  
			int columns = entry.chars.length + 1;
			List<Float> currentRow = new LinkedList<Float>(); currentRow.add(previousRow.get(0) + omissionCost);
		  
			// build one row for the letter, with a column for each letter in the target word, 
			// plus one for the empty string at column 0
			for(int column = 1; column < columns; column++)
			{
				// cost * braille_distance
				float insertCost = currentRow.get(column - 1) + insertionCost;// * 
						//getInsertionDistance(entry.chars, column-1, getChar(entry.node));
				float omitCost = previousRow.get(column) + omissionCost;
				float substituteCost = previousRow.get(column - 1);
			  
				if(entry.chars[column - 1] != getChar(entry.node))
					substituteCost += substitutionCost * distance.getDistance(entry.chars[column - 1], 
							getChar(entry.node));
			  
				currentRow.add(Math.min(insertCost, Math.min(omitCost, substituteCost)));
			}
		  
			// if last entry in the row indicates the optimal cost is less than the maximum cost,
			// and there is a word in this node, then add it.
			int last = currentRow.size() - 1;
			if(currentRow.get(last) <= maxCost && canTerminate(entry.node))
			{
				results.add(new SuggestionResult(entry.subword, currentRow.get(last)));
			}
		  
			// if any entries in the row are less than the maximum cost, then iteratively search each branch
			if(min(currentRow) <= maxCost)
			{
				for (Iterator<Long> iter = childIterator (entry.node); iter.hasNext ();)
				{
					// get child
					long child = iter.next ();
			  
					// build subword
					StackLevenshteinEntry nextEntry = new StackLevenshteinEntry (child, entry.chars, 
							entry.subword + getChar(child), currentRow);
			  
					// search that branch
					stack.push(nextEntry);
				}
			}
		}

		mTimer.cancel();
		
		// return list of results
		return results;
  	}
	
	// not being used. performance trade-off its not worth it
	@SuppressWarnings("unused")
	private int getInsertionDistance(char[] chars, int index, char c)
	{
		if(index > 0  && index < chars.length - 1)
			// check next and previous character distances, and chose min
			return Math.min(ChordDistances.Damerau.getDistance(c, chars[index + 1]), 
					ChordDistances.Damerau.getDistance(c, chars[index - 1]));
		else if(index == 0) // index == 0
			// check next character
			return ChordDistances.Damerau.getDistance(c, chars[index + 1]);
		else // index == length - 1
			// check previous character
			return ChordDistances.Damerau.getDistance(c, chars[index - 1]);
	}
	
	
	  
	  private float min(List<Float> l)
	  {
		  float min = Float.MAX_VALUE;
		  for(float d : l)
			  if(d < min) min = d;
		  return min;
	  }
	  
	  private List<Float> range(int n)
	  {
		  List<Float> list = new LinkedList<Float>();
		  for(int i = 0; i < n; i++) list.add((float) i);
		  return list;
	  }
	  
	  public class SuggestionResult
	  {
		  public final String suggestion;
		  public float msdScore;

		  public SuggestionResult (String suggestion, float score)
		  {
			  this.suggestion = suggestion;
			  this.msdScore = score;
		  }
	  }
	  
	  public static String[] extractWords(SuggestionResult[] results)
	  {
		  return extractWords(results, results.length);
	  }
	  
	  public static String[] extractWords(SuggestionResult[] results, int index)
	  {
		  String[] words = new String[results.length > index ? index : results.length];
		  for(int i = 0; i < index && i < results.length; i++)
		  {
			  words[i] = results[i].suggestion;
		  }
		  return words;
	  }
	  
	  private class StackEntry
	  {
		  public final long node; // the current node to examine
		  public final char[] chars; // the available letters for word building
		  public final String subword; // the word path so far
		  
		  public StackEntry (long node, char[] chars, String subword)
		  {
			  this.node = node;             // the current node to examine
			  this.chars = chars.clone ();  // the available letters for word building
			  this.subword = subword;       // the letter path so far
		  }
	  }
	  
	  private class StackLevenshteinEntry extends StackEntry
	  {
		  public final List<Float> previousRow;
		  
		  public StackLevenshteinEntry (long node, char[] chars, String subword, List<Float> previousRow)
		  {
			  super(node, chars, subword);      
			  this.previousRow = previousRow; // previous row of MSD matrix
		  }
	  }
  
	/* PROTECTED METHODS */
  
	protected ChildIterator childIterator (long parent)
	{
		return new ChildIterator (parent);
	} 

	protected class ChildIterator implements Iterator<Long>
	{
		int childIndex;
		long child;
    
		private ChildIterator (long parent)
		{
			childIndex = getFirstChildIndex (parent);
		}
    
		public boolean hasNext ()
		{
			if (-1 == childIndex)
				return false;

			if (isLastChild (child))
				return false;

			return true;
		}

		public Long next ()
		{
			if (!hasNext ())
				throw new NoSuchElementException ();

			return (child = nodes[childIndex++]);
		}

		public void remove ()
		{
			throw new UnsupportedOperationException ("You may not remove children from this structure");
		}
	}

	protected Long findChild (long node, char c)
	{
		for (Iterator<Long> iter = childIterator (node); iter.hasNext ();)
		{
			long child = iter.next ();

			if (getChar (child) == c)
				return child;
			else if(getChar(child) > c) // children are ordered
				return (long) -1;
		}

		return (long) -1;
	}

	private static int getFirstChildIndex (long node)
	{
		return (int) (node >> 42);
	}

	private static boolean isLastChild (long node)
	{
		return (((node >> 41) & 0x1) == 1);
	}

	private static boolean canTerminate (long node)
	{
		return (((node >> 40) & 0x1) == 1);
	}

	private static char getChar (long node)
	{
		return (char) ((node >> 24) & 0xFFFF);
	}
  
	public static int getMPH(long node)
	{
		return (int)(node & 0xFFFFFF);
	}
 
}