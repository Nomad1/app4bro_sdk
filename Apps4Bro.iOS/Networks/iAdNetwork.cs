#if __IOS__ && USE_IAD
using System;
using iAd;

namespace Apps4Bro.Networks
{
    internal class iAdNetwork : AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private ADInterstitialAd m_interstitial;
        private object m_data;

        public string Network
        {
            get { return "iAd"; }
        }

        public iAdNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();
            
            m_interstitial = new ADInterstitialAd();
            
            m_interstitial.FailedToReceiveAd += (object sender, ADErrorEventArgs e) =>
            {
                m_adManager.AdError(m_data, "Error " + e.Error);
                Hide(); // need this to stop banner spamming
            };
            
            m_interstitial.ActionFinished += (object sender, EventArgs e) =>
            {
                //m_adManager.AdClick(m_data);
            };
            
            m_interstitial.WillLoad += (object sender, EventArgs e) =>
            {
                Console.WriteLine("iAd received interstitial banner");
            };
                
            m_interstitial.AdLoaded += (object sender, EventArgs e) =>
            {
                m_interstitial.PresentFromViewController((UIKit.UIViewController)m_adManager.Context);
                m_adManager.AdLoaded(m_data);
            };
           
            return true;
        }
        
        public void Hide()
        {
            if (m_interstitial != null)
            {
                m_interstitial.Dispose();
                m_interstitial = null;
            }
        }

        public void SetId(string id)
        {
        }
    }
}
#endif