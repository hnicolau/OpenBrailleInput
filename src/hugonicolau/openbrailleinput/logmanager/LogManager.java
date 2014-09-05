package hugonicolau.openbrailleinput.logmanager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import android.graphics.Point;
import android.os.Environment;
import android.text.format.Time;
import android.util.Log;

public class LogManager {

	private static LogManager mInstance = null;
	
	private final String SESSION = "session";
	private final String DOWN = "regDown";
	private final String UP = "regUp";
	private final String MOVE = "regMove";
	private final String CHAR = "regChar";
	private final String SWIPE = "regSwipe";
	private final String CENTROID_UPDATE = "regCentroidUpdate";
	private final String CENTROID_CALIBRATION = "regCentroidCalibration";
	private final String INPUT_STREAM = "inputstream";
	private final String TIME = "time";
	private final String X = "x";
	private final String Y = "y";
	private final String ID = "id";
	private final String DISTANCE = "distance";
	private final String VELOCITY = "velocity";
	private final String DIRECTION = "direction";
	private final String BLOB = "blob";
	private final String ID1 = "id1";
	private final String ID2 = "id2";
	private final String ID3 = "id3";
	private final String ID4 = "id4";
	private final String ID5 = "id5";
	private final String ID6 = "id6";
	
	private String mFileName;
	private String mName = "";
	private Document mDoc;
	private Element mSession;
	private boolean mSessionStarted = false;
	
	private StringBuilder mInputStream;
	
	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss:SSS", Locale.UK);
	
	public static LogManager getInstance()
	{
		if(mInstance == null)
			mInstance = new LogManager();
		return mInstance;
	}
	
	protected LogManager() {}
	
	public void startSession()
	{	
		if(mSessionStarted) return;
		
		// dir
		File dir = new File(Environment.getExternalStorageDirectory() + "/Android/data/android.openbrailleinput.logs");
		dir.mkdirs();
		
		File file = null;
		do
		{
			// generate unique file name
			Time t = new Time();
			t.setToNow();
			mName =  t.toMillis(false) + ".xml";
			
			mFileName = dir + "/" + mName;
			file = new File(mFileName);
		}
		while(file.exists());
		
		// initialize input stream
		mInputStream = new StringBuilder();
		mInputStream.setLength(0);
		
		try
		{     
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			// create doc element
			mDoc = db.newDocument();
			
			// create session element and attributes
			mSession = mDoc.createElement(SESSION);
			mSession.setAttribute(TIME, dateFormat.format(new java.util.Date()).toString());
			mSession.setAttribute(INPUT_STREAM, mInputStream.toString());
			mDoc.appendChild(mSession); 
			
			mSessionStarted = true;
			
			Log.v("brailletouch", "log session started");
		 }    
		 catch(Exception e)
		 {
			 Log.v("brailletouch", "ERROR: error in starting log session");
			 e.printStackTrace();
		 }
	}
	
	public String getFileName()
	{
		return mName;
	}
	
	public String getFilePath()
	{
		return mFileName;
	}
	
	public String getInputStream()
	{
		return mInputStream.toString();
	}
	
	public void logDownEvent(int id, float x, float y, float blob, Date d)
	{
		if(!mSessionStarted) return;
		// create down element
		Element down = mDoc.createElement(DOWN);
		// set id
		down.setAttribute(ID, String.valueOf(id));
		// set x position
		down.setAttribute(X, String.valueOf(x));
		// set y position
		down.setAttribute(Y, String.valueOf(y));
		// set blob size
		down.setAttribute(BLOB, String.valueOf(blob));
		// set time
		down.setAttribute(TIME, dateFormat.format(d));
		
		mSession.appendChild(down);
	}
	
	public void logMoveEvent(int id, float x, float y, float blob, Date d)
	{
		if(!mSessionStarted) return;
		// create move element
		Element move = mDoc.createElement(MOVE);
		// set id
		move.setAttribute(ID, String.valueOf(id));
		// set x position
		move.setAttribute(X, String.valueOf(x));
		// set y position
		move.setAttribute(Y, String.valueOf(y));
		// set blob size
		move.setAttribute(BLOB, String.valueOf(blob));
		// set time
		move.setAttribute(TIME, dateFormat.format(d));
		
		mSession.appendChild(move);
	}
	
