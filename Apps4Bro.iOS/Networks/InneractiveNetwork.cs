#if USE_INNERACTIVE
using System;
using InneractiveAds;

namespace Apps4Bro.Networks
{
    internal class InneractiveNetwork : InneractiveAdDelegate, AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        
        static InneractiveNetwork()
        {
            InneractiveAdSDK.SharedInstance.Initialize();
        }
        
        private string m_id;
        private object m_data;
        private IaAdView m_adView;

        public string Network
        {
            get { return "Inneractive"; }
        }

        public InneractiveNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;
            
            Hide();

            m_adView = new IaAdView(m_id, IaAdType.Interstitial, this);
            InneractiveAdSDK.SharedInstance.LoadAd(m_adView);
            
            return true;
        }

        public void Hide()
        {
            if (m_adView != null)
            {
                InneractiveAdSDK.SharedInstance.RemoveAd(m_adView);
                m_adView.RemoveFromSuperview();
                m_adView = null;
            }
        }

        public void SetId(string id)
        {
            m_id = id;
        }
        
        public override void InneractiveAdLoaded(IaAd ad)
        {
            Console.WriteLine("Inneractive received interstitial banner");   
            InneractiveAdSDK.SharedInstance.ShowInterstitialAd((IaAdView)ad);
        }

        public override void InneractiveAdFailedWithError(Foundation.NSError error, IaAd ad)
        {
            m_adManager.AdError(m_data, "AdError: " + error);
        }

        public override void InneractiveDefaultAdLoaded(IaAd ad)
        {
            #if DEBUG
            Console.WriteLine("Inneractive received default banner");   
            InneractiveAdLoaded(ad);
            #else
            m_adManager.AdError(m_data, "No fill");
            #endif
        }

        public override void InneractiveInterstitialAdWillShow(IaAdView ad)
        {
            m_adManager.AdLoaded(m_data);
        }
        
        public override void InneractiveInterstitialAdDidShow(IaAdView adView)
        {
            
        }

        public override UIKit.UIViewController ViewControllerForPresentingModalView
        {
            get { return (UIKit.UIViewController)m_adManager.Context; }
        }
        
        public override void InneractiveAdClicked(IaAd ad)
        {
            m_adManager.AdClick(m_data);
        }
    }
}
#endif