package hugonicolau.openbrailleinput.soundmanager;

import java.util.HashMap;
import android.annotation.SuppressLint;
import hugonicolau.openbrailleinput.R;
import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

public class SoundManager {

	public static final int mClick = R.raw.click;
	public static final int mSwipe = R.raw.hide;
	public static final int mError = R.raw.error;
	public static final int mClose = R.raw.close;
	public static final int mOpen = R.raw.open;
	

	private static SoundPool soundPool;
	private static HashMap<Integer, Integer> soundPoolMap;

	/** Populate the SoundPool*/
	@SuppressLint("UseSparseArrays")
	public static void initSounds(Context context) {
	    soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 100);
		soundPoolMap = new HashMap<Integer, Integer>(2);     
		soundPoolMap.put( mClick, soundPool.load(context, R.raw.click, 1) );
		soundPoolMap.put( mSwipe, soundPool.load(context, R.raw.hide, 1) );
		soundPoolMap.put( mError, soundPool.load(context, R.raw.error, 1) );
		soundPoolMap.put( mClose, soundPool.load(context, R.raw.close, 1) );
		soundPoolMap.put( mOpen, soundPool.load(context, R.raw.open, 1) );
	}
	
	/** Play a given sound in the soundPool */
	public static void playSound(Context context, int soundID) {
		if(soundPool == null || soundPoolMap == null){
			initSounds(context);
		}
		
	    float volume = (float) 1.0; // whatever in the range = 0.0 to 1.0

	    // play sound with same right and left volume, with a priority of 1, 
	    // zero repeats (i.e play once), and a playback rate of 1f
	    soundPool.play((Integer)soundPoolMap.get(soundID), volume, volume, 1, 0, 1f);
	 }
}
