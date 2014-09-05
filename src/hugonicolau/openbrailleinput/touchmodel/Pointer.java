package hugonicolau.openbrailleinput.touchmodel;

import hugonicolau.openbrailleinput.touchmodel.Event.EVENT_TYPE;

import java.util.ArrayList;

import android.graphics.Point;

// this class describes a touch pointer
public class Pointer {
	
	protected int mID = -1;
	protected ArrayList<Event> mEvents = null;
	
	public Pointer()
	{
		mEvents = new ArrayList<Event>();
	}
	
	public Pointer(int id, Event downEvent)
	{
		mID = id;
		mEvents = new ArrayList<Event>();
		mEvents.add(downEvent);
	}
	
	public void addEvent(Event event)
	{
		if(event != null)
			mEvents.add(event);
	}
	
	public int getID()
	{
		return mID;
	}
	
	public Point getDownPosition()
	{
		if(mEvents.size() > 0)
			return mEvents.get(0).getPosition();
		
		return null;
	}
	
	public Long getDownTime()
	{
		if(mEvents.size() > 0)
			return mEvents.get(0).getTime();
		
		return null;
	}
	
	public Point getUpPosition()
	{
		if(mEvents.size() > 1 && mEvents.get(mEvents.size() - 1).getEvent() == EVENT_TYPE.UP)
			return mEvents.get(mEvents.size() - 1).getPosition();
		
		return null;
	}
	
	public Long getUpTime()
	{
		if(mEvents.size() > 1 && mEvents.get(mEvents.size() - 1).getEvent() == EVENT_TYPE.UP)
			return mEvents.get(mEvents.size() - 1).getTime();
		
		return null;
	}
	
	public Point getLastKnownPosition()
	{
		if(mEvents.size() > 0)
			return mEvents.get(mEvents.size() - 1).getPosition();
		return null;
	}
	
	public Long getLastKnownTime()
	{
		if(mEvents.size() > 0)
			return mEvents.get(mEvents.size() - 1).getTime();
		return null;
	}

}
