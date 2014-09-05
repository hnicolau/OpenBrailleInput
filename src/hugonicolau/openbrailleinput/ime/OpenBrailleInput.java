/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package hugonicolau.openbrailleinput.ime;

import hugonicolau.openbrailleinput.drawing.Circle;
import hugonicolau.openbrailleinput.languagemanager.LanguageManager;
import hugonicolau.openbrailleinput.logmanager.LogManager;
import hugonicolau.openbrailleinput.soundmanager.SoundManager;
import hugonicolau.openbrailleinput.touchmodel.Pointer;
import hugonicolau.openbrailleinput.touchmodel.TouchModel;
import hugonicolau.openbrailleinput.touchmodel.VerticalComparator;
import hugonicolau.openbrailleinput.touchmodel.TouchModel.GESTURE_TYPE;
import hugonicolau.openbrailleinput.wordcorrection.BrailleWordCorrection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Point;
import android.inputmethodservice.InputMethodService;
import hugonicolau.openbrailleinput.R;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.Display;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * OpenBrailleInput IME
 * 
 * TODO: The code needs clean up and re-factoring
 */
public class OpenBrailleInput extends InputMethodService implements OnInitListener{
    public static final String TAG = "OpenBrailleInput"; // for debug purposes
    
    // openbraille view
    public View mInputView;
    private int mWidth = 0;
    private int mHeight = 0;
    public FrameLayout mTouchView;

    // services
    private Vibrator mVibrator = null;
    public final static long VIBRATION_LENGHT = 15; // minimum vibration length
    
    // openbraille vars
    private TouchModel mTouchModel = new TouchModel(mWidth);
    private StringBuilder mComposing = new StringBuilder();
    private Handler h = new Handler();
    private boolean mWasCalibration = false;
    private Pointer[] mLastFingersDown = new Pointer[6];
    private boolean mIsNumberState = false;
    private Handler mSpeakHandler = new Handler();
    private char mDelayedChar = Character.UNASSIGNED;
    
    // preferences
    public static SharedPreferences mSharedPrefs = null;
    private PreferenceChangeListener mSharedPrefsListener = null; // listener
    
    private final int CALIBRATION_TIMEOUT = 1000;
    
    // word correction
    String[] mSuggestions = null;
    private StringBuilder mWordCorrectionComposing = new StringBuilder();
    
    // tts vars
    static public TextToSpeech mTTS = null;
    private boolean mttsloaded = false;
   
    /********
     * IME LIFECYCLE
     ********/
    
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
    	
    	//android.os.Debug.waitForDebugger();  // this line is key for debugging
        super.onCreate();
   		
   		// initialize preferences
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPrefsListener = new PreferenceChangeListener();
        mSharedPrefs.registerOnSharedPreferenceChangeListener(mSharedPrefsListener);
   		
        // init SoundManager
        SoundManager.initSounds(getApplicationContext());
        
