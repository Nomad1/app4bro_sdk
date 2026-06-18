using System;
using System.Diagnostics;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Input;
using Windows.UI.Core;

#if USE_WEBVIEW2
using Microsoft.Web.WebView2.Core;
using WebView2 = Microsoft.UI.Xaml.Controls.WebView2;
#endif

namespace Apps4Bro.Networks
{
    public class BannerView : IDisposable
    {
        private Uri m_uri;
        private string m_text;

#if USE_WEBVIEW2
        private WebView2 m_webView;
#else
        private WebView m_webView;
#endif

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

        public BannerView(Panel container, Uri uri)
        {
            m_container = container;
            m_uri = uri;
            m_text = string.Empty;

            InitBannerView();
        }

        public BannerView(Panel container, string text)
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
#if USE_WEBVIEW2
            m_webView.Close();
#endif
            m_webView = null;
        }
        #endregion

        private void InitBannerView()
        {
#if USE_WEBVIEW2
            m_webView = new WebView2();
#else
            m_webView = new WebView();
#endif
            m_webView.HorizontalAlignment = HorizontalAlignment.Stretch;
            m_webView.VerticalAlignment = VerticalAlignment.Stretch;
            m_webView.Visibility = Visibility.Collapsed;
            m_container.Children.Add(m_webView);
        }

        public async void Load()
        {
#if USE_WEBVIEW2
            await m_webView.EnsureCoreWebView2Async();
            m_webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            m_webView.CoreWebView2.NavigationCompleted += OnNavigationCompleted;
            m_webView.CoreWebView2.NavigationStarting += OnNavigationStarting;
            m_webView.CoreWebView2.WebMessageReceived += OnScriptNotify;
            m_webView.CoreWebView2.NewWindowRequested += OnNewWindowRequested2;
            
            // Pointer events are tricky for WebView2, they might need different handling
            // or CSS/JS-based clicking. Sticking to original logic as much as possible.
#else
            m_webView.LoadCompleted += OnLoadCompleted;
            m_webView.NavigationFailed += OnNavigationFailed;
            m_webView.ScriptNotify += OnScriptNotify;
            m_webView.PointerEntered += OnPointerEntered;
            m_webView.PointerExited += OnPointerExited;

            if (m_clickOverrideUri != null)
            {
                m_webView.PointerPressed += OnPointerPressed;
            }
            else
            {
                m_webView.NewWindowRequested += OnNewWindowRequested;
            }
#endif

            if (m_uri != null)
            {
                Debug.WriteLine("Requesting banner from url: " + m_uri);
#if USE_WEBVIEW2
                m_webView.Source = m_uri;
#else
                m_webView.Navigate(m_uri);
#endif
            }
            else
            {
                Debug.WriteLine("Requesting banner text: " + m_text);
#if USE_WEBVIEW2
                m_webView.NavigateToString(m_text);
#else
                m_webView.NavigateToString(m_text);
#endif
            }
        }

        public void Show()
        {
            m_webView.Visibility = Visibility.Visible;
        }

#if !USE_WEBVIEW2
        private void OnPointerPressed(object sender, Windows.UI.Xaml.Input.PointerRoutedEventArgs e)
        {
            if (m_onClicked != null && m_clickOverrideUri != null)
                m_onClicked(this, m_clickOverrideUri.ToString());
        }
#endif

        #region Callbacks

#if USE_WEBVIEW2
        private void OnScriptNotify(CoreWebView2 sender, CoreWebView2WebMessageReceivedEventArgs args)
        {
             if (m_onNotify != null)
                m_onNotify(this, args.TryGetWebMessageAsString());
        }

        private void OnNewWindowRequested2(CoreWebView2 sender, CoreWebView2NewWindowRequestedEventArgs args)
        {
            args.Handled = true;
            if (args.Uri != null && m_onClicked != null)
                m_onClicked(this, args.Uri);
        }

        private void OnNavigationStarting(CoreWebView2 sender, CoreWebView2NavigationStartingEventArgs args)
        {
            // Similar to WebView1 logic could be added here if needed
        }

        private void OnNavigationCompleted(CoreWebView2 sender, CoreWebView2NavigationCompletedEventArgs args)
        {
            if (!args.IsSuccess)
            {
                Debug.WriteLine("Navigation failed: " + args.WebErrorStatus);
                Dispose();
                if (m_onError != null)
                    m_onError(this, "Load error");
            }
            else
            {
                if (m_onLoaded != null)
                    m_onLoaded(m_webView, null);
            }
        }
#else
        void OnScriptNotify(object sender, NotifyEventArgs e)
        {
            string value = e.Value;

            Debug.WriteLine("Got external notify: " + value);

            if (m_onNotify != null)
                m_onNotify(this, value);
        }

        void OnNewWindowRequested(WebView sender, WebViewNewWindowRequestedEventArgs args)
        {
            args.Handled = true;

            if (args.Uri != null && m_onClicked != null)
                m_onClicked(this, args.Uri.ToString());
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
            string setStyle = "document.body.style.overflow='hidden';document.body.style.margin='0';document.body.style.padding='0';";
            await m_webView.InvokeScriptAsync("eval", new[] { setStyle });

            if (m_onLoaded != null)
                m_onLoaded(m_webView, null);
        }
#endif

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
