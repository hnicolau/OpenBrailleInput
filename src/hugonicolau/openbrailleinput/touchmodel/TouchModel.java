package hugonicolau.openbrailleinput.touchmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.graphics.Point;
import android.view.MotionEvent;

public class TouchModel {
	
	// current touch pointers
	protected HashMap<String, Pointer> mPointers = null;
	
	// current finger centroids
	protected Point[] mCentroids = null; // index corresponds to finger
	
	// thresholds
	final protected int UP_THRESHOLD = 200; //ms
	
	private int mWidth = 0;
	
	public TouchModel(int width)
	{
		mPointers = new HashMap<String, Pointer>();
		mCentroids = new Point[6];
		mWidth = width;
	}
	
	// update pointer state
	public void update(MotionEvent event)
	{
		// get finger count, event index and event id
		int index = getIndex(event);
		int id = event.getPointerId(index);
				
		switch(event.getAction() & MotionEvent.ACTION_MASK)
		{
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
			{
				mPointers.put(String.valueOf(id), new Pointer(id, 
						new Event(Event.EVENT_TYPE.DOWN, new Point((int)event.getX(index), (int)event.getY(index)), 
								event.getDownTime())));
				break;
			}
			
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			{
				Pointer p = mPointers.get(String.valueOf(id));
				p.addEvent(new Event(Event.EVENT_TYPE.UP, new Point((int)event.getX(index), (int)event.getY(index)), 
						event.getEventTime()));
				break;
			}
			
			case MotionEvent.ACTION_MOVE:
			{
				for(int size = event.getPointerCount(), i = 0 ; i < size; i++)
				{
					int pid = event.getPointerId(i);
					Pointer p = mPointers.get(String.valueOf(pid));
					p.addEvent(new Event(Event.EVENT_TYPE.MOVE, new Point((int)event.getX(i), (int)event.getY(i)), 
						event.getEventTime()));
				}
				break;
			}
		}
	}
	
	// reset pointer state
	public void reset()
	{
		mPointers.clear();
	}
	
	public HashMap<String, Pointer> getFingerPointers()
	{
		return mPointers;
	}
	
	public Pointer[] getFingersDown()
	{
		Pointer downPointers[] = new Pointer [mPointers.size()];
		int count = 0;
		
		Iterator<String> myVeryOwnIterator = mPointers.keySet().iterator();
		while(myVeryOwnIterator.hasNext()) {
		    String key=(String)myVeryOwnIterator.next();
		    Pointer value=(Pointer)mPointers.get(key);
		    if(value.getUpTime() == null)
		    	downPointers[count++] = value;
		}
		
		return fingerRecognition(downPointers, count);
	}
	
	// get latest finger up's. usually called in order to recognize braille character
	public Pointer[] getFingersUp(long upEventTime)
	{
		
		// FILTER - check last up events - filter old events
		Pointer upPointers[] = new Pointer [mPointers.size()];
		int count = 0;
							
		Iterator<String> myVeryOwnIterator = mPointers.keySet().iterator();
		while(myVeryOwnIterator.hasNext()) {
		    String key=(String)myVeryOwnIterator.next();
		    Pointer value=(Pointer)mPointers.get(key);
		 	
		    // check if finger is UP
		    Long fingerUpTime = value.getUpTime();
		    if(fingerUpTime != null && upEventTime - fingerUpTime <= UP_THRESHOLD)
		    {
		    	// this pointer should be considered
		    	upPointers[count++] = value;
		    }
		}
		
		// deal with finger recognition and sending char
		return fingerRecognition(upPointers, count);
	}
	
	/*
	 * GESTURE RECOGNITION
	 */
	
	// type of gesture
	public enum GESTURE_TYPE { NONE, LEFT, RIGHT, UP, DOWN };
	private final int MIN_DISTANCE = 70; // px
    //private final float MAX_TIME = (float) 1000; // milliseconds
	
