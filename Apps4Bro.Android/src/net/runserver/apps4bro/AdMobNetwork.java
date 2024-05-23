package net.runserver.apps4bro;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.*;

class AdMobNetwork implements AdNetworkHandler
{
    private final static String TAG = "AdMob";
    private final static String TAG_NPA = "AdMobNPA";
    private final boolean m_npa;

    public String getNetwork()
    {
        return m_npa ? TAG_NPA : TAG;
    }

    public AdMobNetwork(boolean npa)
    {
        m_npa = npa;
    }

    public AdObject request(final AdManager manager, final String id, final Object data)
    {
        Log.d(TAG, "Running network " + getNetwork() + "[" + id + "]");
//        final InterstitialAd interstitial = new InterstitialAd(m_manager.getContext());
//        interstitial.setAdUnitId(id);

        AdRequest request;
        if (m_npa || Apps4BroSDK.isAdTrackingLimited()) // TODO: think of user consent for AdMob
        {
            Bundle extras = new Bundle();
            extras.putString("npa", "1");


            request = new AdRequest.Builder()
                    .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter.class, extras)
                    .build();
        } else
            request = new AdRequest.Builder().build();


        AdMobAd result = new AdMobAd(manager, id, data);

        InterstitialAd.load(manager.getApplicationContext(), id, request, result);
//        interstitial.loadAd(request);

        return result;
    }

    class AdMobAd extends InterstitialAdLoadCallback implements AdObject
    {
        private final String m_id;
        private final Object m_data;
        private final AdManager m_manager;

        private InterstitialAd m_interstitial;
        private AdEnums.AdState m_state;

        public String getId()
        {
            return m_id;
        }

        public AdEnums.AdState getState()
        {
            return m_state;
        }

        public AdMobAd(AdManager manager, String id, Object data)
        {
            m_id = id;
            m_manager = manager;
            m_data = data;
            m_state = AdEnums.AdState.Loading;
        }

        public boolean show(Activity context)
        {
            try
            {
                if (m_interstitial == null)
                {
                    m_state = AdEnums.AdState.Failed;
                    return false;
                }

                m_state = AdEnums.AdState.Shown; // consume ad

                m_interstitial.show(context);

                return true;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                m_state = AdEnums.AdState.Failed;
                return false;
            }
        }

        public void hide()
        {
            m_state = AdEnums.AdState.Closed;

            // no way to hide AdMob Interstitials?
        }

        @Override
        public void onAdFailedToLoad(LoadAdError error)
        {
            m_state = AdEnums.AdState.Failed;

            m_manager.adError(m_data, "AdMob Failed to load ad. Error " + error.toString());
        }

        @Override
        public void onAdLoaded(InterstitialAd interstitialAd)
        {
            FullScreenContentCallback fullScreenContentCallback = new FullScreenContentCallback() {
                @Override
                public void onAdClicked()
                {
                    m_state = AdEnums.AdState.Closed;

                    m_manager.adClick(m_data);
                }

                @Override
                public void onAdDismissedFullScreenContent()
                {
                    m_state = AdEnums.AdState.Closed;

                    m_manager.adClosed(m_data);
                }

                @Override
                public void onAdShowedFullScreenContent()
                {
                    m_state = AdEnums.AdState.Shown;

                    m_manager.adShown(m_data, null);
                }
            };

            m_interstitial = interstitialAd;
            m_interstitial.setFullScreenContentCallback(fullScreenContentCallback);

            m_state = AdEnums.AdState.Ready;

            m_manager.adReady(m_data);
        }
    }
}