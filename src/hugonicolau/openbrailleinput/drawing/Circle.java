package hugonicolau.openbrailleinput.drawing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class Circle extends View{
	private float x;
    private float y;
    private int r;
    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    public Circle(Context context)
    {
    	super(context);
    }
    
    public Circle(Context context, float x, float y, int r) {
        super(context);
        mPaint.setColor(0xFFFFFFFF); 
        this.x = x;
        this.y = y;
        this.r = r;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(x, y, r, mPaint);
    }
}
