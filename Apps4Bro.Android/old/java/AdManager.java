package net.runserver.apps4bro;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class AdManager
{
	private final static String TAG = "AdManager";
    private final static String App4BroTag = "app4bro";

	private Context m_context;

	private final Map<String, String> m_keys;
	private final Map<String, AdNetworkHandler> m_adNetworks;
	
	private AdWrapper[] m_adWrappers;
	private int m_currentWrapper;
	private boolean m_adShown;
    private String m_appId;

	private static AtomicBoolean s_asyncTaskStarted = new AtomicBoolean(false);

	public boolean isAdShown()
	{
		return m_adShown;
	}
	
	public Context getContext()
	{
		return m_context;
	}
	
	public AdManager(String[] keys)
	{
		m_keys = new HashMap<String, String>();

		for (int i = 0; i < keys.length / 2; i++)
			m_keys.put(keys[i * 2].toLowerCase(), keys[i * 2 + 1]);
		
        m_adNetworks = new HashMap<String, AdNetworkHandler>();
        
		m_appId = m_keys.get(App4BroTag);
        if (m_appId == null)
        {
        	Log.e(TAG, "No App4Bro key specified for AdManager!");
        	return;
        }

        ////////
        registerAdNetwork(new AdMobNetwork(this));
        registerAdNetwork(new AppodealNetwork(this));
       // registerAdNetwork(new AppodealVideoNetwork(this));
        registerAdNetwork(new AppLovinNetwork(this));
        registerAdNetwork(new AdCashNetwork(this));
	}

	public boolean isAdTimeoutCompleted(Context context)
	{
		long now = new Date().getTime();
		long next = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).getLong("next_ad", -1);

		if (next == -1)
		{
			next = now + 1; // next launch
			Editor editor = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).edit();
			editor.putLong("next_ad", next);
			editor.commit();
		}
		return next <= now;
	}
	
	private void setAdTimeout()
	{
		Editor editor = m_context.getSharedPreferences(m_context.getPackageName() + "_preferences", Context.MODE_PRIVATE).edit();
		editor.putLong("next_ad", new Date().getTime() + 180000); // 3 minutes
		editor.commit();	
	}
	
    public void registerAdNetwork(AdNetworkHandler handler)
    {
        m_adNetworks.put(handler.getNetwork().toLowerCase(), handler);
    }

	
	public void init(Context context)
	{
		m_context = context;
		
		/*
		
		AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params)
			{
				initAsync();
				return null;
			}
			
			@Override
			protected void onPostExecute(Void result)
			{
				nextAd();
			}
		};
		task.execute();*/
		
		if (s_asyncTaskStarted.get() == false)
		{
			Thread thread = new Thread(new Runnable(){
				  @Override
				  public void run(){
				    initAsync();
				  }
				});
				thread.start();
			}
	}
	
	private void initAsync()
	{
		s_asyncTaskStarted.set(true);

		try
		{
			URLConnection conn = new URL("http://runserver.net/app4bro/ad.php?app=" + m_appId).openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			
			String data = loadStreamText(conn.getInputStream());
			if (data != null && data.length() > 0)
				parseAdData(data);
			else
				Log.w(TAG, "Got empty response");
			
		} catch (Exception ex)
		{
			Log.w(TAG, "Network error: " + ex);
			ex.printStackTrace();
		}

		if (m_adWrappers == null)
		{
            Log.d(TAG, "Registering fallback ads");

            StringBuilder builder = new StringBuilder();
            
            int count = 0;
            for(Map.Entry<String,String> pair : m_keys.entrySet())
            {
                count++;
                builder.append(pair.getKey());
                builder.append(':');
                builder.append(pair.getKey());
                builder.append("def");
                builder.append(count);
                builder.append('|');
                builder.append(pair.getValue());
                builder.append('|');
            }
            
            parseAdData(builder.toString());
			
			if (m_adWrappers == null || m_adWrappers.length == 0)
			{
				Log.w(TAG, "No ad networks registered!");
				return;
			}
		}

		m_currentWrapper = -1;
		
		((Activity)m_context).runOnUiThread(new Runnable()
		{
			  @Override
			  public void run(){
			    showAd();
			  }
			});
				
		
		s_asyncTaskStarted.set(false);
	}
	
	private void parseAdData(String data)
	{
		try
		{
			String[] adData = data.split("\\|");
	
			if (adData.length > 1)
			{
				List<AdWrapper> wrappers = new ArrayList<AdWrapper>(adData.length / 2);
	
				for (int i = 0; i < adData.length; i += 2)
				{
					String network = adData[i].toLowerCase();
	                String networkId;
	
	                if (network.contains(":"))
	                {
	                    String [] nsplit = network.split(":");
	                    network = nsplit[0];
	                    networkId = nsplit[1];
	                }
	                else
	                    networkId = network + " " + (wrappers.size() + 1);
	
	                AdNetworkHandler networkHandler = m_adNetworks.get(network);
	
					if (networkHandler != null)
					{
						wrappers.add(new AdWrapper(networkHandler, adData[i + 1], networkId));
						Log.d(TAG, "Registered ad network " + network + "[" + adData[i+1] + "]");
					} else
						Log.w(TAG, "Failed to register ad network " + network);
				}
	
				if (wrappers.size() == 0)
				{
					Log.w(TAG, "No ad networks registered!");
				} else
				{
					m_adWrappers = wrappers.toArray(new AdWrapper[wrappers.size()]);
					Log.d(TAG, "Registered " + m_adWrappers.length + " ad networks");
				}
			}
		}
		catch(Exception ex)
		{
			Log.e(TAG, "Error parsing data string: " + data);
			ex.printStackTrace();
		}
	}
	
	public void showAd()
	{
        if (m_adNetworks == null)
        {
            Log.w(TAG, "showAd called before initialization!");
            return;
        }
        
		m_currentWrapper++;
		
		Log.w(TAG, "Going to use wrapper #" + m_currentWrapper);
		
		if (m_currentWrapper >= m_adWrappers.length)
		{
			Log.w(TAG, "No ad networks left for showAd()!");
			m_currentWrapper = -1;
			return;
		}

		AdWrapper wrapper = null;
		try
		{
            wrapper = m_adWrappers[m_currentWrapper];
            wrapper.CallCount++;
            
            wrapper.getNetworkHandler().setId(wrapper.getId());
			
			if (!wrapper.getNetworkHandler().show(wrapper))
				adError(wrapper, "Wrapper returned false");
			
		} catch (Exception ex)
		{
			ex.printStackTrace();
            adError(wrapper, ex.getMessage());			
		}
	}
	
    public void hideAd()
    {
        if (m_adNetworks == null)
        {
            Log.w(TAG, "hideAd called before initialization!");
            return;
        }
        
        m_adShown = false;
        
        AdWrapper wrapper = null;
        try
        {
            wrapper = m_adWrappers[m_currentWrapper];
            wrapper.getNetworkHandler().hide();
            Log.d(TAG, "Called hide ad for network "  + wrapper.getNetworkHandler().getNetwork());
        }
        catch (Exception ex)
        {
        	ex.printStackTrace();
        }
    }
	
    /*internal*/ void adError(Object data, String error)
    {
        String networkName = "Unknown network";
        try
        {
            AdWrapper wrapper = (AdWrapper)data;

            if (wrapper != null)
            {
                networkName = wrapper.getName();
                wrapper.LastError = error;
                wrapper.FailCount++;
                Log.w(TAG, String.format("Failed to display ad from %s [%d/%d/%d/%d], error %s", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error));
                //m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} [{1}/{2}/{3}/{4}] '{5}'", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount, error));
            }
            else
            {
                Log.w(TAG, "Failed to display ad from " + networkName + ", error " + error);
                //m_reportManager.ReportEvent("AD_ERROR", string.Format("{0} '{1}'", networkName, error));
            }

        }
        finally
        {
            //if (m_context.OnFailedAd(this, networkName, error))
        	if (m_currentWrapper == - 1 || m_adWrappers[m_currentWrapper] == data)
                showAd();
        }
    }
	
	/*internal*/ void adLoaded(Object data, Object view)
	{
        String networkName = "Unknown network";

        try
        {
        	setAdTimeout();
		
	        AdWrapper wrapper = (AdWrapper)data;
	        
	        m_adShown = true;
	
	        if (wrapper != null)
	        {
	            networkName = wrapper.getName();
	            wrapper.SuccessCount++;
	            Log.w(TAG, String.format("Displayed ad from %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
//	            m_reportManager.ReportEvent("AD_SHOW", string.Format("{0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
	        }
	        else
	        {
	            Log.w(TAG, "Displayed ad from " + networkName);
//	            m_reportManager.ReportEvent("AD_SHOW", "");
	        }
	        
	    }
	    finally
	    {
	        //m_context.OnShownAd(this, networkName, view);
	    }
	}

	/*internal*/ void adClick(Object data)
	{
        String networkName = "Unknown network";

        try
        {
	        AdWrapper wrapper = (AdWrapper)data;
	        
	        m_adShown = false;
	
	        if (wrapper != null)
	        {
	            networkName = wrapper.getName();
	            wrapper.ClickCount++;
	            Log.w(TAG, String.format("Clicked ad from %s [%d/%d/%d/%d]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
//                m_reportManager.ReportEvent("AD_CLICK", string.Format("{0} [{1}/{2}/{3}/{4}]", networkName, wrapper.CallCount, wrapper.SuccessCount, wrapper.FailCount, wrapper.ClickCount));
	        }
	        else
	        {
	            Log.w(TAG, "Displayed ad from " + networkName);
//	            m_reportManager.ReportEvent("AD_SHOW", "");
	        }
	        
	    }
	    finally
	    {
//            m_context.OnClickedAd(this, networkName);
	    }
	}
	
	private static String loadStreamText(InputStream stream)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 0x8000);

			StringBuilder result = new StringBuilder();
			String line = null;

			while ((line = reader.readLine()) != null)
			{
				result.append(line);
				// Log.d("Test", "Line: " + line);
			}

			return result.toString();
		} catch (Exception ex)
		{
			ex.printStackTrace();

		}
		return "";
	}
	
}
