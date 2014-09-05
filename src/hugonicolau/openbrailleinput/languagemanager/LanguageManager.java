package hugonicolau.openbrailleinput.languagemanager;

import java.util.HashMap;

import android.content.Context;

public class LanguageManager {

	// messages
	protected HashMap<String, String> messagesEN;
	protected HashMap<String, String> messagesPT;
	
	private static LanguageManager mInstance = null;
	protected Context mContext = null;
	
	protected LanguageManager(Context c) 
	{
		mContext = c;
		
		// initialize messages
		messagesEN = new HashMap<String, String>();
		messagesPT = new HashMap<String, String>();
		
		messagesEN.put("calibrationstarted", "Calibration started.");
		messagesPT.put("calibrationstarted", "A iniciar calibração");
		
		messagesEN.put("calibrationended", "Finger calibration ended.");
		messagesPT.put("calibrationended", "Calibração terminada.");
		
		messagesEN.put("calibrationfailed", 
				"Calibration failed. Retry by placing your fingers on the screen in this order: 1, 2, 3, 4, 5, 6.");
		messagesPT.put("calibrationfailed", 
				"Erro de calibração. Tente novamente colocando os dedos no ecrã por esta ordem: 1, 2, 3, 4, 5, 6.");
		
		messagesEN.put("deletedchar", "Deleted");
		messagesPT.put("deletedchar", "Apagou");
		
		messagesEN.put("invalid", "Invalid");
		messagesPT.put("invalid", "Inválido");
		
		messagesEN.put("character", "Character");
		messagesPT.put("character", "Caracter");
	}
	
	public static LanguageManager getInstance(Context c)
	{
		if(mInstance == null)
			mInstance = new LanguageManager(c);
		return mInstance;
	}
	
	public String getMessage(String messageID, String language)
	{
		HashMap<String, String> messages;
		
		if(language.equalsIgnoreCase("0"))
			messages = messagesEN;
		else 
			messages = messagesPT;
		
		String message = messages.get(messageID);
		return message == null ? "" : message;
	}
	
}
