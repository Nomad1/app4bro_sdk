using UIKit;
using Foundation;
using System.Text;
using System;
using CoreTelephony;

using System.Globalization;

namespace Apps4Bro.Networks
{
    internal class HouseNetwork : AdNetworkHandler
    {
        private readonly AdManager m_adManager;
        private string m_id;
        private object m_data;
        
        private HouseBannerView m_bannerView;

        public string Network
        {
            get { return "House"; }
        }

        public HouseNetwork(AdManager manager)
        {
            m_adManager = manager;
        }

        public bool Show(object data)
        {
            m_data = data;

            Hide();
            
            m_bannerView = new HouseBannerView(m_id);

            m_bannerView.DidFailWithError += HandleDidFailWithError;
            m_bannerView.DidClosed += HandleDidClosed;
            m_bannerView.WillLeaveApplication += HandleWillLeaveApplication;
            m_bannerView.DidLoadedAd += HandleDidLoadedAd;
            m_bannerView.DidShownAd += HandleDidShownAd;
            m_bannerView.Load();
            
            return true;
        }

        public void Display()
        {
            if (m_bannerView != null)
                m_bannerView.Show();
        }

        public void Hide()
        {
            if (m_bannerView != null)
            {
                m_bannerView.RemoveFromSuperview();
                m_bannerView = null;
            }
        }

        public void SetId(string id)
        {
            m_id = id;
        }
        
        void HandleDidLoadedAd(object sender, EventArgs e)
        {
            m_adManager.AdLoaded(m_data);
        }

        void HandleDidShownAd(object sender, EventArgs e)
        {
            m_adManager.AdShown(m_data, m_bannerView);
        }

        void HandleWillLeaveApplication (object sender, EventArgs e)
        {
            m_adManager.AdClicked(m_data);
            
            Hide();
        }

        void HandleDidClosed (object sender, EventArgs e)
        {
            Hide();
        }

        void HandleDidFailWithError (object sender, string e)
        {
            m_adManager.AdError(m_data, "Error " + e);
            
            Hide();
        }

    }

    public class HouseBannerView : UIWebView
    {
        private readonly string m_zoneId;
        private readonly int m_timeout;

        public event EventHandler DidLoadedAd;
        public event EventHandler DidShownAd;
        public event EventHandler<string> DidFailWithError;
        public event EventHandler WillLeaveApplication;
        public event EventHandler DidClosed;

        public string ZoneID
        { 
            get { return m_zoneId;}
        }

        public int Timeout
        {
            get { return m_timeout; }
        }

        public static bool IsLandscape
        {
            get { return UIApplication.SharedApplication.KeyWindow.Frame.Width > UIApplication.SharedApplication.KeyWindow.Frame.Height; }
        }
        
        public HouseBannerView(string zoneId)
                : base(UIApplication.SharedApplication.KeyWindow.Frame)
        {
            m_zoneId = zoneId;
            m_timeout = Apps4BroSDK.HouseAdTimeout;

            Opaque = false;
            BackgroundColor = UIColor.Clear;
            Alpha = 0.0f;

            ScalesPageToFit = true;
            Delegate = new WebViewDelegate(this);
            AutoresizingMask = UIViewAutoresizing.FlexibleWidth | UIViewAutoresizing.FlexibleHeight;
        }
        
        public void Show()
        {
            UIApplication.SharedApplication.KeyWindow.AddSubview(this);

            UIView.BeginAnimations(null);
            UIView.SetAnimationDuration(1);
            Alpha = 1.0f;
            UIView.CommitAnimations();

            if (DidShownAd != null)
                DidShownAd(this, null);
        }

