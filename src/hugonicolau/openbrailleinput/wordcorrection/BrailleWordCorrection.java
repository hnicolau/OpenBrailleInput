/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hugonicolau.openbrailleinput.wordcorrection;

import hugonicolau.openbrailleinput.R;
import hugonicolau.openbrailleinput.ime.OpenBrailleInput;
import hugonicolau.openbrailleinput.wordcorrection.mafsa.MAFSA;
import hugonicolau.openbrailleinput.wordcorrection.mafsa.MAFSA.SuggestionResult;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import android.content.Context;
import android.util.Log;

public class BrailleWordCorrection {
    
	private static BrailleWordCorrection mSharedInstance = null;
	
	private MAFSA mMAFSA = null;
	private float[] mFrequencies;
	public String Language = "";  
	
	protected BrailleWordCorrection() 
	{ 
	}
	
	public static BrailleWordCorrection getSharedInstance()
	{
		if(mSharedInstance == null) mSharedInstance = new BrailleWordCorrection();
		return mSharedInstance;
	}
	
	/**
	 * Score function, a lower score indicates a better match
	 * score = a . wMSD(Styped, Sword) + § . Äword
	 * a, §: weighting factors (derived from pilot data)
	 * Ä: frequency
	 * MSD substitution: cost = distance * sw
	 * MSD insertion: cost = distance * iw
	 * MSD omissions: cost = ow
	 */
    
    // empirical values
	float a = (float) 0.6484113;
	float b = (float) 0.35158873;
	float sw = (float) 1.6959325;
	float iw = (float) 0.41078573;
	float ow = (float) 1.8156104;
	
	// distance used to calculate similarity between words
	Distance mDistance = ChordDistances.Damerau;
	
	public String[] getSuggestions(String word, int suggestionsLimit) 
    {
    	if(word == null || mMAFSA == null) {return null;}
    	boolean exists = false;
    	
    	// get suggestions
    	//long startTime = System.currentTimeMillis();
    	int maxCost = 2;
    	if(word.length() > 5)
    	{
    		maxCost = 3;
        	//iw = (float) 1.5;
        	//ow = (float) 2;
    	}
    	
    	//println("Get suggestions for[" + word + "]");
    	
    	Set<SuggestionResult> results = mMAFSA.searchMSD(word, maxCost, iw, sw, ow, mDistance);
    	
		//long stopTime = System.currentTimeMillis();
		//println("Search time [" + (stopTime - startTime) + "] ms");
    	
		// calculate final score for each word
		//startTime = System.currentTimeMillis();
		for(SuggestionResult result : results)
		{
			if(result.suggestion.equalsIgnoreCase(word))
			{
				// if transcribed word exists, then it reveives the min score
				result.msdScore = -2;
				exists = true;
			}
			else
			{
				result.msdScore = a * (result.msdScore/maxCost) - b * getFrequency(result.suggestion);
			}
		}
		
		if(!exists && mMAFSA.contains(word)) results.add(mMAFSA.new SuggestionResult(word, -2));
		
		// blank space filter
		for(int i = 1; i < word.length(); i++)
		{
			String word1 = word.substring(0, i);
			String word2 = word.substring(i);
			if(mMAFSA.contains(word1) && mMAFSA.contains(word2))
			{
				SuggestionResult res = mMAFSA.new SuggestionResult(word1 + " " + word2, ow);
				
				float freq1 = getFrequency(word1);
				float freq2 = getFrequency(word2);
				
				res.msdScore = a * (res.msdScore/maxCost) - b * ((freq1 + freq2) / 2);
				res.msdScore *= 1.5; // penalize for being two words
				results.add(res);
			}
		}
		
		//stopTime = System.currentTimeMillis();
		//println("Scoring time [" + (stopTime - startTime) + "] ms");
		
		SuggestionResult[] resultsArray = results.toArray(new SuggestionResult[results.size()]);
		
		// sort 
		//startTime = System.currentTimeMillis();
		Quicksort sorter = new Quicksort();
		sorter.sort(resultsArray);
		//stopTime = System.currentTimeMillis();
		//println("Sort time [" + (stopTime - startTime) + "] ms");
		
		//if(results.size() == 0) println("Empty results for word[" + word + "]");
    	return MAFSA.extractWords(resultsArray, suggestionsLimit);
    }
	