	public void logUpEvent(int id, float x, float y, float blob, Date d)
	{
		if(!mSessionStarted) return;
		// create up element
		Element up = mDoc.createElement(UP);
		// set id
		up.setAttribute(ID, String.valueOf(id));
		// set x position
		up.setAttribute(X, String.valueOf(x));
		// set y position
		up.setAttribute(Y, String.valueOf(y));
		// set blob size
		up.setAttribute(BLOB, String.valueOf(blob));
		// set time
		up.setAttribute(TIME, dateFormat.format(d));
		
		mSession.appendChild(up);
	}
	
	public void logCharEvent(String character, Date d, int id1, int id2, int id3, int id4, int id5, int id6)
	{
		if(!mSessionStarted) return;
		// create char element
		Element c = mDoc.createElement(CHAR);
		c.setTextContent(character.toString());
		
		// set finger 1 id
		c.setAttribute(ID1, String.valueOf(id1));
		// set finger 2 id
		c.setAttribute(ID2, String.valueOf(id2));
		// set finger 3 id
		c.setAttribute(ID3, String.valueOf(id3));
		// set finger 4 id
		c.setAttribute(ID4, String.valueOf(id4));
		// set finger 5 id
		c.setAttribute(ID5, String.valueOf(id5));
		// set finger 6 id
		c.setAttribute(ID6, String.valueOf(id6));
		
		// set time
		c.setAttribute(TIME, dateFormat.format(d));
		
		mSession.appendChild(c);
		
		// add character to input stream
		mInputStream.append(character);
	}
	
	public void logCharEvent(String character, Date d)
	{
		if(!mSessionStarted) return;
		// create char element
		Element c = mDoc.createElement(CHAR);
		c.setTextContent(character.toString());
		
		// set time
		c.setAttribute(TIME, dateFormat.format(d));
		
		mSession.appendChild(c);
		
		// add character to input stream
		mInputStream.append(character);
	}
	
	public void logSwipe(float distance, float velocity, Date d, String direction)
	{
		if(!mSessionStarted) return;
		// create up element
		Element swipe = mDoc.createElement(SWIPE);
		// set direction
		swipe.setAttribute(DIRECTION, direction);
		// set distance
		swipe.setAttribute(DISTANCE, String.valueOf(distance));
		// set velocity
		swipe.setAttribute(VELOCITY, String.valueOf(velocity));
		// set time
		swipe.setAttribute(TIME, dateFormat.format(d));
		
		mSession.appendChild(swipe);
	}
	
	public void logCentroidUpdateEvent(Point[] centroids, Date d)
	{	
		if(!mSessionStarted) return;
		for(int i = 0; i < centroids.length; i++)
		{
			//centroid
			Element centroid = mDoc.createElement(CENTROID_UPDATE);
			centroid.setAttribute(ID, String.valueOf(i + 1));
			// set position
			centroid.setAttribute(X, String.valueOf(centroids[i].x));
			centroid.setAttribute(Y, String.valueOf(centroids[i].y));
			// set time
			centroid.setAttribute(TIME, dateFormat.format(d));
			// append
			mSession.appendChild(centroid);
		}
	}
	
	public void logCentroidCalibrationEvent(Point[] centroids, Date d)
	{	
		if(!mSessionStarted) return;
		for(int i = 0; i < centroids.length; i++)
		{
			//centroid
			Element centroid = mDoc.createElement(CENTROID_CALIBRATION);
			centroid.setAttribute(ID, String.valueOf(i + 1));
			// set position
			centroid.setAttribute(X, String.valueOf(centroids[i].x));
			centroid.setAttribute(Y, String.valueOf(centroids[i].y));
			// set time
			centroid.setAttribute(TIME, dateFormat.format(d));
			// append
			mSession.appendChild(centroid);
		}
	}
	
	public String endSession()
	{	
		if(!mSessionStarted) return "";
		try
		{
			mSession.setAttribute(INPUT_STREAM, mInputStream.toString());
			
			DOMSource source = new DOMSource(mDoc);
			
			File file = new File(mFileName);
	        Result result = new StreamResult(file);

	        // Write the DOM document to the file
	        Transformer xformer = TransformerFactory.newInstance().newTransformer();
	        xformer.transform(source, result);
	        mSessionStarted = false;
	        Log.v("brailletouch", "log ended");
	        return mName;
	        
		}catch(Exception e)
		{
			Log.v("brailletouch", "ERROR: error in endind log session");
			e.printStackTrace();
			return "";
		}
	}
}
