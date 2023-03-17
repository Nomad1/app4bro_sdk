#if __IOS__ && USE_IAD
using System;
using iAd;
using UIKit;
using Foundation;

namespace Apps4Bro.Networks
{
    internal class iAdBannerNetwork : AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private ADBannerView m_adBannerView;
        private object m_data;

        public string Network
        {
            get { return "iAdBanner"; }
        }

        public iAdBannerNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();

            UIViewController controller = (UIViewController)m_adManager.Context;
            
            m_adBannerView = new ADBannerView(); 

            NSMutableSet mutableSet = new NSMutableSet(); 
            mutableSet.Add(ADBannerView.SizeIdentifierLandscape); 
            mutableSet.Add(ADBannerView.SizeIdentifierPortrait); 
            m_adBannerView.RequiredContentSizeIdentifiers = mutableSet; 
            m_adBannerView.CurrentContentSizeIdentifier = 
                controller.InterfaceOrientation == UIInterfaceOrientation.Portrait || controller.InterfaceOrientation == UIInterfaceOrientation.PortraitUpsideDown ? 
                ADBannerView.SizeIdentifierPortrait : ADBannerView.SizeIdentifierLandscape;
            
            
            m_adBannerView.AdLoaded += (object sender, EventArgs e) =>
                {
                    m_adBannerView.Hidden = false;
                    m_adManager.AdLoaded(m_data, m_adBannerView);
                };
            
            m_adBannerView.WillLoad += (object sender, EventArgs e) =>
                {
                    Console.WriteLine("iAd received banner");
                };
            m_adBannerView.FailedToReceiveAd += (object sender, AdErrorEventArgs e) => 
                {
                    m_adManager.AdError(m_data, "Error " + e.Error);
                    Hide();
                };
            
            m_adBannerView.ActionFinished +=  (object sender, EventArgs e) =>
                {
                    m_adManager.AdClick(m_data);
                };
            
            
            controller.View.AddSubview(m_adBannerView);
            m_adBannerView.Hidden = true; 
            
            return true;
        }

        public void Hide()
        {
            if (m_adBannerView != null)
            {
                m_adBannerView.RemoveFromSuperview();
                m_adBannerView.Dispose();
                m_adBannerView = null;
            }
        }

        public void SetId(string id)
        {
        }
    }
}
#endif