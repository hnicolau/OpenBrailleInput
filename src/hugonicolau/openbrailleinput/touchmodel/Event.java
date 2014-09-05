package hugonicolau.openbrailleinput.touchmodel;

import android.graphics.Point;

public class Event {
	
	public enum EVENT_TYPE { DOWN, MOVE, UP };
	protected EVENT_TYPE mEvent;
	protected Point mPosition;
	protected long mTime;
	
	public Event(EVENT_TYPE eventType, Point p, long t)
	{
		mEvent = eventType;
		mPosition = p;
		mTime = t;
	}

	public EVENT_TYPE getEvent() {
		return mEvent;
	}

	public void setEvent(EVENT_TYPE event) {
		this.mEvent = event;
	}

	public Point getPosition() {
		return mPosition;
	}

	public void setPosition(Point position) {
		this.mPosition = position;
	}

	public long getTime() {
		return mTime;
	}

	public void setTime(long time) {
		this.mTime = time;
	}
	
	
}
