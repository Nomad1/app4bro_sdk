package net.runserver.apps4bro;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback;

class AdMobStartupNetwork implements AdNetworkHandler
{
    private final static String TAG = "AdMobStartup";
    private final static String TAG_NPA = "AdMobStartupNPA";
    private final boolean m_npa;

    public String getNetwork()
    {
        return m_npa ? TAG_NPA : TAG;
    }

    public int getType()
    {
        return AdEnums.NetworkType.AppOpen;
    }

    public AdMobStartupNetwork(boolean npa)
    {
        m_npa = npa;
    }

    public AdObject request(final AdManager manager, final String id, final Object data)
    {
        Log.d(TAG, "Running network " + getNetwork() + "[" + id + "]");

        AdRequest request;
        if (m_npa || Apps4BroSDK.isAdTrackingLimited())
        {
            Bundle extras = new Bundle();
            extras.putString("npa", "1");

            request = new AdRequest.Builder()
                    .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter.class, extras)
                    .build();
        } else
            request = new AdRequest.Builder().build();

        AdMobStartupAd result = new AdMobStartupAd(manager, id, data);

        AppOpenAd.load(manager.getApplicationContext(), id, request, result);

        return result;
    }

    class AdMobStartupAd extends AppOpenAdLoadCallback implements AdObject
    {
        private final String m_id;
        private final Object m_data;
        private final AdManager m_manager;

        private AppOpenAd m_appOpenAd;
        private AdEnums.AdState m_state;

        public String getId()
        {
            return m_id;
        }

        public AdEnums.AdState getState()
        {
            return m_state;
        }

        public AdMobStartupAd(AdManager manager, String id, Object data)
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
                if (m_appOpenAd == null)
                {
                    m_state = AdEnums.AdState.Failed;
                    return false;
                }

                m_appOpenAd.show(context);
                // State transitions to Shown via onAdShowedFullScreenContent, or to
                // Failed via onAdFailedToShowFullScreenContent — actual callback wins.

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

            // App Open Ads have no hide API; lifecycle drives dismissal.
        }

        @Override
        public void onAdFailedToLoad(LoadAdError error)
        {
            m_state = AdEnums.AdState.Failed;

            AdEnums.AdManagerError code;
            switch (error.getCode())
            {
                case AdRequest.ERROR_CODE_NO_FILL:
                case AdRequest.ERROR_CODE_MEDIATION_NO_FILL:
                    code = AdEnums.AdManagerError.NoFill;
                    break;
                case AdRequest.ERROR_CODE_NETWORK_ERROR:
                    code = AdEnums.AdManagerError.NoNetwork;
                    break;
                default:
                    code = AdEnums.AdManagerError.FailedToLoad;
                    break;
            }

            m_manager.adError(m_data, code, "AdMobStartup: " + error.toString());
        }

        @Override
        public void onAdLoaded(AppOpenAd appOpenAd)
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

                @Override
                public void onAdFailedToShowFullScreenContent(AdError error)
                {
                    m_state = AdEnums.AdState.Failed;

                    m_manager.adFailedToShow(m_data, "AdMobStartup: " + error.toString());
                }
            };

            m_appOpenAd = appOpenAd;
            m_appOpenAd.setFullScreenContentCallback(fullScreenContentCallback);

            m_state = AdEnums.AdState.Ready;

            m_manager.adReady(m_data);
        }
    }
}