	/* UTILS */
    private float getFrequency(String word)
    {        	
    	int hash = mMAFSA.wordToHash(word);
    	//println("Word[" + word + "] Hash[" + hash + "] Freq[" + mFrequencies[hash] + "]");
    	return mFrequencies[hash];
    }
	
	/*
     * LOADING METHODS 
     */
	public void load(Context context, String locale)
    {
		// load MA-FSA
     	InputStream is = null;
     	try 
     	{
     		if(locale.equalsIgnoreCase("0"))
 	    	{
 	    		// english
 	    		is = context.getResources().openRawResource(R.raw.android_en);
 	    		mDistance = ChordDistances.DamerauEN;
 	    	}
 	    	else if(locale.equalsIgnoreCase("1"))
 	    	{
 	    		// portuguese
 	    		is = context.getResources().openRawResource(R.raw.android_pt);
 	    		mDistance = ChordDistances.DamerauPT;
 	    	}
 	    	else
 	    	{
 	    		throw new IOException("Invalid locale");
 	    	}
         	
     		mMAFSA = MAFSA.load(is);
 			
 		}
 		catch (IOException ioe) 
 		{
 			// handle this exception
 			Log.v(OpenBrailleInput.TAG, "Couldn't load dawg" + ioe.getMessage());
 		}
 		finally 
 		{
 			IOUtils.closeQuietly(is);
 		}
     	
     	// load frequencies
     	is = null;
     	try 
     	{
     		if(locale.equalsIgnoreCase("0"))
 	    	{
     			// english
 	    		is = context.getResources().openRawResource(R.raw.freq_en);
 	    	}
 	    	else if(locale.equalsIgnoreCase("1"))
 	    	{
 	    		// portuguese
 	    		is = context.getResources().openRawResource(R.raw.freq_pt);
 	    	}
 	    	else
 	    	{
 	    		throw new IOException("Invalid locale");
 	    	}
     		
     		mFrequencies = loadFrequencies(is);
         	
         	if(mFrequencies == null) Log.v(OpenBrailleInput.TAG, "Couldn't load frequencies");
 			
 		}
 		catch (IOException ioe) 
 		{
 			// handle this exception
 			Log.v(OpenBrailleInput.TAG, "Couldn't load frequencies" + ioe.getMessage());
 		}
 		finally 
 		{
 			IOUtils.closeQuietly(is);
 		}
     	
     	Language = locale;
     }
     
     private float[] loadFrequencies(InputStream is) throws StreamCorruptedException, IOException
     {
     	BufferedInputStream bis = new BufferedInputStream (is, 8 * 1024);
 	    ObjectInputStream ois = new ObjectInputStream (bis);

 	    float[] floats = null;
 	
 	    try 
 	    {
 	    	floats = (float[]) ois.readObject ();
 		} 
 	    catch (ClassNotFoundException e) {
 			e.printStackTrace();
 		}
 	    
 	    return floats;
     }
    
    private class Quicksort  
    {
  	  private SuggestionResult[] data;
  	  private int number;

  	  public void sort(SuggestionResult[] values) {
  	    // check for empty or null array
  	    if (values == null || values.length == 0){
  	      return;
  	    }
  	    this.data = values;
  	    number = values.length;
  	    quicksort(0, number - 1);
  	  }

  	  private void quicksort(int low, int high) {
  	    int i = low, j = high;
  	    // Get the pivot element from the middle of the list
  	    SuggestionResult pivot = data[low + (high-low)/2];

  	    // Divide into two lists
  	    while (i <= j) {
  	      // If the current value from the left list is smaller then the pivot
  	      // element then get the next element from the left list
  	      while (data[i].msdScore < pivot.msdScore) {
  	        i++;
  	      }
  	      // If the current value from the right list is larger then the pivot
  	      // element then get the next element from the right list
  	      while (data[j].msdScore > pivot.msdScore) {
  	        j--;
  	      }

  	      // If we have found a values in the left list which is larger then
  	      // the pivot element and if we have found a value in the right list
  	      // which is smaller then the pivot element then we exchange the
  	      // values.
  	      // As we are done we can increase i and j
  	      if (i <= j) {
  	        exchange(i, j);
  	        i++;
  	        j--;
  	      }
  	    }
  	    // Recursion
  	    if (low < j)
  	      quicksort(low, j);
  	    if (i < high)
  	      quicksort(i, high);
  	  }

  	  private void exchange(int i, int j) {
  	    SuggestionResult temp = data[i];
  	    data[i] = data[j];
  	    data[j] = temp;
  	  }
    }
    
}
