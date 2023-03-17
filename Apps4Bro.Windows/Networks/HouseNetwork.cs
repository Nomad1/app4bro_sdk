using System;

using System.Globalization;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml;
using System.Diagnostics;
using Windows.UI.Xaml.Input;
using Windows.UI.Core;
using Windows.ApplicationModel;
using Windows.System.Profile;
using Windows.Graphics.Display;

namespace Apps4Bro.Networks
{
    internal class HouseNetwork : BaseNetwork
    {
        private ExternalBanner m_bannerView;

        public override string Network
        {
            get { return "House"; }
        }

        public HouseNetwork(AdManager manager)
            : base(manager)
        {
        }

        public override bool Show(AdWrapper wrapper, object data)
        {
            base.Show(wrapper, data);

            Panel bannerGrid = data as Panel;

            if (bannerGrid == null)
                return false;

            var displayInformation = DisplayInformation.GetForCurrentView();

            bool banner = true;

            string url = string.Format(Apps4BroSDK.HouseAdUrl, m_unitId,
                Package.Current.Id,
                "",
                AnalyticsInfo.VersionInfo.DeviceFamilyVersion,
                "",
                displayInformation.ScreenWidthInRawPixels, displayInformation.ScreenHeightInRawPixels,
                CultureInfo.CurrentCulture.TwoLetterISOLanguageName, Apps4BroSDK.Version, Apps4BroSDK.Platform, Apps4BroSDK.AdvertisingId, banner.ToString().ToLower());

            m_bannerView = new ExternalBanner(bannerGrid, new Uri(url));

            m_bannerView.OnError += HandleDidFailWithError;
            //m_bannerView.o += HandleDidClosed;
            m_bannerView.OnClicked += HandleWillLeaveApplication;
            m_bannerView.OnLoaded += HandleDidLoadedAd;
            //m_bannerView.on += HandleDidShownAd;

            m_bannerView.Load();
            return true;
        }

        public override void Display()
        {
            if (m_bannerView != null)
                m_bannerView.Show();
        }

        public override void Hide()
        {
            if (m_bannerView != null)
            {
                m_bannerView.Dispose();
                m_bannerView = null;
            }
        }
       
        void HandleDidLoadedAd(object sender, EventArgs e)
        {
            m_adManager.AdLoaded(m_wrapper);
        }

        void HandleDidShownAd(object sender, EventArgs e)
        {
            m_adManager.AdShown(m_wrapper, m_bannerView);
        }

        void HandleWillLeaveApplication(object sender, string e)
        {
            m_adManager.AdClicked(m_wrapper);

            Hide();

            Windows.System.Launcher.LaunchUriAsync(new Uri(e));
        }

        void HandleDidClosed(object sender, EventArgs e)
        {
            Hide();
        }

        void HandleDidFailWithError(object sender, string e)
        {
            m_adManager.AdError(m_wrapper, "Error " + e);

            Hide();
        }

    }

    public class ExternalBanner : IDisposable
    {
        private Uri m_uri;
        private string m_text;
        private WebView m_webView;
        private readonly Panel m_container;
        private Uri m_clickOverrideUri;

        private event EventHandler m_onLoaded;
        private event EventHandler<string> m_onError;
        private event EventHandler<string> m_onClicked;
        private event EventHandler<string> m_onNotify;

        public Uri Uri
        {
            get { return m_uri; }
            set { m_uri = value; }
        }

        public Uri ClickOverrideUri
        {
            get { return m_clickOverrideUri; }
            set { m_clickOverrideUri = value; }
        }

        public string Text
        {
            get { return m_text; }
            set { m_text = value; }
        }

        public event EventHandler OnLoaded
        {
            add { m_onLoaded += value; }
            remove { m_onLoaded -= value; }
        }

        public event EventHandler<string> OnClicked
        {
            add { m_onClicked += value; }
            remove { m_onClicked -= value; }
        }

        public event EventHandler<string> OnNotify
        {
            add { m_onNotify += value; }
            remove { m_onNotify -= value; }
        }

        public event EventHandler<string> OnError
        {
            add { m_onError += value; }
            remove { m_onError -= value; }
        }

        #region Constructors

