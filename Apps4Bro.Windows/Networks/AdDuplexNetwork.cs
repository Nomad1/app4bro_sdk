#if USE_ADDUPLEX
using AdDuplex;
using AdDuplex.Common.Models;
using System;
using System.Diagnostics;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;

namespace Apps4Bro.Networks
{
    internal class AdDuplexNetwork : BaseNetwork
    {
        private AdControl m_adDuplexAd;

        public override string Network
        {
            get { return "AdDuplex"; }
        }

        public AdDuplexNetwork(AdManager manager)
            : base(manager)
        {

        }

        public override bool Show(AdWrapper wrapper, object data)
        {
            base.Show(wrapper, data);

            try
            {
                Panel bannerGrid = data as Panel;

                AdControl adDuplexAd = new AdControl();
                adDuplexAd.AdUnitId = m_unitId;
                adDuplexAd.AppKey = m_appId;
                adDuplexAd.AutoSize = true;
                adDuplexAd.RefreshInterval = 30;
                //   m_adDuplexAd.IsTest = true;
                adDuplexAd.Background = new Windows.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(0xff, 00, 0x90, 0));

                adDuplexAd.AdClick += adDuplexAd_AdClick;
                adDuplexAd.AdLoaded += adDuplexAd_AdLoaded;
                adDuplexAd.AdLoadingError += adDuplexAd_AdLoadingError;

                //bannerGrid.Visibility = Visibility.Visible;
                //bannerGrid.Opacity = 1;
                bannerGrid.Children.Add(adDuplexAd);

                m_adDuplexAd = adDuplexAd;
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to init ADDuplex advertising: " + ex);
                m_adDuplexAd = null;
            }

            return true;
        }

        private void adDuplexAd_AdLoadingError(object sender, AdLoadingErrorEventArgs e)
        {
            m_adManager.AdError(m_wrapper, "Error " + e.Error);
        }

        private void adDuplexAd_AdLoaded(object sender, AdDuplex.Banners.Models.BannerAdLoadedEventArgs e)
        {
            m_adManager.AdLoaded(m_wrapper);
        }

        private void adDuplexAd_AdClick(object sender, AdDuplex.Banners.Models.AdClickEventArgs e)
        {
            m_adManager.AdClicked(m_wrapper);
        }

        public override void Hide()
        {
            if (m_adDuplexAd != null)
            {
                m_adDuplexAd.Visibility = Visibility.Collapsed;
                m_adDuplexAd = null;
            }

            Panel bannerGrid = m_data as Panel;
            if (bannerGrid != null)
                bannerGrid.Children.Clear();

            GC.Collect();
        }

        public override void Display()
        {
            if (m_adDuplexAd != null)
            {
                m_adDuplexAd.Visibility = Visibility.Visible;
                m_adDuplexAd.Opacity = 1;
            }
        }
    }
}
#endif