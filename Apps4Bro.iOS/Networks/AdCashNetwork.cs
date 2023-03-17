#if USE_ADCASH
using System;

#if __IOS__
using AdcashSDK.Api;
#else
using AdcashSDK;
#endif

namespace Apps4Bro.Networks
{
    internal class AdCashNetwork : AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private Interstitial m_adCashInterstitial;
        private object m_data;

        public string Network
        {
            get { return "AdCash"; }
        }

        public AdCashNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();
            
            m_adCashInterstitial = new Interstitial(m_id);
            
            m_adCashInterstitial.AdLoaded += (object sender, EventArgs e) =>
            {
                Console.WriteLine("AdCash received interstitial banner");
                m_adCashInterstitial.Show();
            };
            
            m_adCashInterstitial.AdOpened += (object sender, EventArgs e) =>
            {
                m_adManager.AdLoaded(m_data);
            };
            
            m_adCashInterstitial.AdFailedToLoad += (object sender, AdFailedToLoadEventArgs e) =>
            {
                m_adManager.AdError(m_data, "Error " + e.Message);
            };
            
            m_adCashInterstitial.AdLeftApplication += (object sender, EventArgs e) =>
            {
                m_adManager.AdClick(m_data);
            };
                
            m_adCashInterstitial.LoadAd();
            
            return true;
        }
        
        public void Hide()
        {
            if (m_adCashInterstitial != null)
            {
                m_adCashInterstitial.Destroy();
                m_adCashInterstitial = null;
            }
        }

        public void SetId(string id)
        {
            m_id = id;
        }
    }
}
#endif