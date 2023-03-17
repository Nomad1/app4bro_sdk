/*package net.runserver.apps4bro;

import android.app.Activity;
import android.util.Log;

import com.appodeal.ads.Appodeal;
import com.appodeal.ads.VideoCallbacks;

class AppodealVideoNetwork implements AdNetworkHandler
{
	private final static String TAG = "AppodealVideo";
	
	private final AdManager m_manager;
	private String m_id;

	public String getNetwork()
	{
		return TAG;
	}

	public void setId(String id)
	{
		m_id = id;
	}

	public AppodealVideoNetwork(AdManager manager)
	{
		m_manager = manager;
	}
	
	public boolean show(final Object data)
	{
		try
		{
			final Activity context = (Activity)m_manager.getContext();
			Appodeal.disableNetwork(context, "chartboost");
			
			Appodeal.disableNetwork(context, "applovin");
			Appodeal.disableNetwork(context, "unity_ads");
			Appodeal.disableNetwork(context, "admob");
			
			Appodeal.initialize(context, m_id, Appodeal.VIDEO);
			Appodeal.cache(context, Appodeal.VIDEO);
			
			Appodeal.setVideoCallbacks(new VideoCallbacks()
			{
				@Override
				public void onVideoClosed()
				{
					// TODO Auto-generated method stub
				}

				@Override
				public void onVideoFailedToLoad()
				{
					m_manager.adError(data, "AppoDeal Video Ad failed to load");
				}

				@Override
				public void onVideoFinished()
				{
					// TODO Auto-generated method stub
					//m_manager.adClick(data);
				}

				@Override
				public void onVideoLoaded()
				{
					if (m_manager.isAdShown())
					{
						Log.e(TAG, "Ad already showing!");
						return;
					}
					
					Appodeal.show(context, Appodeal.VIDEO);
				}

				@Override
				public void onVideoShown()
				{
					if (m_manager.isAdShown())
					{
						Log.e(TAG, "Ad already showing!");
						return;
					}
					
					m_manager.adLoaded(data, null);
				}
			});

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
}*/