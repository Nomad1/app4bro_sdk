#if USE_ADSJUMBO
using AdsJumbo;
using System;
using System.Diagnostics;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;

namespace Apps4Bro.Networks
{
    internal class AdsJumboNetwork : BaseNetwork
    {
        private BannerAd m_adsJumboAd;

        public override string Network
        {
            get { return "AdsJumbo"; }
        }

        public AdsJumboNetwork(AdManager manager)
            : base(manager)
        {

        }

        public override bool Show(AdWrapper wrapper, object data)
        {
            base.Show(wrapper, data);

            try
            {
                Panel bannerGrid = data as Panel;

                if (bannerGrid == null)
                    return false;

                BannerAd bannerAd = new BannerAd();
                bannerAd.AdUnitId = m_unitId;
                bannerAd.ApplicationId = m_appId;
                bannerAd.WidthAd = 728;
                bannerAd.HeightAd = 90;
                bannerAd.Position = "Right";
                bannerAd.VerticalAlignment = VerticalAlignment.Center;             // Vertical Alignment
                bannerAd.HorizontalAlignment = HorizontalAlignment.Left;         // Horizontal Alignment

                bannerAd.OnAdError += adsJumboAd_AdLoadingError;
                bannerAd.OnAdErrorNoAds += adsJumboAd_AdLoadingError;
                bannerAd.OnAdRefreshed += adsJumboAd_AdLoaded;

				bannerGrid.Visibility = Visibility.Visible;
				bannerGrid.Opacity = 1;
				bannerGrid.Children.Add(bannerAd);

                m_adsJumboAd = bannerAd;
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to init AdsJumbo advertising: " + ex);
                m_adManager.ReportManager.ReportEvent("JUMBO_ERROR", ex.ToString());
                m_adsJumboAd = null;
            }

            return true;
        }

        private void adsJumboAd_AdLoadingError(object sender, RoutedEventArgs e)
        {
            m_adManager.AdError(m_wrapper, "Error " + e);
        }

        private void adsJumboAd_AdLoaded(object sender, RoutedEventArgs e)
        {
            m_adManager.AdLoaded(m_wrapper);
        }

        private void adsJumboAd_AdClick(object sender, RoutedEventArgs e)
        {
            m_adManager.AdClicked(m_wrapper);
        }

        public override void Hide()
        {
            if (m_adsJumboAd != null)
            {
                m_adsJumboAd.Visibility = Visibility.Collapsed;
                m_adsJumboAd = null;
            }

            Panel bannerGrid = m_data as Panel;
            if (bannerGrid != null)
                bannerGrid.Children.Clear();

            GC.Collect();
        }

        public override void Display()
        {
            if (m_adsJumboAd != null)
            {
                m_adsJumboAd.Visibility = Visibility.Visible;
                m_adsJumboAd.Opacity = 1;
            }
        }
    }
}
#endif