package net.runserver.apps4bro;

import android.app.Activity;
import android.util.Log;

import com.appodeal.ads.Appodeal;
import com.appodeal.ads.InterstitialCallbacks;
import com.appodeal.ads.VideoCallbacks;

class AppodealNetwork implements AdNetworkHandler, InterstitialCallbacks, VideoCallbacks
{
	private final static String TAG = "ApoDeal";
	
	private final AdManager m_manager;
	private String m_id;
	private boolean m_inited;
	private Object m_data;

	public String getNetwork()
	{
		return TAG;
	}

	public void setId(String id)
	{
		m_id = id;
	}

	public AppodealNetwork(AdManager manager)
	{
		m_manager = manager;
	}
	
	public boolean show(final Object data)
	{
		try
		{
			m_data = data;
			
			Activity context = (Activity)m_manager.getContext();
			
			if (!m_inited)
			{
				
	//			Appodeal.disableNetwork(context, "chartboost");
				
	//			Appodeal.disableNetwork(context, "applovin");
	//			Appodeal.disableNetwork(context, "unity_ads");
				Appodeal.disableNetwork(context, "admob");
				
				Appodeal.initialize(context, m_id, Appodeal.INTERSTITIAL | Appodeal.VIDEO);
			
				//Appodeal.setTesting(true);
				
				Appodeal.setInterstitialCallbacks(this);
				Appodeal.setVideoCallbacks(this);
				Appodeal.cache(context, Appodeal.INTERSTITIAL | Appodeal.VIDEO);
				m_inited = true;
			} else
			{
				if (Appodeal.isLoaded(Appodeal.VIDEO))
					onVideoLoaded();
				else
					if (Appodeal.isLoaded(Appodeal.INTERSTITIAL))
						onInterstitialLoaded(true);
					else
						return false;
			
			}

			return true;
		} catch (Exception ex)
		{
			ex.printStackTrace();
			return false;
		}
	}
	
	public void hide()
	{
	}
	
	@Override
	public void onInterstitialLoaded(boolean type)
	{
		if (m_manager.isAdShown())
		{
			Log.e(TAG, "Ad already showing!");
			return;
		}
		
		Appodeal.show((Activity)m_manager.getContext(), Appodeal.INTERSTITIAL);
	}

	@Override
	public void onInterstitialFailedToLoad()
	{
		m_manager.adError(m_data, "AppoDeal Ad failed to load");
	}

	@Override
	public void onInterstitialShown()
	{
		if (m_manager.isAdShown())
		{
			Log.e(TAG, "Ad already showing!");
			return;
		}
		
		m_manager.adLoaded(m_data, null);
	}

	@Override
	public void onInterstitialClicked()
	{
		m_manager.adClick(m_data);
	}

	@Override
	public void onInterstitialClosed()
	{
	}
	
	@Override
	public void onVideoClosed()
	{
	}

	@Override
	public void onVideoFailedToLoad()
	{
		m_manager.adError(m_data, "AppoDeal Video Ad failed to load");
	}

	@Override
	public void onVideoFinished()
	{
		
	}

	@Override
	public void onVideoLoaded()
	{
		if (m_manager.isAdShown())
		{
			Log.e(TAG, "Ad already showing!");
			return;
		}
		
		Appodeal.show((Activity)m_manager.getContext(), Appodeal.VIDEO);
	}

	@Override
	public void onVideoShown()
	{
		if (m_manager.isAdShown())
		{
			Log.e(TAG, "Ad already showing!");
			return;
		}
		
		m_manager.adLoaded(m_data, null);
	}

}