        public ExternalBanner(Panel container, Uri uri)
        {
            m_container = container;
            m_uri = uri;
            m_text = string.Empty;

            InitBannerView();
        }

        public ExternalBanner(Panel container, string text)
        {
            m_container = container;
            m_uri = null;
            m_text = text;

            InitBannerView();
        }

        public void Dispose()
        {
            if (m_webView == null)
                return;

            m_webView.Visibility = Visibility.Collapsed;

            if (m_container.Children.Contains(m_webView))
            {
                m_container.Children.Remove(m_webView);
            }
        }
        #endregion

        private void InitBannerView()
        {
            m_webView = new WebView();
            m_webView.HorizontalAlignment = HorizontalAlignment.Stretch;
            m_webView.VerticalAlignment = VerticalAlignment.Stretch;
            m_webView.Visibility = Visibility.Collapsed;
            m_container.Children.Add(m_webView);
        }

        public void Load()
        {
            m_webView.LoadCompleted += OnLoadCompleted;
            m_webView.NavigationFailed += OnNavigationFailed;
            m_webView.ScriptNotify += OnScriptNotify;
            m_webView.PointerEntered += OnPointerEntered;
            m_webView.PointerExited += OnPointerExited;

            if (m_clickOverrideUri != null)
            {
                m_webView.PointerPressed += OnPointerPressed;
            }

            if (m_uri != null)
            {
                Debug.WriteLine("Requesting banner from url: " + m_uri);

               // m_webView.AllowedScriptNotifyUris = new Uri[] { m_uri };
                m_webView.Navigate(m_uri);
            }
            else
            {
                Debug.WriteLine("Requesting banner text: " + m_text);

                m_webView.NavigateToString(m_text);
            }
        }

        public void Show()
        {
            m_webView.Visibility = Visibility.Visible;
        }

        private void OnPointerPressed(object sender, Windows.UI.Xaml.Input.PointerRoutedEventArgs e)
        {
            if (m_onClicked != null && m_clickOverrideUri != null)
                m_onClicked(this, m_clickOverrideUri.ToString());
        }

        #region Callbacks

        void OnScriptNotify(object sender, NotifyEventArgs e)
        {
            string value = e.Value;

            Debug.WriteLine("Got external notify: " + value);

            if (value.StartsWith("%%"))
            {
                if (m_onClicked != null)
                    m_onClicked(this, value.Substring(2));
                return;
            }

            if (m_onNotify != null)
                m_onNotify(this, value);
        }

        void OnNavigationFailed(object sender, WebViewNavigationFailedEventArgs e)
        {
            Debug.WriteLine("Navigation failed: " + e.WebErrorStatus);

            Dispose();

            if (m_onError != null)
                m_onError(this, "Load error");
        }

        async void OnLoadCompleted(object sender, Windows.UI.Xaml.Navigation.NavigationEventArgs e)
        {
            

#if DEBUG
            string retrieveHtml = "document.documentElement.outerHTML;";
            string html = await m_webView.InvokeScriptAsync("eval", new[] { retrieveHtml });
            Debug.WriteLine("HTML text: " + html);
#endif

            string setStyle = "document.body.style.overflow='hidden';document.body.style.margin='0';document.body.style.padding='0';";
            await m_webView.InvokeScriptAsync("eval", new[] { setStyle });

            if (m_clickOverrideUri == null)
            {
                string fixer = "for (var i = 0; i < document.links.length; i++) { document.links[i].onclick = function() { window.external.notify('%%' + this.href); return false; } }";
                //string fixer = "function navigating(){ window.external.notify('%%' + location.href); } window.onbeforeunload = navigating;";
                await m_webView.InvokeScriptAsync("eval", new[] { fixer });
            }
            if (m_onLoaded != null)
                m_onLoaded(m_webView, null);
        }

        #endregion

        void OnPointerExited(object sender, PointerRoutedEventArgs e)
        {
            Window.Current.CoreWindow.PointerCursor = new CoreCursor(Windows.UI.Core.CoreCursorType.Arrow, 2);
        }

        void OnPointerEntered(object sender, PointerRoutedEventArgs e)
        {
            Window.Current.CoreWindow.PointerCursor = new CoreCursor(Windows.UI.Core.CoreCursorType.Hand, 1);
        }

    }

}

