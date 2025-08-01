package net.runserver.apps4bro;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.ads.*;

class AdMobNetwork implements AdNetworkHandler
{
    private final static String TAG = "AdMob";
    private final static String TAG_NPA = "AdMobNPA";

    private final AdManager m_manager;
    private final boolean m_npa;

    public String getNetwork()
    {
        return m_npa ? TAG_NPA : TAG;
    }

    public AdMobNetwork(AdManager manager, boolean npa)
    {
        m_manager = manager;
        m_npa = npa;
    }

    public AdObject request(final String id, final Object data)
    {
        Log.d(TAG, "Running network " + getNetwork() + "[" + id + "]");
        final InterstitialAd interstitial = new InterstitialAd(m_manager.getContext());
        interstitial.setAdUnitId(id);

        AdRequest request;
        if (m_npa || Apps4BroSDK.isAdTrackingLimited()) // TODO: think of user conset for AdMob
        {
            Bundle extras = new Bundle();
            extras.putString("npa", "1");


            request = new AdRequest.Builder()
                    .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter.class, extras)
                    .build();
        } else
            request = new AdRequest.Builder().build();

        AdMobAd result = new AdMobAd(id, interstitial, data);

        interstitial.setAdListener(result);
        interstitial.loadAd(request);

        return result;
    }

    class AdMobAd extends AdListener implements AdObject
    {
        private final String m_id;
        private final InterstitialAd m_interstitial;
        private final Object m_data;
        private AdState m_state;

        public String getId()
        {
            return m_id;
        }

        public AdState getState()
        {
            return m_state;
        }

        public AdMobAd(String id, InterstitialAd interstitial, Object data)
        {
            m_id = id;
            m_interstitial = interstitial;
            m_data = data;
            m_state = AdState.Loading;
        }

        public boolean show()
        {
            try
            {
                if (!m_interstitial.isLoaded())
                {
                    m_state = AdState.Failed;
                    return false;
                }

                if (m_manager.isTimedOut())
                {
                    Log.e(TAG, "Ad loading timed out!");
                    return false;
                }

                m_state = AdState.Used; // consume ad

                m_interstitial.show();

                return true;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                m_state = AdState.Failed;
                return false;
            }
        }

        public void hide()
        {
            m_state = AdState.Closed;

            // no way to hide AdMob Interstitials?
        }

        public void onAdLoaded()
        {
            super.onAdLoaded();

            m_state = AdState.Ready;

            m_manager.adReady(m_data);
        }

        @Override
        public void onAdOpened()
        {
            super.onAdOpened();

            m_state = AdState.Used;

            m_manager.adShown(m_data, null);
        }

        @Override
        public void onAdFailedToLoad(int errorCode)
        {
            super.onAdFailedToLoad(errorCode);

            m_state = AdState.Failed;

            m_manager.adError(m_data, "AdMob Failed to load ad: " + errorCode);
        }

        @Override
        public void onAdLeftApplication()
        {
            super.onAdLeftApplication();

            m_state = AdState.Closed;

            m_manager.adClick(m_data);
        }
    }
}