	public GESTURE_TYPE checkGesture(Pointer[] upFingers)
	{
		// find what finger did the gesture
		int fingerIndex = -1;
		for(int i = 0; i < upFingers.length; i++)
		{
			if(upFingers[i] != null)
			{
				// only deal with one finger gesture
				if(fingerIndex != -1) return GESTURE_TYPE.NONE;
				fingerIndex = i;
			}
		}
		if(fingerIndex == -1) return GESTURE_TYPE.NONE;
		
		Point downPosition = upFingers[fingerIndex].getDownPosition();
		Point upPosition = upFingers[fingerIndex].getUpPosition();
		Long downTime = upFingers[fingerIndex].getDownTime();
		Long upTime = upFingers[fingerIndex].getUpTime();
		
		if(downPosition == null || upPosition == null || downTime == null || upTime == null) return GESTURE_TYPE.NONE;
		
		float distanceX = upPosition.x - downPosition.x;
		float distanceY = upPosition.y - downPosition.y;
		//long time = upTime - downTime;
		//Log.v(OpenBrailleInput.TAG, "Distance X[" + distanceX + "] Y[" + distanceY + "] TIME[" + time + "]");
		
		// if gesture time is higher than min time, then it isn't a gesture
		//if(time > MAX_TIME) return GESTURE_TYPE.NONE;
		
		if(Math.abs(distanceX) >= Math.abs(distanceY))
		{
			// horizontal gesture
			if(upPosition.x - downPosition.x > MIN_DISTANCE)
				return GESTURE_TYPE.RIGHT;
			else if(downPosition.x - upPosition.x > MIN_DISTANCE)
				return GESTURE_TYPE.LEFT;
		}
		else
		{
			// vertical gesture
			if(upPosition.y - downPosition.y > MIN_DISTANCE)
				return GESTURE_TYPE.DOWN;
			else if(downPosition.y - upPosition.y > MIN_DISTANCE)
				return GESTURE_TYPE.UP;
		}
		
		return GESTURE_TYPE.NONE;
	}
	
	/*
	 * FINGER RECOGNITION
	 */
	
	/**
     * 
     * Update centroids
     * update function:
     * New_C = Curr_C + K * M * Er
     * New_C: New centroid position
     * Curr_C: Current centroid position
     * K: Adaptation coefficient; effect of tracking 0 - 1 
     * M: Correlation coefficient matrix; degree to which the error of one finger affects other fingers of the same hand
     * Er: Error or distance to current centroid
     */
    double K = 0.1;
	double[][] M = {{1,0.4,0.4},{0.4,1,0.4},{0.4,0.4,1}};
	double[][] M2 = {{0.1,0.04,0.04},{0.04,0.1,0.04},{0.04,0.04,0.1}};
	
	public void updateFingerCentroids(Pointer[] fingers)
	{
		double[] errorX = new double[6];
    	double[] errorY = new double[6];
    	
    	// calculate error for each centroid
    	for(int i=0; i<6; i++)
    	{
    		if(fingers[i] != null)
    		{
    			errorX[i] = fingers[i].getLastKnownPosition().x - mCentroids[i].x;
    			errorY[i] = fingers[i].getLastKnownPosition().y - mCentroids[i].y;
    		}
    		else
    		{
    			errorX[i] = 0;
    			errorY[i] = 0;
    		}
    	}
    	
    	// update left hand
    	for(int i = 0; i < 3; i++) //line
    	{
    		for(int j = 0; j < 3; j++) // column
    		{
    			mCentroids[i + 3].x = mCentroids[i + 3].x + (int)(M2[i][j] * errorX[j + 3]);
    			mCentroids[i + 3].y = mCentroids[i + 3].y + (int)(M2[i][j] * errorY[j + 3]);
    		}
    	}
    	
    	// update right hand
    	for(int i = 0; i < 3; i++) //line
    	{
    		for(int j = 0; j < 3; j++) // column
    		{
    			mCentroids[i].x = mCentroids[i].x + (int)(M2[i][j] * errorX[j]);
    			mCentroids[i].y = mCentroids[i].y + (int)(M2[i][j] * errorY[j]);
    		}
    	}
	}
	