		// get vibration service
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        // load dictionaries for word correction
        loadWordCorrection();
    }
    
    // call when creating IME, change in language or auto-correct
    private void loadWordCorrection()
    {
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefAutoCorrect), false))
        {
        	BrailleWordCorrection.getSharedInstance().load(getApplicationContext(), 
        			mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0"));
        }
    }
    
    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
    	
        super.onStartInput(attribute, restarting);
        
        // save old centroids (in case the user hit the home button)
        saveCentroids();
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        mSuggestions = null;
        mWordCorrectionComposing.setLength(0);
        
    }
    
    public View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			
			// get finger count, event index and event id
			int countFingers = event.getPointerCount();
			int index = getIndex(event);
			int id = event.getPointerId(index);
			
			// logging actions
			switch(event.getAction() & MotionEvent.ACTION_MASK)
			{
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_POINTER_DOWN:
				{
					// log down action
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
						LogManager.getInstance().logDownEvent(id, event.getX(index), event.getY(index), 
							event.getSize(index), new Date(event.getEventTime()));
					break;
				}
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_POINTER_UP:
				{
					// log up action
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
						LogManager.getInstance().logUpEvent(id, event.getX(index), event.getY(index), 
							event.getSize(index), new Date(event.getEventTime()));
					break;
				}
				case MotionEvent.ACTION_MOVE: // a pointer was moved
				{
					for(int i = 0 ; i < countFingers; i++)
					{
						// log move action
						int pid = event.getPointerId(i);
						if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
							LogManager.getInstance().logMoveEvent(pid, event.getX(i), event.getY(i), 
								event.getSize(i), new Date(event.getEventTime()));
					}
					break;
				}
					
			}
			
			// update touch model state
			mTouchModel.update(event);
			
			switch(event.getAction() & MotionEvent.ACTION_MASK)
			{
				case MotionEvent.ACTION_DOWN:
				{	
					// vibrate device's built-in motor
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefVibration), true)) mVibrator.vibrate(VIBRATION_LENGHT * 2);
					
					// update finger/touch model on touchdown
					Pointer[] fingersDown = null;
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechDown), true))
					{
						fingersDown = mTouchModel.getFingersDown();
						
						// sound on touchdown
						char ch = getCharacter(	fingersDown[0] != null ? true : false,
												fingersDown[1] != null ? true : false,
												fingersDown[2] != null ? true : false,
												fingersDown[3] != null ? true : false,
												fingersDown[4] != null ? true : false,
												fingersDown[5] != null ? true : false);
						
						if(ch == Character.UNASSIGNED)
					    {
							// its not a valid letter
							ttsStop();
							//ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("invalid", LANGUAGE), TextToSpeech.QUEUE_FLUSH);
					    }
						else
						{
							ttsStop();
							ttsSpeak(ch, TextToSpeech.QUEUE_FLUSH);
						}
						mLastFingersDown = fingersDown;
					}
					break;
				}
				case MotionEvent.ACTION_UP:
				{					
					// if it was a calibration task, ignore up event
					if(mWasCalibration)
					{ 
						mWasCalibration = false; 
						mTouchModel.reset(); 
						return true;
					}
					
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechDown), true)) 
					{
						// ignore last delayed reading
						mSpeakHandler.removeCallbacks(mSpeakRunnable);
					}
					
					Pointer[] fingers = mTouchModel.getFingersUp(event.getEventTime());
					
					// check for gesture first
					GESTURE_TYPE gesture = mTouchModel.checkGesture(fingers);
					if(gesture != GESTURE_TYPE.NONE)
					{
						// it was a gesture
						switch(gesture)
						{
						case LEFT: swipeLeft(); break;
						case RIGHT: swipeRight(); break;
						case UP: swipeUp(); break;
						case DOWN: swipeDown(); break;
						case NONE: break;
						}
						
						mTouchModel.reset();
						return true;
					}
					
					// decode braille character
					char ch = getCharacter(	fingers[0] != null ? true : false,
											fingers[1] != null ? true : false,
											fingers[2] != null ? true : false,
											fingers[3] != null ? true : false,
											fingers[4] != null ? true : false,
											fingers[5] != null ? true : false);
					
					if(ch == Character.UNASSIGNED)
				    {
						// get chord - it's not a letter
						char invalid = getInvalidCharacter(	fingers[0] != null ? true : false,
													fingers[1] != null ? true : false,
													fingers[2] != null ? true : false,
													fingers[3] != null ? true : false,
													fingers[4] != null ? true : false,
													fingers[5] != null ? true : false);
						if(invalid != Character.UNASSIGNED) mWordCorrectionComposing.append(invalid);
				    }
					
					// send character to textview
					sendKeyChar(ch);
					
					// log character action
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
						LogManager.getInstance().logCharEvent(String.valueOf(ch),
											new Date(),
											fingers[0] != null ? fingers[0].getID() : -1,
											fingers[1] != null ? fingers[1].getID() : -1,
											fingers[2] != null ? fingers[2].getID() : -1,
											fingers[3] != null ? fingers[3].getID() : -1,
											fingers[4] != null ? fingers[4].getID() : -1,
											fingers[5] != null ? fingers[5].getID() : -1);
					
				    // finger tracking: update centroids
				    updateCentroids(fingers);
					
				    // initialize pointers
				    mTouchModel.reset();
				    
					return true;
				}
				case MotionEvent.ACTION_POINTER_DOWN:
				{
					// vibrate
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefVibration), true)) 
						mVibrator.vibrate(VIBRATION_LENGHT * 2);
					
					// update finger/touch model on touchdown
					Pointer[] fingersDown = null;
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechDown), true))
					{
						// ignore last delayed reading
						mSpeakHandler.removeCallbacks(mSpeakRunnable);
						
						fingersDown = mTouchModel.getFingersDown();
					
						// sound on touchdown
						char ch = getCharacter(	fingersDown[0] != null ? true : false,
												fingersDown[1] != null ? true : false,
												fingersDown[2] != null ? true : false,
												fingersDown[3] != null ? true : false,
												fingersDown[4] != null ? true : false,
												fingersDown[5] != null ? true : false);
						
						if(ch == Character.UNASSIGNED)
					    {
							// its not a valid letter
							ttsStop();
							//ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("invalid", LANGUAGE), TextToSpeech.QUEUE_FLUSH);
					    }
						else
						{
							
							ttsStop();
							ttsSpeak(ch, TextToSpeech.QUEUE_FLUSH);
						}
						mLastFingersDown = fingersDown;
					}
					
					// interrupt calibration process
					h.removeCallbacks(mStartCalibrationRunnable);
					h.removeCallbacks(mCalibrationRunnable);
					
					// start counting down to calibration
					if(countFingers == 6)
					{
						h.postDelayed(mStartCalibrationRunnable, CALIBRATION_TIMEOUT);
					};
					
					break;
				}
				case MotionEvent.ACTION_POINTER_UP:
				{
					// interrupt calibration process
					if(countFingers == 6 && !mWasCalibration)
					{
						//mWasCalibration = false;
						h.removeCallbacks(mStartCalibrationRunnable);
						h.removeCallbacks(mCalibrationRunnable);
					}
					
					// update finger/touch model on touchup					
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechDown), true)
							&& !mWasCalibration)
					{
						// ignore last delayed reading
						mSpeakHandler.removeCallbacks(mSpeakRunnable);
						
						Pointer[] fingersDown = mTouchModel.getFingersDown();
						
						char ch = getCharacter(	fingersDown[0] != null ? true : false,
												fingersDown[1] != null ? true : false,
												fingersDown[2] != null ? true : false,
												fingersDown[3] != null ? true : false,
												fingersDown[4] != null ? true : false,
												fingersDown[5] != null ? true : false);
						
						mDelayedChar = ch;
						// wait 200ms to read character; prevents unwanted readings
						mSpeakHandler.postDelayed(mSpeakRunnable, 200);
						/*if(ch == Character.UNASSIGNED)
					    {
							// its not a valid letter
							ttsStop();
							//ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("invalid", LANGUAGE), TextToSpeech.QUEUE_FLUSH);
					    }
						else
						{
							ttsStop();
							ttsSpeak(ch, TextToSpeech.QUEUE_FLUSH);
						}*/
						mLastFingersDown = fingersDown;
					}
					break;
				}
				case MotionEvent.ACTION_MOVE:
				{
					// update finger/touch model on move					
					if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechDown), true)
							&& !mWasCalibration)
					{			
						Pointer[] fingersDown = mTouchModel.getFingersDown();
						
						// check if there are differences since the last move
						if(mTouchModel.areDifferences(mLastFingersDown, fingersDown))
						{
							// ignore last delayed reading
							mSpeakHandler.removeCallbacks(mSpeakRunnable);
							
							char ch = getCharacter(	fingersDown[0] != null ? true : false,
													fingersDown[1] != null ? true : false,
													fingersDown[2] != null ? true : false,
													fingersDown[3] != null ? true : false,
													fingersDown[4] != null ? true : false,
													fingersDown[5] != null ? true : false);
							
							if(ch == Character.UNASSIGNED)
						    {
								// its not a valid letter
								ttsStop();
								//ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("invalid", LANGUAGE), TextToSpeech.QUEUE_FLUSH);
						    }
							else
							{
								ttsStop();
								ttsSpeak(ch, TextToSpeech.QUEUE_FLUSH);
							}
							
							mLastFingersDown = fingersDown;
						}
					}
					break;
				}
			}
						
			return true;
		}
	};
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override
    public View onCreateInputView() {
    	
    	// get root inputview
    	mInputView = (View) getLayoutInflater().inflate(R.layout.openbrailleinput, null);
  
        //TTS
        if(!mttsloaded)
        {
        	mTTS = new TextToSpeech(this, this); //wait for TTS init
        }
        
        updateWindowSize();
        
        // set fullscreen overlay
        mTouchView = (FrameLayout)mInputView.findViewById(R.id.fl1);
        mTouchView.setLayoutParams(new FrameLayout.LayoutParams(mWidth, mHeight));
      
        // brailletouch code
        mTouchView.setOnTouchListener(mTouchListener);
        
        return mInputView;
    }
    
    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override
    public View onCreateCandidatesView() 
    {
    	
    	return super.onCreateCandidatesView();
    }
    
    /**
     * This is called when the user starts editing a field.  We can use
     * this to reset our state.
     */
    @Override
   	public void onStartInputView(EditorInfo info, boolean restarting) 
    {
    	
   		super.onStartInputView(info, restarting);
           
   		startInput();
    }
    
    public void startInput()
    {
        if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSound), true))
        {
        	SoundManager.playSound(getApplicationContext(), SoundManager.mOpen);
        }
        
    	// start a new logging session in LogManager
        if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
        	LogManager.getInstance().startSession();
    	
   		// restore centroids
        restoreCentroids();
        
        reDrawCentrois();
    }
    
    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
    	
        super.onFinishInput();

        // Clear current composing text and candidates.
        mComposing.setLength(0);
        mSuggestions = null;
        mWordCorrectionComposing.setLength(0);
    }
    
    /**
     * This is called when the user is done using the IME.
     */
    @Override public void onDestroy()
    {	
    	
    	if(mTTS != null)
    	{
    		mTTS.stop();
    		mTTS.shutdown();
    	}
    	
    	super.onDestroy();
    }
    
    /**
     * This is called when the IME is shown.  We can use
     * this to update our state.
     */
    @Override public void onWindowShown()
    {
    	
    }
    
    /**
     * This is called when the IME is hidden.  We can use
     * this to save our state.
     */
    @Override public void onWindowHidden()
    {
    	endInput();
    }
    
    public void endInput()
    {
    	// save centroids
    	saveCentroids();
    	
    	// save touches
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
    	{
    		String fileName = LogManager.getInstance().endSession();
    		// send filename to editor
    		Bundle data = new Bundle();
    		data.putString("filename", fileName);
    		Intent command = new Intent("imelistener");
            command.putExtra("filename", fileName);
            getApplicationContext().sendBroadcast(command);
        }
    	
    	mComposing = new StringBuilder();
    	mWordCorrectionComposing = new StringBuilder();
    }
    
    /********
     * MANAGE PREFERNCES AND IME STATE (finger centroids)
     ********/
    
    private void updateWindowSize()
    {
    	
    	// get window size
        WindowManager wm = (WindowManager) this.getSystemService(OpenBrailleInput.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int width=display.getWidth();
        int height=display.getHeight();
        
        if(width != mWidth || height != mHeight)
        {
        	saveCentroids();
        	
        	// rotation just happened
        	mWidth = width;
        	mHeight = height;
        	
        	// update recognizer
        	mTouchModel.setWidth(mWidth);
        }
    }
    
    private void saveCentroids()
    {
    	
    	if(mTouchModel.getFingerCentroids() == null) return;
    	for(int i = 0; i < 6; i++) if (mTouchModel.getFingerCentroids()[i] == null) return;
    	
    	if(isLandscapeMode())
    	{
    		// landscape mode
	    	// save prefs
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_1X_LANDSCAPE), mTouchModel.getFingerCentroids()[0].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_2X_LANDSCAPE), mTouchModel.getFingerCentroids()[1].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_3X_LANDSCAPE), mTouchModel.getFingerCentroids()[2].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_4X_LANDSCAPE), mTouchModel.getFingerCentroids()[3].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_5X_LANDSCAPE), mTouchModel.getFingerCentroids()[4].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_6X_LANDSCAPE), mTouchModel.getFingerCentroids()[5].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_1Y_LANDSCAPE), mTouchModel.getFingerCentroids()[0].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_2Y_LANDSCAPE), mTouchModel.getFingerCentroids()[1].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_3Y_LANDSCAPE), mTouchModel.getFingerCentroids()[2].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_4Y_LANDSCAPE), mTouchModel.getFingerCentroids()[3].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_5Y_LANDSCAPE), mTouchModel.getFingerCentroids()[4].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_6Y_LANDSCAPE), mTouchModel.getFingerCentroids()[5].y).commit();
    	}
    	else
    	{
    		// portrait mode
	    	// save prefs
    		mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_1X_PORTRAIT), mTouchModel.getFingerCentroids()[0].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_2X_PORTRAIT), mTouchModel.getFingerCentroids()[1].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_3X_PORTRAIT), mTouchModel.getFingerCentroids()[2].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_4X_PORTRAIT), mTouchModel.getFingerCentroids()[3].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_5X_PORTRAIT), mTouchModel.getFingerCentroids()[4].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_6X_PORTRAIT), mTouchModel.getFingerCentroids()[5].x).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_1Y_PORTRAIT), mTouchModel.getFingerCentroids()[0].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_2Y_PORTRAIT), mTouchModel.getFingerCentroids()[1].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_3Y_PORTRAIT), mTouchModel.getFingerCentroids()[2].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_4Y_PORTRAIT), mTouchModel.getFingerCentroids()[3].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_5Y_PORTRAIT), mTouchModel.getFingerCentroids()[4].y).commit();
	        mSharedPrefs.edit().putInt(getResources().getString(R.string.prefCentroid_6Y_PORTRAIT), mTouchModel.getFingerCentroids()[5].y).commit();
    	}
    }
	
    private boolean isLandscapeMode()
    {
        return mWidth > mHeight;
    }
    
    private void restoreCentroids()
    {
    	// get window size
        WindowManager wm = (WindowManager) this.getSystemService(OpenBrailleInput.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        mWidth=display.getWidth();
        mTouchModel.setWidth(mWidth);
        mHeight=display.getHeight();
        
        // calculate default centroids
        Point c4 = new Point((int)(mWidth / 2 / 2), (int)(mHeight / 3 / 2 - 20));
		Point c5 = new Point((int)c4.x, (int)(c4.y + mHeight / 3));
		Point c6 = new Point((int)c4.x, (int)(c5.y + mHeight / 3));
		Point c1 = new Point((int)(c4.x + mWidth / 2), c4.y);
		Point c2 = new Point((int)c1.x, (int)(c1.y + mHeight / 3));
		Point c3 = new Point((int)c1.x, (int)(c2.y + mHeight / 3));
		
		// create centroids
		mTouchModel.getFingerCentroids()[0] = new Point(0,0);
		mTouchModel.getFingerCentroids()[1] = new Point(0,0);
		mTouchModel.getFingerCentroids()[2] = new Point(0,0);
		mTouchModel.getFingerCentroids()[3] = new Point(0,0);
		mTouchModel.getFingerCentroids()[4] = new Point(0,0);
		mTouchModel.getFingerCentroids()[5] = new Point(0,0);
		
        if(mWidth > mHeight)
        {
        	// landscape mode
        	mTouchModel.getFingerCentroids()[0].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_1X_LANDSCAPE), c1.x);
        	mTouchModel.getFingerCentroids()[1].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_2X_LANDSCAPE), c2.x);
        	mTouchModel.getFingerCentroids()[2].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_3X_LANDSCAPE), c3.x);
        	mTouchModel.getFingerCentroids()[3].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_4X_LANDSCAPE), c4.x);
        	mTouchModel.getFingerCentroids()[4].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_5X_LANDSCAPE), c5.x);
        	mTouchModel.getFingerCentroids()[5].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_6X_LANDSCAPE), c6.x);
        	mTouchModel.getFingerCentroids()[0].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_1Y_LANDSCAPE), c1.y);
        	mTouchModel.getFingerCentroids()[1].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_2Y_LANDSCAPE), c2.y);
        	mTouchModel.getFingerCentroids()[2].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_3Y_LANDSCAPE), c3.y);
        	mTouchModel.getFingerCentroids()[3].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_4Y_LANDSCAPE), c4.y);
        	mTouchModel.getFingerCentroids()[4].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_5Y_LANDSCAPE), c5.y);
        	mTouchModel.getFingerCentroids()[5].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_6Y_LANDSCAPE), c6.y);
        }
        else
        {
        	// portrait mode
        	mTouchModel.getFingerCentroids()[0].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_1X_PORTRAIT), c1.x);
        	mTouchModel.getFingerCentroids()[1].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_2X_PORTRAIT), c2.x);
        	mTouchModel.getFingerCentroids()[2].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_3X_PORTRAIT), c3.x);
        	mTouchModel.getFingerCentroids()[3].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_4X_PORTRAIT), c4.x);
        	mTouchModel.getFingerCentroids()[4].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_5X_PORTRAIT), c5.x);
        	mTouchModel.getFingerCentroids()[5].x = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_6X_PORTRAIT), c6.x);
        	mTouchModel.getFingerCentroids()[0].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_1Y_PORTRAIT), c1.y);
        	mTouchModel.getFingerCentroids()[1].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_2Y_PORTRAIT), c2.y);
        	mTouchModel.getFingerCentroids()[2].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_3Y_PORTRAIT), c3.y);
        	mTouchModel.getFingerCentroids()[3].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_4Y_PORTRAIT), c4.y);
        	mTouchModel.getFingerCentroids()[4].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_5Y_PORTRAIT), c5.y);
        	mTouchModel.getFingerCentroids()[5].y = mSharedPrefs.getInt(getResources().getString(R.string.prefCentroid_6Y_PORTRAIT), c6.y);
        }
        	
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
    		LogManager.getInstance().logCentroidUpdateEvent(mTouchModel.getFingerCentroids(), new Date());
    }
	
    private void updateCentroids(Pointer[] fingers)
    {
    	// if finger tracking is disabled
    	if(!mSharedPrefs.getBoolean(getResources().getString(R.string.prefFingerTracking), true)) return;
    	
    	mTouchModel.updateFingerCentroids(fingers);
    	
    	// log centroids
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
    		LogManager.getInstance().logCentroidUpdateEvent(mTouchModel.getFingerCentroids(), new Date());
    	
    	reDrawCentrois();
    }
 
    private void drawCentroids(FrameLayout touchview)
    {   
    	for(int i=0; i<6; i++)
        {
    		touchview.addView(new Circle(getApplicationContext(), mTouchModel.getFingerCentroids()[i].x, 
    				mTouchModel.getFingerCentroids()[i].y, 30));
        }
    	
    	if(mSuggestions != null && mSuggestions.length > 0)
    	{
    		for(int i = 0; i < mSuggestions.length; i++)
    		{
    			TextView s = new TextView(getApplicationContext());
    			s.setTextSize(20);
        		s.setX(mWidth / 2 - 50);
        		s.setY(300 + i * 60);
        		s.setText(mSuggestions[i]);
        		touchview.addView(s);
    		}
    	}
    }
    
    private void reDrawCentrois()
    {
    	FrameLayout touchview = (FrameLayout)mInputView.findViewById(R.id.fl1);
    	touchview.removeAllViews();
    	drawCentroids(touchview);
    }
    
    /**
     * Get pointer index
     */
    private int getIndex(MotionEvent event) {
    	 
    	  int idx = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    	  return idx;
    }
    
    /********
     * HELPER FUNCTIONS FOR BRAILLETOUCH
     ********/
    
    /**
     * Braille coding, TODO new class: BrailleDecoder
     */
    private char getCharacter(boolean finger1, boolean finger2, boolean finger3, boolean finger4, boolean finger5, 
    		boolean finger6){
    	
    	// numbers
    	if(mIsNumberState)
    	{
    		if(finger1 && !finger2 && !finger3 && !finger4 && !finger5 && !finger6) return '1';
        	else if(finger1 && finger2 && !finger3 && !finger4 && !finger5 && !finger6) return '2';
        	else if(finger1 && !finger2 && !finger3 && finger4 && !finger5 && !finger6) return '3';
        	else if(finger1 && !finger2 && !finger3 && finger4 && finger5 && !finger6) return '4';
        	else if(finger1 && !finger2 && !finger3 && !finger4 && finger5 && !finger6) return '5';
        	else if(finger1 && finger2 && !finger3 && finger4 && !finger5 && !finger6) return '6';
        	else if(finger1 && finger2 && !finger3 && finger4 && finger5 && !finger6) return '7';
        	else if(finger1 && finger2 && !finger3 && !finger4 && finger5 && !finger6) return '8';
        	else if(!finger1 && finger2 && !finger3 && finger4 && !finger5 && !finger6) return '9';
        	else if(!finger1 && finger2 && !finger3 && finger4 && finger5 && !finger6) return '0';
        	else return Character.UNASSIGNED;
    	}
    	
    	// letters
    	if(finger1 && !finger2 && !finger3 && !finger4 && !finger5 && !finger6) return 'a';
    	else if(finger1 && finger2 && !finger3 && !finger4 && !finger5 && !finger6) return 'b';
    	else if(finger1 && !finger2 && !finger3 && finger4 && !finger5 && !finger6) return 'c';
    	else if(finger1 && !finger2 && !finger3 && finger4 && finger5 && !finger6) return 'd';
    	else if(finger1 && !finger2 && !finger3 && !finger4 && finger5 && !finger6) return 'e';
    	else if(finger1 && finger2 && !finger3 && finger4 && !finger5 && !finger6) return 'f';
    	else if(finger1 && finger2 && !finger3 && finger4 && finger5 && !finger6) return 'g';
    	else if(finger1 && finger2 && !finger3 && !finger4 && finger5 && !finger6) return 'h';
    	else if(!finger1 && finger2 && !finger3 && finger4 && !finger5 && !finger6) return 'i';
    	else if(!finger1 && finger2 && !finger3 && finger4 && finger5 && !finger6) return 'j';
    	else if(finger1 && !finger2 && finger3 && !finger4 && !finger5 && !finger6) return 'k';
    	else if(finger1 && finger2 && finger3 && !finger4 && !finger5 && !finger6) return 'l';
    	else if(finger1 && !finger2 && finger3 && finger4 && !finger5 && !finger6) return 'm';
    	else if(finger1 && !finger2 && finger3 && finger4 && finger5 && !finger6) return 'n';
    	else if(finger1 && !finger2 && finger3 && !finger4 && finger5 && !finger6) return 'o';
    	else if(finger1 && finger2 && finger3 && finger4 && !finger5 && !finger6) return 'p';
    	else if(finger1 && finger2 && finger3 && finger4 && finger5 && !finger6) return 'q';
    	else if(finger1 && finger2 && finger3 && !finger4 && finger5 && !finger6) return 'r';
    	else if(!finger1 && finger2 && finger3 && finger4 && !finger5 && !finger6) return 's';
    	else if(!finger1 && finger2 && finger3 && finger4 && finger5 && !finger6) return 't';
    	else if(finger1 && !finger2 && finger3 && !finger4 && !finger5 && finger6) return 'u';
    	else if(finger1 && finger2 && finger3 && !finger4 && !finger5 && finger6) return 'v';
    	else if(!finger1 && finger2 && !finger3 && finger4 && finger5 && finger6) return 'w';
    	else if(finger1 && !finger2 && finger3 && finger4 && !finger5 && finger6) return 'x';
    	else if(finger1 && !finger2 && finger3 && finger4 && finger5 && finger6) return 'y';
    	else if(finger1 && !finger2 && finger3 && !finger4 && finger5 && finger6) return 'z';
    	else if(!finger1 && !finger2 && finger3 && finger4 && finger5 && finger6)
    	{
    		return Character.LETTER_NUMBER;
    	}
    	
    	// symbols and accents
    	String lang = mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0");
    	if(lang.equalsIgnoreCase("0"))
    	{
    		// english
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSymbols), true))
    		{
    			// symbols
	    		if(!finger1 && finger2 && !finger3 && !finger4 && !finger5 && !finger6) return ',';
	    		else if(!finger1 && finger2 && !finger3 && !finger4 && finger5 && finger6) return '.';
	    		else if(!finger1 && finger2 && finger3 && !finger4 && !finger5 && finger6) return '?';
    		}
    	}
    	else if(lang.equalsIgnoreCase("1"))
    	{
    		// portuguese
    		// accents
    		if(finger1 && finger2 && finger3 && finger4 && finger5 && finger6) return 'é';
    		else if(finger1 && finger2 && finger3 && finger4 && !finger5 && finger6) return 'ç';
    		else if(!finger1 && finger2 && finger3 && finger4 && finger5 && finger6) return 'ú';
    		else if(finger1 && finger2 && finger3 && !finger4 && finger5 && finger6) return 'á';
    		else if(!finger1 && finger2 && !finger3 && finger4 && !finger5 && finger6) return 'õ';
    		else if(finger1 && finger2 && !finger3 && !finger4 && !finger5 && finger6) return 'ê';
    		else if(!finger1 && !finger2 && finger3 && finger4 && finger5 && !finger6) return 'ã';
    		else if(!finger1 && !finger2 && finger3 && finger4 && !finger5 && !finger6) return 'í';
    		else if(finger1 && !finger2 && !finger3 && !finger4 && !finger5 && finger6) return 'â';
    		else if(finger1 && finger2 && !finger3 && finger4 && !finger5 && finger6) return 'à';
    		else if(finger1 && !finger2 && !finger3 && finger4 && finger5 && finger6) return 'ô';
    		else if(!finger1 && !finger2 && finger3 && finger4 && !finger5 && finger6) return 'ó';
    		
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSymbols), true))
    		{
    			// symbols
	    		if(!finger1 && finger2 && !finger3 && !finger4 && !finger5 && !finger6) return ',';
	    		else if(!finger1 && !finger2 && finger3 && !finger4 && !finger5 && !finger6) return '.';
	    		else if(!finger1 && finger2 && !finger3 && !finger4 && !finger5 && finger6) return '?';
    		}
    	}
    	
    	return Character.UNASSIGNED;
    }
    
    private char getInvalidCharacter(boolean finger1, boolean finger2, boolean finger3, boolean finger4, boolean finger5, 
    		boolean finger6){
    	char ch = Character.UNASSIGNED;
    	
    	if(mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0").equalsIgnoreCase("0"))
    	{
    		// English decoder
	    	//1-point (63)
	    	if(!finger1 && finger2 && !finger3 && !finger4 && !finger5 && !finger6) ch = ','; // valid
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && !finger5 && !finger6) ch = '\''; // valid
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && !finger5 && !finger6) ch = 'å';
	    	else if(!finger1 && !finger2 && !finger3 && !finger4 && finger5 && !finger6) ch = 'ƒ';
	    	else if(!finger1 && !finger2 && !finger3 && !finger4 && !finger5 && finger6) ch = '§';  	
	    	//2-point (57)
	    	else if(finger1 && !finger2 && !finger3 && !finger4 && !finger5 && finger6) ch = '†';   	
	    	else if(!finger1 && finger2 && finger3 && !finger4 && !finger5 && !finger6) ch = ';'; // valid
	    	else if(!finger1 && finger2 && !finger3 && !finger4 && finger5 && !finger6) ch = ':'; // valid
	    	else if(!finger1 && finger2 && !finger3 && !finger4 && !finger5 && finger6) ch = '¢'; 	
	    	else if(!finger1 && !finger2 && finger3 && finger4 && !finger5 && !finger6) ch = '/'; // valid
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && finger5 && !finger6) ch = '∞';
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && !finger5 && finger6) ch = '-'; // valid   	
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && finger5 && !finger6) ch = 'ß';
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && !finger5 && finger6) ch = '©';  	
	    	else if(!finger1 && !finger2 && !finger3 && !finger4 && finger5 && finger6) ch = '∆'; 	
	    	//3-point (42)
	    	else if(finger1 && finger2 && !finger3 && !finger4 && !finger5 && finger6) ch = 'Ω';  	
	    	else if(finger1 && !finger2 && !finger3 && finger4 && !finger5 && finger6) ch = '´';
	    	else if(finger1 && !finger2 && !finger3 && !finger4 && finger5 && finger6) ch = '¨';  	
	    	else if(!finger1 && finger2 && finger3 && !finger4 && finger5 && !finger6) ch = '!'; // valid
	    	else if(!finger1 && finger2 && finger3 && !finger4 && !finger5 && finger6) ch = '?'; // valid
	    	else if(!finger1 && finger2 && !finger3 && finger4 && !finger5 && finger6) ch = '^';
	    	else if(!finger1 && finger2 && !finger3 && !finger4 && finger5 && finger6) ch = '.'; // valid   	
	    	else if(!finger1 && !finger2 && finger3 && finger4 && finger5 && !finger6) ch = '@'; // valid
	    	else if(!finger1 && !finger2 && finger3 && finger4 && !finger5 && finger6) ch = 'œ';
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && finger5 && finger6) ch = '¬';  	
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && finger5 && finger6) ch = '∂';	
	    	//4-point (22)
	    	else if(finger1 && finger2 && !finger3 && finger4 && !finger5 && finger6) ch = '®';
	    	else if(!finger1 && finger2 && finger3 && finger4 && !finger5 && finger6) ch = 'ª';
	    	else if(finger1 && finger2 && !finger3 && !finger4 && finger5 && finger6) ch = '¥';
	    	else if(!finger1 && finger2 && finger3 && !finger4 && finger5 && finger6) ch = '='; // valid    	
	    	else if(finger1 && !finger2 && !finger3 && finger4 && finger5 && finger6) ch = '∑'; 
	    	else if(!finger1 && !finger2 && finger3 && finger4 && finger5 && finger6) ch = '¡';
	    	//5-point (7)
	    	else if(finger1 && finger2 && finger3 && finger4 && !finger5 && finger6) ch = '•';
	    	else if(finger1 && finger2 && finger3 && !finger4 && finger5 && finger6) ch = 'π';
	    	else if(finger1 && finger2 && !finger3 && finger4 && finger5 && finger6) ch = 'ø';
	    	else if(!finger1 && finger2 && finger3 && finger4 && finger5 && finger6) ch = 'º';
	    	//6-point
	    	else if(finger1 && finger2 && finger3 && finger4 && finger5 && finger6) ch = '¶';
    	}
    	else if(mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0").equalsIgnoreCase("1"))
    	{
    		// Portuguese
    		//1-point (63)
	    	if(!finger1 && finger2 && !finger3 && !finger4 && !finger5 && !finger6) ch = ','; // valid
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && !finger5 && !finger6) ch = '.'; // valid
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && !finger5 && !finger6) ch = '§';
	    	else if(!finger1 && !finger2 && !finger3 && !finger4 && finger5 && !finger6) ch = '•';
	    	else if(!finger1 && !finger2 && !finger3 && !finger4 && !finger5 && finger6) ch = '\''; // valid  	
	    	//2-point (57) 	
	    	else if(!finger1 && finger2 && finger3 && !finger4 && !finger5 && !finger6) ch = ';'; // valid
	    	else if(!finger1 && finger2 && !finger3 && !finger4 && finger5 && !finger6) ch = ':'; // valid
	    	else if(!finger1 && finger2 && !finger3 && !finger4 && !finger5 && finger6) ch = '?'; // valid	
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && finger5 && !finger6) ch = '*'; // valid
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && !finger5 && finger6) ch = '-'; // valid   	
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && finger5 && !finger6) ch = '¶';
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && !finger5 && finger6) ch = 'º';  	
	    	else if(!finger1 && !finger2 && !finger3 && !finger4 && finger5 && finger6) ch = '$'; 	
	    	//3-point (42)
	    	else if(finger1 && !finger2 && !finger3 && finger4 && !finger5 && finger6) ch = 'ì';
	    	else if(finger1 && !finger2 && !finger3 && !finger4 && finger5 && finger6) ch = '@'; // valid  	
	    	else if(!finger1 && finger2 && finger3 && !finger4 && finger5 && !finger6) ch = '!'; // valid
	    	else if(!finger1 && finger2 && finger3 && !finger4 && !finger5 && finger6) ch = '"'; // valid
	    	else if(!finger1 && finger2 && !finger3 && !finger4 && finger5 && finger6) ch = '/'; // valid   	
	    	else if(!finger1 && !finger2 && finger3 && !finger4 && finger5 && finger6) ch = '∞';  	
	    	else if(!finger1 && !finger2 && !finger3 && finger4 && finger5 && finger6) ch = '|'; // valid	
	    	//4-point (22)
	    	else if(!finger1 && finger2 && finger3 && finger4 && !finger5 && finger6) ch = 'è';
	    	else if(finger1 && finger2 && !finger3 && !finger4 && finger5 && finger6) ch = 'ü';
	    	else if(!finger1 && finger2 && finger3 && !finger4 && finger5 && finger6) ch = '¢';    	 
	    	else if(!finger1 && !finger2 && finger3 && finger4 && finger5 && finger6) ch = '¡';
	    	//5-point (7)
	    	else if(finger1 && finger2 && !finger3 && finger4 && finger5 && finger6) ch = 'ï';
    	}
    	return ch;
    }
    final protected boolean[][] mFingers = 
    {
    		{false, false, false, false, false, false},
    		{true, false, false, false, false, false},
    		{true, true, false, false, false, false},
    		{true, false, false, true, false, false},
    		{true, false, false, true, true, false},
    		{true, false, false, false, true, false},
    		{true, true, false, true, false, false},
    		{true, true, false, true, true, false},
    		{true, true, false, false, true, false},
    		{false, true, false, true, false, false},
    		{false, true, false, true, true, false},
    		{true, false, true, false, false, false},
    		{true, true, true, false, false, false},
    		{true, false, true, true, false, false},
    		{true, false, true, true, true, false},
    		{true, false, true, false, true, false},
    		{true, true, true, true, false, false},
    		{true, true, true, true, true, false},
    		{true, true, true, false, true, false},
    		{false, true, true, true, false, false},
    		{false, true, true, true, true, false},
    		{true, false, true, false, false, true},
    		{true, true, true, false, false, true},
    		{false, true, false, true, true, true},
    		{true, false, true, true, false, true},
    		{true, false, true, true, true, true},
    		{true, false, true, false, true, true}
    };
    
    private boolean isValidChar(char c)
    {
    	c = Character.toLowerCase(c);
    	if((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
    		return true;
    	
    	String lang = mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0");
    	if(lang.equalsIgnoreCase("0"))
    	{
    		if(c == ',' || c == '.' || c =='?' || c == ' ') return true;
    	}
    	else if(lang.equalsIgnoreCase("1"))
    	{
    		if(c == 'é' || c=='ç' || c == 'ú' || c == 'á' || c == 'õ' || c == 'ê' || c == 'ã' || c == 'í' ||
    				c == 'â' || c == 'à' || c == 'ô' || c == 'ó') return true;
    	}
    	return false;
    }
    
    public Runnable mSpeakRunnable = new Runnable() {
		
    	@Override
    	public void run() {
			ttsStop();
			if(mDelayedChar != Character.UNASSIGNED)
			{
				ttsSpeak(mDelayedChar, TextToSpeech.QUEUE_FLUSH);
				mDelayedChar = Character.UNASSIGNED;
			}
		}
	};
    
    /**
     * Helper for calibration alert
     */
    public Runnable mStartCalibrationRunnable = new Runnable() {
		
		@Override
		public void run() {
			ttsStop();
			ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("calibrationstarted", 
					mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0")), TextToSpeech.QUEUE_FLUSH);
			h.postDelayed(mCalibrationRunnable, 2000);
		}
	};
	
	/**
     * Helper for finger calibration. Supports calibration with 5 fingers by estimating the 6th finger
     */
	public Runnable mCalibrationRunnable = new Runnable() {
		
		@Override
		public void run() {
			
			mWasCalibration = true;
			
			// calibrate fingers
			// finger 1 and 4 have to be closest to the top, while 3 and 6 closest to the bottom
			
			// divide by side
			List<Pointer> upPointersRight = new ArrayList<Pointer>();
			List<Pointer> upPointersLeft = new ArrayList<Pointer>();
			
			Iterator<String> myVeryOwnIterator = mTouchModel.getFingerPointers().keySet().iterator();
			while(myVeryOwnIterator.hasNext()) {
			    String key=(String)myVeryOwnIterator.next();
			    Pointer value=(Pointer)mTouchModel.getFingerPointers().get(key);
			    
			    if(value.getLastKnownPosition().x < mWidth / 2)
			    {
			    	//right
					upPointersRight.add(value);
			    }
			    else
			    {
			    	//left
					upPointersLeft.add(value);
			    }
			}
			
			// sort by closest to the top
			Collections.sort(upPointersRight, new VerticalComparator());
			Collections.sort(upPointersLeft, new VerticalComparator());
			
			if(upPointersLeft.size() == 3)
			{
				mTouchModel.getFingerCentroids()[0] = upPointersLeft.get(0).getLastKnownPosition();
				mTouchModel.getFingerCentroids()[1] = upPointersLeft.get(1).getLastKnownPosition();
				mTouchModel.getFingerCentroids()[2] = upPointersLeft.get(2).getLastKnownPosition();
				
				if(upPointersRight.size() >= 2) // device only supports 5 fingers
				{
					mTouchModel.getFingerCentroids()[3] = upPointersRight.get(0).getLastKnownPosition();
					mTouchModel.getFingerCentroids()[4] = upPointersRight.get(1).getLastKnownPosition();
					if(upPointersRight.size() == 3){
						mTouchModel.getFingerCentroids()[5] = upPointersRight.get(2).getLastKnownPosition(); // device supports 6 fingers
					}
					else
					{
						mTouchModel.getFingerCentroids()[5] = new Point(upPointersRight.get(0).getLastKnownPosition().x, 
								upPointersLeft.get(2).getLastKnownPosition().y); // estimate sixth finger
					}
				}
				
				if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
					LogManager.getInstance().logCentroidCalibrationEvent(mTouchModel.getFingerCentroids(), new Date());
				
				reDrawCentrois();
				
				ttsStop();
				ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("calibrationended", 
						mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0")), TextToSpeech.QUEUE_FLUSH);
				return;
			}

			// else, could not perform calibration
			ttsStop();
			ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("calibrationfailed", 
					mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0")), TextToSpeech.QUEUE_FLUSH);
		}
	};
    
    public boolean swipeRight() {
    	return handleBackspace();
    }
    
    public boolean swipeLeft() {
    	return handleBlankspace();
    }

    public boolean swipeDown() {
        return handleClose();
    }

    public boolean swipeUp() {
    	//if(IS_EARCON)
    		//SoundManager.playSound(getApplicationContext(), SoundManager.mClick);
    	return false;
    }

    /********
     * HELPER FUNCTIONS (communication between IME and Editor)
     ********/
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
    	InputConnection ic = getCurrentInputConnection();
    	if(ic!=null)
    	{
	        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
	        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    	}
    }

    /**
     * Helper to send a backspace to the editor.
     */
    private boolean handleBackspace() {
    	if(!mSharedPrefs.getBoolean(getResources().getString(R.string.prefBackspace), true)) return false;
    	
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSound), true))
    		SoundManager.playSound(getApplicationContext(), SoundManager.mSwipe);
    	
    	if(mIsNumberState)
    	{
    		// deactivate number state
    		mIsNumberState = false;
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefVibration), true))
	    	{
	    		mVibrator.vibrate(VIBRATION_LENGHT * 10);
	    	}	
    	}
    	else
    	{
    		// delete char
	    	final int length = mComposing.length();
	    	if(length > 0)
	    	{
		    	char c = mComposing.charAt(length - 1);
		    	
		    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechDown), true) || 
		    			mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechUp), false))
		    	{
		    		ttsStop();
		    		ttsSpeak(LanguageManager.getInstance(getApplicationContext()).getMessage("deletedchar", 
		    				mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0")) + 
		    				" " + getLanguageCharacter(c), TextToSpeech.QUEUE_FLUSH);
		    	}
		    	
		    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefVibration), true))
		    	{
		    		mVibrator.vibrate(VIBRATION_LENGHT * 10);
		    	}	
		    	
		    	mComposing.delete(length - 1, length);
		    	mWordCorrectionComposing = deleteUntilValidChar(mWordCorrectionComposing);
	    	}
	    	else
	    	{
	    		mWordCorrectionComposing.setLength(0);
	    	}
	    	
	    	keyDownUp(KeyEvent.KEYCODE_DEL);
    	}
    	
    	// log char action
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
    		LogManager.getInstance().logCharEvent("†", new Date());
    	
    	return true;
    }
    
    /**
     * Helper to send a character to the editor.
     */
	@Override 
    public void sendKeyChar(char c)
    {    	
    	// update number state when sending a number
    	if(mIsNumberState) mIsNumberState = false;
    	
    	if(c == Character.LETTER_NUMBER)
    	{
    		// number sign
    		mIsNumberState = true;
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechUp), false))
    		{
    			ttsStop();
    			ttsSpeak(c, TextToSpeech.QUEUE_FLUSH);
    		}
    		
    		return;
    	}
    	else if(c == Character.UNASSIGNED)
    	{
    		c = 'ø';
    		return;
    		// read character
    		/*if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSound), true))
    			SoundManager.playSound(getApplicationContext(), SoundManager.mError);
    		
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefVibration), true))
    		{
    			long[] pattern = {0, 
    					VIBRATION_LENGHT, VIBRATION_LENGHT,
    					VIBRATION_LENGHT, VIBRATION_LENGHT,
    					VIBRATION_LENGHT, VIBRATION_LENGHT,
    					VIBRATION_LENGHT, VIBRATION_LENGHT,
    					VIBRATION_LENGHT};
    			mVibrator.vibrate(pattern, -1);
    		}
    		*/
    	}
    	else if(c != ' ' && c != '.' && c != ',' && c != '?')
    	{
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSound), true))
        		SoundManager.playSound(getApplicationContext(), SoundManager.mClick);
    		
    		// read character
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechUp), false))
    		{
    			ttsStop();
    			ttsSpeak(c, TextToSpeech.QUEUE_FLUSH);
    		}
    		
    		mComposing.append(c);
    		mWordCorrectionComposing.append(c);
        	getCurrentInputConnection().commitText(String.valueOf(c), 1);
    		
    	}
    	else
    	{    		
    		String word = "";
    		
    		// auto-correct
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefAutoCorrect), false))
    		{
    			word = getLastWord(mWordCorrectionComposing);
    			
    			if(word.length() > 0)
    			{
	    			mSuggestions = BrailleWordCorrection.getSharedInstance().getSuggestions(word, 5);
	    			if(mSuggestions.length > 0 && mSuggestions[0] != null && !word.equalsIgnoreCase(mSuggestions[0]))
	    			{
	    				// update view
	    	    		reDrawCentrois();
	    	    		
	    	    		// update internal text
	    	    		String realWord = getLastWord(mComposing);
	    	    		mComposing.delete(mComposing.length() - realWord.length(), mComposing.length());
	    	    		mComposing.append(mSuggestions[0]);
	    	    		mWordCorrectionComposing.delete(mWordCorrectionComposing.length() - word.length(), mWordCorrectionComposing.length());
	    	    		mWordCorrectionComposing.append(mSuggestions[0]);
	    	    		
	    	    		// update textview
	    	    		getCurrentInputConnection().finishComposingText();
	    	    		getCurrentInputConnection().deleteSurroundingText(realWord.length(), 0);
	    	    		getCurrentInputConnection().setComposingText(mSuggestions[0].toLowerCase(Locale.getDefault()), 1);
	    	    		getCurrentInputConnection().finishComposingText();
	    	    		word = mSuggestions[0];
	    			}
    			}
    			else mSuggestions = null;
    		}
    		else
    		{
    			word = getLastWord(mComposing);
    		}
    		
    		// read word
    		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSpeechWord), true))
    		{
    			ttsStop();
    			ttsSpeak(word, TextToSpeech.QUEUE_FLUSH);
    		}
    		
    		mComposing.append(c);
    		mWordCorrectionComposing.append(c);
			getCurrentInputConnection().commitText(String.valueOf(c), 1);
			
			// if it is a word separator then add blank space
			if(c != ' ')
    		{
    			mComposing.append(' ');
        		mWordCorrectionComposing.append(' ');
        		getCurrentInputConnection().commitText(String.valueOf(' '), 1);
    		}
    	}    	
    }
    
    /**
     * Helper to send a string to the editor.
     */
    public void sendKeyString(String s)
    {
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSound), true))
    		SoundManager.playSound(getApplicationContext(), SoundManager.mClick);
    	
    	mComposing.append(s);
    	mWordCorrectionComposing.append(s);
    	for(int i=0; i < s.length(); i++)
    	{
    		super.sendKeyChar(s.charAt(i));
    	}
    }
    
    /**
     * Helper to get last written word
     */
    private String getLastWord(StringBuilder message)
    {
    	if(message.length() == 0) return "";
    	
    	String ret = "";
    	int i = message.length() - 1;
    	while(i >= 0 && message.charAt(i) != ' ') { i--; }
    	if(i < 0) i = 0; else i++;
    	ret = message.substring(i, message.length());

    	return ret;
    } 
    
    /**
     * Helper to delete characters until a valid one 
     */
    private StringBuilder deleteUntilValidChar(StringBuilder message)
    {
    	while(message.length() > 0 && !isValidChar(message.charAt(message.length() - 1)))
    	{
    		message.delete(message.length() - 1, message.length());
    	}
    	if(message.length() > 0)
    		message.delete(message.length() - 1, message.length());
    	
    	return message;
    }
    
    /**
     * Helper to close the keyboard.
     */
    private boolean handleClose() {
    	if(!mSharedPrefs.getBoolean(getResources().getString(R.string.prefClose), true)) return false;
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSound), true))
    		SoundManager.playSound(getApplicationContext(), SoundManager.mClose);
        requestHideSelf(0);
        return true;
    }
    
    /**
     * Helper to send a blank space to the editor.
     */
    private boolean handleBlankspace()
    {    	
    	// is word separator
		if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefSound), true))
    		SoundManager.playSound(getApplicationContext(), SoundManager.mSwipe);
		
    	sendKeyChar(' ');
    	
    	// log char action
    	if(mSharedPrefs.getBoolean(getResources().getString(R.string.prefLogging), true)) 
    		LogManager.getInstance().logCharEvent(" ", new Date());
    	
    	return true;
    }
	
    /**
     * Helper to get log file path
     */
    public String getIMELogPath()
    {
    	return LogManager.getInstance().getFilePath();
    }
    
    /**
     * Helper to get log file path
     */
    public String getIMELogName()
    {
    	return LogManager.getInstance().getFileName();
    }
    
    /*********
	 * TEXT TO SPEECH
	 *********/
    
    /**
     * OnInitLister implementation for TTS
     */
	@Override
	public void onInit(int status) {
		if(mttsloaded || mTTS == null) return;
    	mttsloaded = true;
    	
    	if(status == TextToSpeech.SUCCESS)
    	{	
    		mTTS.setLanguage(Locale.getDefault());
    	}
    	else //ERROR
    	{
    		//tts.playEarcon("error", TextToSpeech.QUEUE_FLUSH, null);
    		Toast.makeText(this, "Error: TTS not avaliable. Check your device settings.", Toast.LENGTH_LONG).show();
    	}	
	}
	
	private void ttsSpeak(char character, int queuemode)
	{
		if(mTTS != null)
		{
			String message = getLanguageCharacter(character);
			mTTS.speak(message, queuemode, null);
		}
	}
	
	private void ttsSpeak(String message, int queuemode)
	{
		if(mTTS != null)
		{			
			mTTS.speak(message, queuemode, null);
		}
	}
	
	private void ttsStop()
	{
		if(mTTS != null && mTTS.isSpeaking())
		{
			mTTS.stop();
		}
	}
	
	private String getLanguageCharacter(char character)
	{
		if(character == 'ø') 
			return LanguageManager.getInstance(getApplicationContext()).getMessage("character", 
					mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0"));
	
		String message = String.valueOf(character);
		String lang = mSharedPrefs.getString(getResources().getString(R.string.prefLanguage), "0");
		
		if(lang.equalsIgnoreCase("1"))
		{
			// portuguese
			if(character == 'a') message = "á";
			else if(character == 'e') message = "é";
			else if(character == 'b') message = "bê.";
			else if(character == 'd') message = "dê.";
			else if(character == '.') message = "ponto";
			else if(character == ',') message = "vírgula";
			else if(character == '?') message = "interrogação";
			else if(character == Character.LETTER_NUMBER) message = "número";
			
			if(message.length() == 1) message += ".";
		}
		else if(lang.equalsIgnoreCase("0"))
		{
			// english
			if(character == '.') message = "period";
			else if(character == ',') message = "comma";
			else if(character == '?') message = "question mark";
			else if(character == Character.LETTER_NUMBER) message = "number";
		}
		
		return message;
	}
		
	// handle preference changes
	private class PreferenceChangeListener implements OnSharedPreferenceChangeListener {

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) 
		{
			if(key.equalsIgnoreCase(getResources().getString(R.string.prefLanguage)))
			{
				// language changed
				if(!BrailleWordCorrection.getSharedInstance().Language.equalsIgnoreCase(
						sharedPreferences.getString(getResources().getString(R.string.prefLanguage), "0")))
				{
					// language is different from the one loaded
					loadWordCorrection();
				}
			}
			else if(key.equalsIgnoreCase(getResources().getString(R.string.prefAutoCorrect)))
			{
				// auto-correction changed
				loadWordCorrection();
			}
		}
		
	}
}
