package net.runserver.apps4bro;

import android.util.Log;

class AdMobNetwork implements AdNetworkHandler
{
	private final static String TAG = "AdMob";
	
	private final AdManager m_manager;
	private String m_id;

	public void setId(String id)
	{
		m_id = id;
	}

	public String getNetwork()
	{
		return TAG;
	}

	public AdMobNetwork(AdManager manager)
	{
		m_manager = manager;
	}

	public boolean show(final Object data)
	{
		try
		{
			Log.d(TAG, "Running network " + getNetwork() + "[" + m_id + "]");
			final com.google.android.gms.ads.InterstitialAd interstitial = new com.google.android.gms.ads.InterstitialAd(m_manager.getContext());
			interstitial.setAdUnitId(m_id);
			interstitial.loadAd(new com.google.android.gms.ads.AdRequest.Builder().build());
			
			interstitial.setAdListener(new com.google.android.gms.ads.AdListener()
			{

				public void onAdLoaded()
				{
					super.onAdLoaded();

					if (m_manager.isAdShown())
					{
						Log.e(TAG, "Ad already showing!");
						return;
					}
						
					try
					{
						interstitial.show();
						m_manager.adLoaded(data, null);
					} catch (Exception ex)
					{
						m_manager.adError(data, "Crash in AdMob");
					}
				}

				@Override
				public void onAdFailedToLoad(int errorCode)
				{
					super.onAdFailedToLoad(errorCode);
					
					m_manager.adError(data, "AdMob Failed to load ad: " + errorCode);
				}
				
				@Override
				public void onAdLeftApplication()
				{
					super.onAdLeftApplication();
					
					m_manager.adClick(data);
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
}