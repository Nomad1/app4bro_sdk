using System;
using System.Diagnostics;
using System.Globalization;

using Windows.ApplicationModel;
using Windows.Graphics.Display;
using Windows.System.Profile;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;

#if USE_WEBVIEW2
using Microsoft.Web.WebView2.Core;

using WebView2 = Microsoft.UI.Xaml.Controls.WebView2;
#endif

namespace Apps4Bro.Networks
{
    internal class HouseInterNetwork : BaseNetwork
    {
        private InterstitialView m_interstitialView;
        private bool m_loaded;
        private bool m_dismissed;
        private Type m_initialPageType;

        /// <summary>
        /// Fires once when the ad has been shown and is now closed (close button or click-out).
        /// Does NOT fire on cascade-relevant failures (404, navigation error) — those go through AdError.
        /// </summary>
        public event EventHandler Dismissed;

        public override string Network
        {
            get { return "HouseInter"; }
        }

        public HouseInterNetwork(AdManager manager)
            : base(manager)
        {
        }

        public override bool Show(AdWrapper wrapper, object data)
        {
            base.Show(wrapper, data);

            m_loaded = false;
            m_dismissed = false;
            m_initialPageType = GetCurrentPageType();

            try
            {
                m_inited = AdNetworkInitStatus.Initializing;
                m_adManager.RunOnUiThread(initAndShow);
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to init HouseInter advertising: " + ex);
                m_adManager.ReportManager.ReportEvent("HOUSE_EXCEPTION", ex.ToString());
                m_inited = AdNetworkInitStatus.Error;
            }

            return true;
        }

        public override void Display()
        {
            // No-op. HouseInter is hosting a Google ad tag inside the WebView; the page
            // signals "ready" when the tag has actually rendered, at which point the
            // WebView is self-shown from HandleMessage. There's no separate display step.
        }

        public override void Hide()
        {
            // Treated as a dismissal: external code (AdManager.HideAd, splash) is asking us to close.
            m_adManager.RunOnUiThread(() =>
            {
                HideInternal();
                FireDismissedOnce();
            });
        }

        private void initAndShow()
        {
            DisplayInformation displayInformation = DisplayInformation.GetForCurrentView();
            const bool banner = false;

            string url = string.Format(Apps4BroSDK.HouseAdUrl,
                m_unitId,
                m_adManager.AppId,
                "",
                AnalyticsInfo.VersionInfo.DeviceFamilyVersion,
                "",
                displayInformation.ScreenWidthInRawPixels,
                displayInformation.ScreenHeightInRawPixels,
                CultureInfo.CurrentCulture.TwoLetterISOLanguageName,
                Apps4BroSDK.Version,
                Apps4BroSDK.Platform,
                Apps4BroSDK.AdvertisingId,
                banner.ToString().ToLower());

            InitAndNavigate(url);
        }

        private void InitAndNavigate(string url)
        {
            try
            {
                m_interstitialView = new InterstitialView();
                m_interstitialView.OnClicked += (s, uri) => HandleNewWindow(uri);
                m_interstitialView.OnNotify += (s, msg) => HandleMessage(msg);
                m_interstitialView.OnError += (s, err) => HandleNavigationCompleted(false, err);

                m_inited = AdNetworkInitStatus.Inited;

                Debug.WriteLine("HouseInter requesting: " + url);
                m_interstitialView.Load(new Uri(url));
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to show HouseInter advertising: " + ex);
                m_adManager.ReportManager.ReportEvent("HOUSE_EXCEPTION", ex.ToString());
                HideInternal();
                m_inited = AdNetworkInitStatus.Error;
                m_adManager.AdError(m_wrapper, "WebView init failed: " + ex.Message);
            }
        }

        private void HandleMessage(string msg)
        {
            Debug.WriteLine("HouseInter web message: " + msg);

            switch (msg)
            {
                case "ready":
                    if (m_loaded)
                        break;
                    m_loaded = true;

                    if (m_initialPageType != null && GetCurrentPageType() != m_initialPageType)
                    {
                        // Foreground page changed while the Google tag was loading —
                        // splash already moved on. Don't pop the ad over whatever
                        // page is showing now; just discard silently.
                        Debug.WriteLine("HouseInter ready after page change; discarding");
                        HideInternal();
                        break;
                    }

                    if (m_interstitialView != null)
                        m_interstitialView.Show();
                    m_adManager.AdLoaded(m_wrapper);
                    break;

                case "404":
                    // Empty fill — cascade should try the next network.
                    HideInternal();
                    m_adManager.AdError(m_wrapper, "empty");
                    break;

                case "200":
                    // User closed the ad — fire Dismissed so the host can move on.
                    HideInternal();
                    FireDismissedOnce();
                    break;
            }
        }

        private void HandleNavigationCompleted(bool success, string errorStatus)
        {
            if (!success && !m_loaded)
            {
                Debug.WriteLine("HouseInter navigation failed: " + errorStatus);
                HideInternal();
                m_adManager.AdError(m_wrapper, "Navigation failed: " + errorStatus);
            }
        }

        private void HandleNewWindow(string uri)
        {
            m_adManager.AdClicked(m_wrapper);
            HideInternal();
            FireDismissedOnce();

            if (!string.IsNullOrEmpty(uri))
            {
                try
                {
                    _ = Windows.System.Launcher.LaunchUriAsync(new Uri(uri));
                }
                catch (Exception ex)
                {
                    Debug.WriteLine("HouseInter LaunchUriAsync failed for " + uri + ": " + ex);
                }
            }
        }

        private static Type GetCurrentPageType()
        {
            Frame frame = Window.Current != null ? Window.Current.Content as Frame : null;
            object content = frame != null ? frame.Content : null;
            return content != null ? content.GetType() : null;
        }

        private void HideInternal()
        {
            try
            {
                if (m_interstitialView == null)
                    return;

                m_interstitialView.Dispose();
                m_interstitialView = null;
            }
            catch (Exception ex)
            {
                Debug.WriteLine("HouseInter teardown error: " + ex);
            }
        }

        private void FireDismissedOnce()
        {
            if (m_dismissed)
                return;
            m_dismissed = true;

            EventHandler handler = Dismissed;
            if (handler != null)
                handler(this, EventArgs.Empty);
        }
    }
}
