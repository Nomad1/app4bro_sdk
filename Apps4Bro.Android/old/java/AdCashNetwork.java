package net.runserver.apps4bro;

import android.app.Activity;
import android.util.Log;

import com.adcash.mobileads.AdcashAdRequestFailedError;
import com.adcash.mobileads.AdcashInterstitial;
import com.adcash.mobileads.AdcashView.AdListener;

class AdCashNetwork implements AdNetworkHandler
{
	private final static String TAG = "AdCash";

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

	public AdCashNetwork(AdManager manager)
	{
		m_manager = manager;
	}

	public boolean show(final Object data)
	{
		try
		{
			final AdcashInterstitial mInterstitial = new AdcashInterstitial(m_id, (Activity) m_manager.getContext());

			mInterstitial.setAdListener(new AdListener()
			{
				// Only define methods what are needed!!
				@Override
				public void onAdLoaded()// When interstitial is ready to be
										// shoved
				{
					if (m_manager.isAdShown())
					{
						Log.e(TAG, "Ad already showing!");
						return;
					}
						
					mInterstitial.showAd();
					m_manager.adLoaded(data, null);
				}

				@Override
				public void onAdClosed() // When Interstitial ad is closed
				{
					// do something
				}

				@Override
				public void onAdFailedToLoad(int errorCode)
				{
					super.onAdFailedToLoad(errorCode);
					
					String message = "";
					switch (errorCode)
					{
						case AdcashAdRequestFailedError.NO_NETWORK:
							message = "No internet connection";
							break;
						case AdcashAdRequestFailedError.REQUEST_FAILED:
							message = "Request failed";
							break;
						case AdcashAdRequestFailedError.NETWORK_FAILURE:
							message = "Network failure";
							break;
						case AdcashAdRequestFailedError.NO_AD:
							message = "There is no ad";
							break;
						default:
							message = "Some other problem";
							break;
					}
					Log.e(TAG, "AdCash Ad failed to load: " + message);

					m_manager.adError(data, message);
				}
				
				@Override
				public void onAdLeftApplication()
				{
					super.onAdLeftApplication();
					
					m_manager.adClick(data);
				}
			});
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