        public void Load()
        {
            string model = UIDevice.CurrentDevice.Model;
            string version = UIDevice.CurrentDevice.SystemVersion;
            
            string carrierName;
            try
            {
                CTTelephonyNetworkInfo networkInfo = new CTTelephonyNetworkInfo();
                carrierName = networkInfo.SubscriberCellularProvider == null ? null : networkInfo.SubscriberCellularProvider.CarrierName;
            }
            catch
            {
                carrierName = null;
            }

            string url = string.Format(Apps4BroSDK.HouseAdUrl, m_zoneId, NSBundle.MainBundle.BundleIdentifier, UrlEncode(model), version, UrlEncode(carrierName), Frame.Size.Width, Frame.Size.Height, CultureInfo.CurrentCulture.TwoLetterISOLanguageName, Apps4BroSDK.Version, Apps4BroSDK.Platform, Apps4BroSDK.AdvertisingId);

            Console.WriteLine("Requesting banner from url: " + url);

            NSUrlRequest request = new NSUrlRequest(new NSUrl(url), NSUrlRequestCachePolicy.ReloadIgnoringLocalAndRemoteCacheData, m_timeout);
            ((WebViewDelegate)this.Delegate).Clear();
            this.LoadRequest(request);
        }

        #region Callbacks

        private void CloseBanner()
        {
            Console.WriteLine("Closing banner");
            if (DidClosed != null)
                DidClosed(this, null);
        }

        private void EnterBanner()
        {
            Console.WriteLine("Leaving application");
            if (WillLeaveApplication != null)
                WillLeaveApplication(this, null);
        }

        private void BannerLoaded()
        {
            Console.WriteLine("Banner loaded");
            if (DidLoadedAd != null)
                DidLoadedAd(this, null);
        }

        private void BannerLoadError(NSError error)
        {
            Console.WriteLine("Banner error: " + error);
            if (DidFailWithError != null)
                DidFailWithError(this, error.ToString());
        }

        private void BannerServerError(string error)
        {
            Console.WriteLine("Banner error: " + error);
            if (DidFailWithError != null)
                DidFailWithError(this, error);
        }

        #endregion

        #region Helpers

        private static string UrlEncode(string source)
        {
            if (string.IsNullOrEmpty(source))
                return string.Empty;

            StringBuilder result = new StringBuilder(source.Length * 3);
            
            for (int i = 0; i < source.Length; ++i)
            {
                char thisChar = source[i];
                if (thisChar == ' ')
                    result.Append('+');
                else
                    if (thisChar == '.' || thisChar == '-' || thisChar == '_' || thisChar == '~' || 
                        (thisChar >= 'a' && thisChar <= 'z') ||
                        (thisChar >= 'A' && thisChar <= 'Z') ||
                        (thisChar >= '0' && thisChar <= '9')) 
                        result.Append(thisChar);
                else
                    result.AppendFormat("%{0,2:X2}", (int)thisChar);
            }
            return result.ToString();
        }

        private class WebViewDelegate : UIWebViewDelegate
        {
            private readonly HouseBannerView m_parent;
            private bool m_isAdLoaded;
            private bool m_shouldClose;

            public WebViewDelegate(HouseBannerView parent)
            {
                m_parent = parent;
            }

            public void Clear()
            {
                m_isAdLoaded = false;
                m_shouldClose = false;
            }

            public override void LoadingFinished(UIWebView webView)
            {
                if (!m_isAdLoaded && !m_shouldClose)
                    m_parent.BannerLoaded();
                
                m_isAdLoaded = true;
            }

            public override void LoadFailed(UIWebView webView, NSError error)
            {
                m_parent.BannerLoadError(error);
                m_shouldClose = true;
            }

            public override bool ShouldStartLoad(UIWebView webView, NSUrlRequest request, UIWebViewNavigationType navigationType)
            {
                try
                {
                    Console.WriteLine("Should Start load: " + request.Url);

                    if (request.Url.Scheme == "app4bro") 
                    {
                        m_shouldClose = true;
                        if (request.Url.Host != "200")
                            m_parent.BannerServerError(request.Url.Host);
                        else
                            m_parent.CloseBanner();
                        return false;
                    }
                    

                    if (navigationType == UIWebViewNavigationType.LinkClicked)
                    {
                        m_parent.EnterBanner();
                        UIApplication.SharedApplication.OpenUrl(request.Url);
                        return false;
                    }
                }
                catch(Exception ex)
                {
                    Console.WriteLine("Banner error: " + ex);
                    m_parent.CloseBanner();
                    return false;
                }
                
                return true;
            }
        }
        #endregion
    }
}

