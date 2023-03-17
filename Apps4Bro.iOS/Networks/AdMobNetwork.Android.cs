#if __ANDROID__
using System;
using Android.Gms.Ads;
using Android.Content;

namespace Apps4Bro.Networks
{
    internal class AdMobNetwork : AdListener, AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private InterstitialAd m_gadInterstitial;
        private object m_data;

        public string Network
        {
            get { return "AdMob"; }
        }

        public AdMobNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();

            m_gadInterstitial = new InterstitialAd((Context)m_adManager.Context);
            m_gadInterstitial.AdUnitId = m_id;
            m_gadInterstitial.AdListener = this;
            m_gadInterstitial.LoadAd(new AdRequest.Builder().Build());

            return true;
        }

        public void Hide()
        {
            if (m_gadInterstitial != null)
            {
                m_gadInterstitial.AdListener = null;
                m_gadInterstitial.Dispose();
                m_gadInterstitial = null;
            }
        }

        public void SetId(string id)
        {
            m_id = id;
        }
        
        public override void OnAdLoaded()
        {
            Console.WriteLine("AdMob received interstitial banner");
            m_gadInterstitial.Show();
            m_adManager.AdLoaded(m_data);
        }
        
        public override void OnAdFailedToLoad(int errorCode)
        {
            m_adManager.AdError(m_data, "Error " + errorCode);
        }
       
        public override void OnAdLeftApplication()
        {
            m_adManager.AdClick(m_data);
        }
    }
}
#endif
