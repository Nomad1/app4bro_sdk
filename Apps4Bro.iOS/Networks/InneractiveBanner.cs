#if USE_INNERACTIVE
using System;
using InneractiveAds;
using UIKit;

namespace Apps4Bro.Networks
{
    internal class InneractiveBanner : InneractiveAdDelegate, AdNetworkHandler
    {
        private readonly AdManager m_adManager;

        static InneractiveBanner()
        {
            InneractiveAdSDK.SharedInstance.Initialize();
        }

        private string m_id;
        private object m_data;
        private IaAdView m_adBannerView;

        public string Network
        {
            get { return "InneractiveBanner"; }
        }

        public InneractiveBanner(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();
            
            UIViewController controller = (UIViewController)m_adManager.Context;
            
            m_adBannerView = new IaAdView(m_id, IaAdType.Banner, this);
            
            controller.View.AddSubview(m_adBannerView);
            m_adBannerView.Hidden = true; 
            
            InneractiveAdSDK.SharedInstance.LoadAd(m_adBannerView);

            return true;
        }

        public void Hide()
        {
            if (m_adBannerView != null)
            {
                InneractiveAdSDK.SharedInstance.RemoveAd(m_adBannerView);
                m_adBannerView.RemoveFromSuperview();
                m_adBannerView = null;
            }
        }

        public void SetId(string id)
        {
            m_id = id;
        }

        public override void InneractiveAdLoaded(IaAd ad)
        {
            Console.WriteLine("Inneractive received  banner");   
            ad.Hidden = false;
            m_adManager.AdLoaded(m_data);
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