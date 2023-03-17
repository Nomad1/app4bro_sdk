package net.runserver.apps4bro;
import android.app.Activity;
import android.util.Log;

import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdClickListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdService;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkSettings;

class AppLovinNetwork implements AdNetworkHandler
{
	private final static String TAG = "AppLovin";
	
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

	public AppLovinNetwork(AdManager manager)
	{
		m_manager = manager;
	}
	
	public boolean show(final Object data)
	{
		try
		{
			final Activity context = (Activity)m_manager.getContext();
			
			AppLovinSdkSettings settings = new AppLovinSdkSettings();
			settings.setAutoPreloadSizes("INTERSTITIAL");
			
			AppLovinSdk.initializeSdk(context);
	        final AppLovinSdk sdk = AppLovinSdk.getInstance(m_id, settings, context);
	        final AppLovinAdService adService = sdk.getAdService();
	        adService.loadNextAd( AppLovinAdSize.INTERSTITIAL, new AppLovinAdLoadListener()
			{

				@Override
				public void adReceived(AppLovinAd appLovinAd)
				{
					if (m_manager.isAdShown())
					{
						Log.e(TAG, "Ad already showing!");
						return;
					}
						
					AppLovinInterstitialAdDialog ad = AppLovinInterstitialAd.create(sdk, context);
					ad.setAdClickListener(new AppLovinAdClickListener()
					{
						@Override
						public void adClicked(AppLovinAd arg0)
						{
							m_manager.adClick(data);
						}
					});
					ad.showAndRender(appLovinAd);
					m_manager.adLoaded(data, null);
				}

				@Override
				public void failedToReceiveAd(int arg0)
				{
					m_manager.adError(data, "Applovin Ad failed to load: " + arg0);
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