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
        private BannerView m_bannerView;

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
                m_adManager.AppId,
                "",
                AnalyticsInfo.VersionInfo.DeviceFamilyVersion,
                "",
                displayInformation.ScreenHeightInRawPixels, displayInformation.ScreenHeightInRawPixels,
 //               bannerGrid.ActualWidth, bannerGrid.ActualHeight,
                CultureInfo.CurrentCulture.TwoLetterISOLanguageName, Apps4BroSDK.Version, Apps4BroSDK.Platform, Apps4BroSDK.AdvertisingId, banner.ToString().ToLower());

            m_bannerView = new BannerView(bannerGrid, new Uri(url));

            m_bannerView.OnError += HandleDidFailWithError;
            //m_bannerView.o += HandleDidClosed;
            m_bannerView.OnClicked += HandleWillLeaveApplication;
            m_bannerView.OnNotify += HandleNotify;
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
       
        void HandleNotify(object sender, string e)
        {
            Debug.WriteLine("HouseBanner notify: " + e);
            switch (e)
            {
                case "ready":
                    m_adManager.AdLoaded(m_wrapper);
                    break;
                case "404":
                    m_adManager.AdError(m_wrapper, "empty");
                    Hide();
                    break;
            }
        }

        void HandleWillLeaveApplication(object sender, string e)
        {
            m_adManager.AdClicked(m_wrapper);

            Hide();

			_ = Windows.System.Launcher.LaunchUriAsync(new Uri(e));
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

}

