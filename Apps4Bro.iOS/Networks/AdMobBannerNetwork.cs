#if USE_ADMOB
using System;
using Google.MobileAds;
using UIKit;
using CoreGraphics;
using Foundation;

namespace Apps4Bro.Networks
{
    internal class AdMobBannerNetwork : BannerViewDelegate, AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private BannerView m_gadBannerView;
        private object m_data;

        public string Network
        {
            get { return "AdMobBanner"; }
        }

        public AdMobBannerNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();
            
            UIViewController controller = (UIViewController)m_adManager.Context;
            
            CGRect frame = new CGRect();

            nfloat width = Math.Max((float)UIScreen.MainScreen.Bounds.Width, (float)UIScreen.MainScreen.Bounds.Height);
            nfloat height = Math.Min((float)UIScreen.MainScreen.Bounds.Width, (float)UIScreen.MainScreen.Bounds.Height);


            bool top = Apps4BroSDK.Settings.ContainsKey(Apps4BroSettings.ShowBannerOnTop) && (bool)Apps4BroSDK.Settings[Apps4BroSettings.ShowBannerOnTop] == true;
            float bannerHeight;

            if (UIDevice.CurrentDevice.UserInterfaceIdiom == UIUserInterfaceIdiom.Pad)
            {
                bannerHeight = 90;
                switch (controller.InterfaceOrientation)
                {
                    case UIInterfaceOrientation.LandscapeLeft:
                    case UIInterfaceOrientation.LandscapeRight:
                        frame = new CGRect(0, top ?  0 : height - bannerHeight, width, bannerHeight);
                        break;
                    case UIInterfaceOrientation.Portrait:
                    case UIInterfaceOrientation.PortraitUpsideDown:
                        frame = new CGRect(0, top ? 0 : width - bannerHeight, height, bannerHeight);
                        break;
                }
            } else
            {
                bannerHeight = 60;
                switch (controller.InterfaceOrientation)
                {
                    case UIInterfaceOrientation.LandscapeLeft:
                    case UIInterfaceOrientation.LandscapeRight:
                        frame = new CGRect(0, top ? 0 : height - bannerHeight, width, bannerHeight);
                        break;
                    case UIInterfaceOrientation.Portrait:
                    case UIInterfaceOrientation.PortraitUpsideDown:
                        frame = new CGRect(0, top ? 0 : width - bannerHeight, height, bannerHeight);
                        break;
                }
            }
            
            m_gadBannerView = new BannerView(
                controller.InterfaceOrientation == UIInterfaceOrientation.Portrait || controller.InterfaceOrientation == UIInterfaceOrientation.PortraitUpsideDown ?
                    AdSizeCons.GetPortraitAnchoredAdaptiveBannerAdSize(height) :
                AdSizeCons.GetLandscapeAnchoredAdaptiveBannerAdSize(width), new CGPoint(frame.X, frame.Y));
            
            m_gadBannerView.RootViewController = controller;
            m_gadBannerView.Frame = frame;
            m_gadBannerView.Hidden = true;

            if (Apps4BroSDK.Settings.ContainsKey(Apps4BroSettings.UseBannerSuperview) && (bool)Apps4BroSDK.Settings[Apps4BroSettings.UseBannerSuperview] == true)
                controller.View.Superview.AddSubview(m_gadBannerView);
            else
                controller.View.AddSubview(m_gadBannerView);

#if DEBUG
            m_gadBannerView.AdUnitId = "ca-app-pub-3940256099942544/2934735716"; // test ads
#else
            m_gadBannerView.AdUnitId = m_id;
#endif

            m_gadBannerView.Delegate = this;

            Request request = Request.GetDefaultRequest();
            if (Apps4BroSDK.UseNonPersonalizedAds)
            {
                var extras = new Extras ();
                extras.AdditionalParameters = NSDictionary.FromObjectAndKey (new NSString ("1"), new NSString ("npa"));
                request.RegisterAdNetworkExtras(extras);
            }
            m_gadBannerView.LoadRequest(request);
            return true;
        }

        public void Display()
        {
            if (m_gadBannerView != null)
            {
                m_gadBannerView.Hidden = false;
                m_adManager.AdShown(m_data, m_gadBannerView);
            }
        }
        
        public void Hide()
        {
            if (m_gadBannerView != null)
            {
                m_gadBannerView.Delegate = null;
                m_gadBannerView.RemoveFromSuperview();
                m_gadBannerView.Dispose();
                m_gadBannerView = null;
            }
        }

        public void SetId(string id)
        {
            m_id = id;
        }

       

        public override void DidReceiveAd(BannerView view)
        {
            //Console.WriteLine("AdMob received banner");
            m_adManager.AdLoaded(m_data);
        }

        public override void WillPresentScreen(BannerView adView)
        {
            m_adManager.AdClicked(m_data);
            // never called
        }

#if OLD_ADMOB
        public override void WillLeaveApplication(BannerView adView)
        {
            m_adManager.AdClicked(m_data);
        }

        public override void DidFailToReceiveAd(BannerView view, RequestError error)
        {
            if (error.Code == 9) // no fill
                m_adManager.AdError(m_data, "Error " + error.Code);
            else
                m_adManager.AdError(m_data, "Error " + error.Code + ": " + error.Description);
        }
#else
        public override void WillDismissScreen(BannerView adView)
        {
            m_adManager.AdClicked(m_data);
        }

        public override void DidFailToReceiveAd(BannerView view, NSError error)
        {
            m_adManager.AdError(m_data, "Error " + error.Code + ": " + error.Description);
        }

        public override void DidRecordClick(BannerView view)
        {
            m_adManager.AdClicked(m_data);
        }
#endif
    }
}
#endif