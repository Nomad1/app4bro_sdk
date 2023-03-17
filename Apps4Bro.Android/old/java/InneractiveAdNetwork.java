package net.runserver.apps4bro;

import android.app.Activity;
import android.util.Log;

import com.inneractive.api.ads.sdk.InneractiveAdManager;
import com.inneractive.api.ads.sdk.InneractiveAdView;
import com.inneractive.api.ads.sdk.InneractiveErrorCode;
import com.inneractive.api.ads.sdk.InneractiveInterstitialView;
import com.inneractive.api.ads.sdk.InneractiveInterstitialView.InneractiveInterstitialAdListener;

class InneractiveAdNetwork implements AdNetworkHandler
{
	private final static String TAG = "Inneractive";
	
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

	public InneractiveAdNetwork(AdManager manager)
	{
		m_manager = manager;
	}

	public boolean show(final Object data)
	{
		try
		{
	    	InneractiveAdManager.initialize((Activity) m_manager.getContext());	    	
	    	InneractiveInterstitialView mInterstitial = new InneractiveInterstitialView((Activity) m_manager.getContext(), m_id);
	    	
	    	// setters
	    	mInterstitial.setInterstitialAdListener(new InneractiveInterstitialAdListener()
			{
				
				@Override
				public void inneractiveInterstitialVideoCompleted(InneractiveInterstitialView arg0)
				{
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public void inneractiveInterstitialShown(InneractiveInterstitialView arg0)
				{
					if (m_manager.isAdShown())
					{
						Log.e(TAG, "Ad already showing!");
						return;
					}
					
					m_manager.adLoaded(data, null);
				}
				
				@Override
				public void inneractiveInterstitialLoaded(InneractiveInterstitialView arg0)
				{
					if (m_manager.isAdShown())
					{
						Log.e(TAG, "Ad already showing!");
						return;
					}
					
					arg0.showAd();
				}
				
				@Override
				public void inneractiveInterstitialFailed(InneractiveInterstitialView arg0, InneractiveErrorCode arg1)
				{
					Log.w(TAG, "Inneractive Ad failed to load: " + arg1);					
					m_manager.adError(data, "Inneractive Ad failed to load: " + arg1);
				}
				
				@Override
				public void inneractiveInterstitialDismissed(InneractiveInterstitialView arg0)
				{
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public void inneractiveInterstitialClicked(InneractiveInterstitialView arg0)
				{
					m_manager.adClick(data); 					
				}
				
				@Override
				public void inneractiveInternalBrowserDismissed(InneractiveAdView arg0)
				{
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public void inneractiveDefaultInterstitialLoaded(InneractiveInterstitialView arg0)
				{
					Log.w(TAG, "Inneractive Ad failed to load: no fill");
					m_manager.adError(data, "No fill");
				}
				
				@Override
				public void inneractiveAdWillOpenExternalApp(InneractiveAdView arg0)
				{
					// TODO Auto-generated method stub
					
				}
			});

	    	Log.d(TAG, "Inneractive SDK v= " + mInterstitial.getSDKversion());
	    	
	    	mInterstitial.loadAd();

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