	private Pointer[] fingerRecognition(Pointer[] upPointers, int count)
    {
    	// calculate finger - touch mapping
		boolean upFingers[] = new boolean[6];
		for(int i=0; i < 6; i++) upFingers[i] = false;
		
		// divide by side
		List<Pointer> upPointersRight = new ArrayList<Pointer>();
		List<Pointer> upPointersLeft = new ArrayList<Pointer>();
		for(int i=0; i<count;i++){
			if(upPointers[i].getLastKnownPosition().x < mWidth / 2){
				//right
				upPointersRight.add(upPointers[i]);
			}
			else{
				//left
				upPointersLeft.add(upPointers[i]);
			}
		}
		
		// order from top to bottom 
		Collections.sort(upPointersRight, new VerticalComparator());
		Collections.sort(upPointersLeft, new VerticalComparator());
		
		Pointer[] fingers = new Pointer[6];
		for(int i = 0; i < 6; i++) fingers[i] = null;
		
		// assess pattern
		// left side
		// if three points, then done
		if(upPointersLeft.size() == 3) 
		{
			upFingers[0] = upFingers[1] = upFingers[2] = true;
			fingers[0] = upPointersLeft.get(0);
			fingers[1] = upPointersLeft.get(1);
			fingers[2] = upPointersLeft.get(2);
		}
		else if(upPointersLeft.size() > 0)
		{ 
			// else for each touch point get closest centroid from c to 3
			int f1 = getLeftFinger(upPointersLeft.get(0).getLastKnownPosition().x, upPointersLeft.get(0).getLastKnownPosition().y);
			upFingers[f1] = true;
			fingers[f1] = upPointersLeft.get(0);
			
			if(upPointersLeft.size() > 1)
			{
				int f2 = getLeftFinger(upPointersLeft.get(1).getLastKnownPosition().x, upPointersLeft.get(1).getLastKnownPosition().y);
				if(f1 == f2)
				{
					// two finger for the same centroid, but f2 is certainly on bottom
					if(f1 == 2) 
					{ 
						upFingers[1] = true;
						fingers[1] = upPointersLeft.get(0);
						fingers[2] = upPointersLeft.get(1);
					}
					else 
					{ 
						f2 = f2 + 1;
						upFingers[f2] = true;
						fingers[f2] = upPointersLeft.get(1);
					}
				}
				else
				{
					upFingers[f2] = true;
					fingers[f2] = upPointersLeft.get(1);
				}
			}
		}
			
		// right side
		// if three points, then done
		if(upPointersRight.size() == 3) 
		{
			upFingers[3] = upFingers[4] = upFingers[5] = true;
			fingers[3] = upPointersRight.get(0);
			fingers[4] = upPointersRight.get(1);
			fingers[5] = upPointersRight.get(2);
		}
		else if(upPointersRight.size() > 0)
		{ 
			// else for each touch point get closest centroid
			int f1 = getRightFinger(upPointersRight.get(0).getLastKnownPosition().x, upPointersRight.get(0).getLastKnownPosition().y);
			upFingers[f1] = true;
			fingers[f1] = upPointersRight.get(0);
				
			if(upPointersRight.size() > 1)
			{
				int f2 = getRightFinger(upPointersRight.get(1).getLastKnownPosition().x, upPointersRight.get(1).getLastKnownPosition().y);
				if(f1 == f2)
				{
					// two finger for the same centroid, but f2 is certainly on bottom
					if(f1 == 5) 
					{ 
						upFingers[4] = true;
						fingers[4] = upPointersRight.get(0);
						fingers[5] = upPointersRight.get(1);
					}
					else 
					{ 
						f2 = f2 + 1;
						upFingers[f2] = true;
						fingers[f2] = upPointersRight.get(1);
					}
				}
				else
				{
					upFingers[f2] = true;
					fingers[f2] = upPointersRight.get(1);
				}
			}	
		}
	    
	    return fingers;
    }
	
	public boolean areDifferences(Pointer[] last, Pointer[] current)
	{
		boolean ret = false;
		if(last == null || current == null || current.length < last.length) return ret;
		
		for(int i = 0; i < last.length; i++)
		{
			// check for differences in pointers state
			if((last[i] == null && current[i] != null) || (last[i] != null && current[i] == null))
			{
				return true;
			}
		}
		
		return ret;
	}
	
	 /**
     * Helper that gets the closest left hand finger to point(x,y)
     */
    private int getLeftFinger(float x, float y){
    	int finger = -1;
    	float minDistance = -1;
    	
    	finger = 0;
    	minDistance = getDistance(mCentroids[0].x, mCentroids[0].y, x, y);
    	
    	for(int i=1; i<3; i++)
    	{
    		float d = Math.abs(getDistance(mCentroids[i].x, mCentroids[i].y, x, y));
    		if(d < minDistance)
    		{
    			minDistance = d;
    			finger = i;
    		}
    	}
    	
    	return finger;
    }
    
    /**
     * Helper that gets the closest right hand finger to point(x,y)
     */
    private int getRightFinger(float x, float y){
    	int finger = -1;
    	float minDistance = -1;
    	
    	finger = 3;
    	minDistance = getDistance(mCentroids[3].x, mCentroids[3].y, x, y);
    	
    	for(int i=4; i<6; i++)
    	{
    		float d = Math.abs(getDistance(mCentroids[i].x, mCentroids[i].y, x, y));
    		if(d < minDistance)
    		{
    			minDistance = d;
    			finger = i;
    		}
    	}
    	
    	return finger;
    }
    
    @SuppressWarnings("unused")
	private int getFinger(float x, float y)
    {
    	int finger = 0;
    	float minDistance = getDistance(mCentroids[0].x, mCentroids[0].y, x, y);
    	
    	for(int i = 1; i < 6; i++)
    	{
    		float d = Math.abs(getDistance(mCentroids[i].x, mCentroids[i].y, x, y));
    		if(d < minDistance)
    		{
    			minDistance = d;
    			finger = i;
    		}
    	}
    	return finger;
    }
    
    /**
     * Get distance between two points
     */
    private float getDistance(float x1, float y1, float x2, float y2){
    	return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }
    
	/**
     * Get pointer index
     */
    private int getIndex(MotionEvent event) {
    	 
    	  int idx = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    	  return idx;
    }
    
    public Point[] getFingerCentroids()
    {
    	return mCentroids;
    }
    
    public void setFingerCentroids(Point[] pointer)
    {
    	mCentroids = pointer;
    }

    public void setWidth(int width)
    {
    	mWidth = width;
    }
}
