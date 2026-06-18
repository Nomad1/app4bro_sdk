using System;
using System.Diagnostics;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;

#if USE_WEBVIEW2
using Microsoft.Web.WebView2.Core;
using WebView2 = Microsoft.UI.Xaml.Controls.WebView2;
#endif

namespace Apps4Bro.Networks
{
    public class InterstitialView : IDisposable
    {
#if USE_WEBVIEW2
        private WebView2 m_webView;
#else
        private WebView m_webView;
#endif

        private Popup m_popup;
        private Grid m_adGrid;
        private WindowSizeChangedEventHandler m_sizeChangedHandler;

        private volatile bool m_disposed;

        public event EventHandler OnLoaded;
        public event EventHandler<string> OnError;
        public event EventHandler<string> OnClicked;
        public event EventHandler<string> OnNotify;
        public event EventHandler OnDismissed;

        public InterstitialView()
        {
            InitInterstitialView();
        }

        private void InitInterstitialView()
        {
#if USE_WEBVIEW2
            m_webView = new WebView2();
#else
            m_webView = new WebView();
#endif
            m_webView.HorizontalAlignment = HorizontalAlignment.Stretch;
            m_webView.VerticalAlignment = VerticalAlignment.Stretch;
            m_webView.Visibility = Visibility.Collapsed;

            Windows.Foundation.Rect bounds = Window.Current.Bounds;

            m_webView.Width = bounds.Width;
            m_webView.Height = bounds.Height;

            m_adGrid = new Grid();
            m_adGrid.Width = bounds.Width;
            m_adGrid.Height = bounds.Height;
            m_adGrid.Children.Add(m_webView);

            m_popup = new Popup();
            m_popup.Child = m_adGrid;
            m_popup.IsOpen = true;

            m_sizeChangedHandler = OnWindowSizeChanged;
            Window.Current.SizeChanged += m_sizeChangedHandler;

#if USE_WEBVIEW2
            m_webView.CoreWebView2.WebMessageReceived += onWebMessageReceived;
            m_webView.CoreWebView2.NewWindowRequested += onNewWindowRequested;
            m_webView.CoreWebView2.NavigationStarting += onNavigationStarting;
            m_webView.CoreWebView2.NavigationCompleted += onNavigationCompleted;
#else
            m_webView.ScriptNotify += onScriptNotify;
            m_webView.NewWindowRequested += onNewWindowRequested;
            m_webView.NavigationStarting += onNavigationStarting;
            m_webView.NavigationCompleted += onNavigationCompleted;
#endif
        }

        public async void Load(Uri url)
        {
#if USE_WEBVIEW2
            await m_webView.EnsureCoreWebView2Async();
            if (m_disposed)
                return;

            m_webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            m_webView.CoreWebView2.Settings.AreDevToolsEnabled = false;
            m_webView.CoreWebView2.Settings.IsZoomControlEnabled = false;
            m_webView.CoreWebView2.Settings.IsStatusBarEnabled = false;
            m_webView.CoreWebView2.Settings.IsBuiltInErrorPageEnabled = false;
            m_webView.CoreWebView2.WebMessageReceived += onWebMessageReceived;
            m_webView.CoreWebView2.NewWindowRequested += onNewWindowRequested;
            m_webView.CoreWebView2.NavigationStarting += onNavigationStarting;
            m_webView.CoreWebView2.NavigationCompleted += onNavigationCompleted;
            m_webView.Source = url;
#else
            m_webView.Source = url;
#endif
        }

        public void Show()
        {
            m_webView.Visibility = Visibility.Visible;
        }

        private void OnWindowSizeChanged(object sender, Windows.UI.Core.WindowSizeChangedEventArgs args)
        {
            if (m_adGrid != null)
            {
                m_adGrid.Width = args.Size.Width;
                m_adGrid.Height = args.Size.Height;
            }
            if (m_webView != null)
            {
                m_webView.Width = args.Size.Width;
                m_webView.Height = args.Size.Height;
            }
        }

        public void Dispose()
        {
            if (m_disposed)
                return;
            m_disposed = true;

            if (m_sizeChangedHandler != null)
            {
                if (Window.Current != null)
                    Window.Current.SizeChanged -= m_sizeChangedHandler;
                m_sizeChangedHandler = null;
            }

            if (m_webView == null)
                return;

#if USE_WEBVIEW2
            m_webView.CoreWebView2.WebMessageReceived -= onWebMessageReceived;
            m_webView.CoreWebView2.NewWindowRequested -= onNewWindowRequested;
            m_webView.CoreWebView2.NavigationStarting -= onNavigationStarting;
            m_webView.CoreWebView2.NavigationCompleted -= onNavigationCompleted;
            m_webView.Close();
#else
            m_webView.ScriptNotify -= onScriptNotify;
            m_webView.NewWindowRequested -= onNewWindowRequested;
            m_webView.NavigationStarting -= onNavigationStarting;
            m_webView.NavigationCompleted -= onNavigationCompleted;
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
            m_webView = null;
        }

#if USE_WEBVIEW2
        private void onWebMessageReceived(CoreWebView2 sender, CoreWebView2WebMessageReceivedEventArgs args)
        {
            if (OnNotify != null)
                OnNotify(this, args.TryGetWebMessageAsString());
        }

        private void onNewWindowRequested(CoreWebView2 sender, CoreWebView2NewWindowRequestedEventArgs args)
        {
            args.Handled = true;
            if (OnClicked != null)
                OnClicked(this, args.Uri);
        }

        private void onNavigationStarting(CoreWebView2 sender, CoreWebView2NavigationStartingEventArgs args)
        {
            // Similar to WebView1 logic could be added here if needed
        }

        private void onNavigationCompleted(CoreWebView2 sender, CoreWebView2NavigationCompletedEventArgs args)
        {
            if (!args.IsSuccess && OnError != null)
                OnError(this, args.WebErrorStatus.ToString());
            else if (OnLoaded != null)
                OnLoaded(this, EventArgs.Empty);
        }
#else
        private void onScriptNotify(object sender, NotifyEventArgs args)
        {
            if (OnNotify != null)
                OnNotify(this, args.Value);
        }

        private void onNewWindowRequested(WebView sender, WebViewNewWindowRequestedEventArgs args)
        {
            args.Handled = true;
            if (OnClicked != null && args.Uri != null)
                OnClicked(this, args.Uri.ToString());
        }

        private void onNavigationStarting(WebView sender, WebViewNavigationStartingEventArgs args)
        {
        }

        private void onNavigationCompleted(WebView sender, WebViewNavigationCompletedEventArgs args)
        {
            if (!args.IsSuccess && OnError != null)
                OnError(this, args.WebErrorStatus.ToString());
            else if (OnLoaded != null)
                OnLoaded(this, EventArgs.Empty);
        }
#endif
    }
}
