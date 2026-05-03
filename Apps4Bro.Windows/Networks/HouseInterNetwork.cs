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
#if USE_WEBVIEW2
        private WebView2 m_webView;
#else
        private WebView m_webView;
#endif

        // Hosting our own Popup means we don't need a host-supplied Panel: the WebView
        // sits above the application's navigation Frame regardless of which page is
        // currently active, and survives splash → MainPage transitions.
        private Popup m_popup;
        private Grid m_adGrid;

        private bool m_loaded;
        private bool m_dismissed;

        // Page type the Frame was showing when Show() was called. If it changes
        // before Display() runs (typical: splash → MainPage), the ad is silently
        // discarded so it can't pop over a page that's already moved on.
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
                20/*m_unitId*/,
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

        private async void InitAndNavigate(string url)
        {
            try
            {
#if USE_WEBVIEW2
                m_webView = new WebView2();
#else
                m_webView = new WebView();
#endif
                m_webView.HorizontalAlignment = HorizontalAlignment.Stretch;
                m_webView.VerticalAlignment = VerticalAlignment.Stretch;
                m_webView.Visibility = Visibility.Collapsed;
                //m_webView.DefaultBackgroundColor = Windows.UI.Colors.Black;

                Windows.Foundation.Rect bounds = Window.Current.Bounds;

                m_adGrid = new Grid();
                m_adGrid.Width = bounds.Width;
                m_adGrid.Height = bounds.Height;
                m_adGrid.Children.Add(m_webView);

                m_popup = new Popup();
                m_popup.Child = m_adGrid;
                m_popup.IsOpen = true;

#if USE_WEBVIEW2
                await m_webView.EnsureCoreWebView2Async();

                CoreWebView2Settings settings = m_webView.CoreWebView2.Settings;
                settings.AreDefaultContextMenusEnabled = false;
                settings.AreDevToolsEnabled = false;
                settings.IsZoomControlEnabled = false;

                m_webView.CoreWebView2.WebMessageReceived += onHouse_WebMessageReceived;
                m_webView.CoreWebView2.NewWindowRequested += onHouse_NewWindowRequested;
                m_webView.CoreWebView2.NavigationStarting += onHouse_NavigationStarting;
                m_webView.CoreWebView2.NavigationCompleted += onHouse_NavigationCompleted;
#else
                m_webView.ScriptNotify += onHouse_ScriptNotify;
                m_webView.NewWindowRequested += onHouse_NewWindowRequested;
                m_webView.NavigationStarting += onHouse_NavigationStarting;
                m_webView.NavigationCompleted += onHouse_NavigationCompleted;
#endif

                m_inited = AdNetworkInitStatus.Inited;

                Debug.WriteLine("HouseInter requesting: " + url);
                m_webView.Source = new Uri(url);
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

#if USE_WEBVIEW2
        private void onHouse_WebMessageReceived(CoreWebView2 sender, CoreWebView2WebMessageReceivedEventArgs args)
        {
            string msg;
            try
            {
                msg = args.TryGetWebMessageAsString();
            }
            catch (Exception ex)
            {
                Debug.WriteLine("HouseInter web message read failed: " + ex);
                return;
            }
            HandleMessage(msg);
        }

        private void onHouse_NewWindowRequested(CoreWebView2 sender, CoreWebView2NewWindowRequestedEventArgs args)
        {
            args.Handled = true;
            HandleNewWindow(args.Uri);
        }

        private void onHouse_NavigationStarting(CoreWebView2 sender, CoreWebView2NavigationStartingEventArgs args)
        {
            if (TryHandleApp4BroNavigation(args.Uri))
                args.Cancel = true;
        }

        private void onHouse_NavigationCompleted(CoreWebView2 sender, CoreWebView2NavigationCompletedEventArgs args)
        {
            HandleNavigationCompleted(args.IsSuccess, args.WebErrorStatus.ToString());
        }
#else
        private void onHouse_ScriptNotify(object sender, NotifyEventArgs args)
        {
            HandleMessage(args.Value);
        }

        private void onHouse_NewWindowRequested(WebView sender, WebViewNewWindowRequestedEventArgs args)
        {
            args.Handled = true;
            HandleNewWindow(args.Uri != null ? args.Uri.ToString() : null);
        }

        private void onHouse_NavigationStarting(WebView sender, WebViewNavigationStartingEventArgs args)
        {
            string uri = args.Uri != null ? args.Uri.OriginalString : null;
            if (TryHandleApp4BroNavigation(uri))
                args.Cancel = true;
        }

        private void onHouse_NavigationCompleted(WebView sender, WebViewNavigationCompletedEventArgs args)
        {
            HandleNavigationCompleted(args.IsSuccess, args.WebErrorStatus.ToString());
        }
#endif

        private bool TryHandleApp4BroNavigation(string uri)
        {
            const string scheme = "app4bro://";
            if (string.IsNullOrEmpty(uri) || !uri.StartsWith(scheme, StringComparison.OrdinalIgnoreCase))
                return false;

            string message = uri.Substring(scheme.Length).TrimEnd('/');
            Debug.WriteLine("HouseInter intercepted custom-scheme navigation: " + uri);
            HandleMessage(message);
            return true;
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

                    if (m_webView != null)
                        m_webView.Visibility = Visibility.Visible;
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

        private async void HandleNewWindow(string uri)
        {
            m_adManager.AdClicked(m_wrapper);
            HideInternal();
            FireDismissedOnce();

            if (!string.IsNullOrEmpty(uri))
            {
                try
                {
                    await Windows.System.Launcher.LaunchUriAsync(new Uri(uri));
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
                if (m_webView == null)
                    return;

#if USE_WEBVIEW2
                if (m_webView.CoreWebView2 != null)
                {
                    m_webView.CoreWebView2.WebMessageReceived -= onHouse_WebMessageReceived;
                    m_webView.CoreWebView2.NewWindowRequested -= onHouse_NewWindowRequested;
                    m_webView.CoreWebView2.NavigationStarting -= onHouse_NavigationStarting;
                    m_webView.CoreWebView2.NavigationCompleted -= onHouse_NavigationCompleted;
                }
#else
                m_webView.ScriptNotify -= onHouse_ScriptNotify;
                m_webView.NewWindowRequested -= onHouse_NewWindowRequested;
                m_webView.NavigationStarting -= onHouse_NavigationStarting;
                m_webView.NavigationCompleted -= onHouse_NavigationCompleted;
#endif

                m_webView.Visibility = Visibility.Collapsed;

                if (m_adGrid != null && m_adGrid.Children.Contains(m_webView))
                    m_adGrid.Children.Remove(m_webView);

                if (m_popup != null)
                {
                    m_popup.IsOpen = false;
                    m_popup.Child = null;
                    m_popup = null;
                }
                m_adGrid = null;

#if USE_WEBVIEW2
                m_webView.Close();
#endif
                m_webView = null;
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
