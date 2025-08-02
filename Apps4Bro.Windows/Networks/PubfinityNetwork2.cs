#if USE_PUBFINITY2
using System;
using System.Diagnostics;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;

using Pubfinity.AdsSdk.Uwp;
using Pubfinity.AdsSdk.Uwp.AdUnits;
using Pubfinity.AdsSdk.Uwp.Events;
using ConsentDesk.CmpSdk.Uwp;

namespace Apps4Bro.Networks
{
    internal class PubfinityNetwork2 : BaseNetwork
    {
		private static bool s_inited = false;

        private BannerAd m_pubfinityAdControl;
        private Panel m_bannerGrid;

        public override string Network
        {
            get { return "Pubfinity"; }
        }

        public PubfinityNetwork2(AdManager manager)
            : base(manager)
        {
        }

        public override bool Show(AdWrapper wrapper, object data)
        {
            base.Show(wrapper, data);

			m_bannerGrid = data as Panel;

			try
			{
				if (!s_inited)
				{
					s_inited = true;

					PubfinityAdsConfig pubfinityInitParams = new PubfinityAdsConfig();
					pubfinityInitParams.Privacy = new PrivacyConfig();
					pubfinityInitParams.Privacy.IsCmpPresent = m_allIds.Length >= 3;
					pubfinityInitParams.Storage = new StorageConfig();

					//PubfinityAdsConfig consentDeskInitParams = new PubfinityAdsConfig();
					//consentDeskInitParams.Privacy = new PrivacyConfig();
					//consentDeskInitParams.Privacy.IsCmpPresent = true;
					//consentDeskInitParams.Storage = new StorageConfig();					
					//consentDeskInitParams.PrivacySettingsDigitalPropertiesUrl = "https://runserver.net/apps/";
					//consentDeskInitParams.PrivacySettingsLogoUrl = "https://runserver.net/app4bro/apps/logo_new.png";
					//consentDeskInitParams. = "Classic Solitaire"; // This is used for analytics 
					//consentDeskInitParams.OptionalPublisherName = "RunServer, LLC"; // This is used for analytics 
					if (m_allIds.Length >= 3)
						ConsentDeskCmp.Initialize(m_allIds[2]);


					PubfinityAds.OnSdkInitializationCompleted += onPubfinity_SDKStatus;
					PubfinityAds.Initialize(m_appId, pubfinityInitParams);
				} else
					m_adManager.RunOnUiThread(() => ShowBanner());
			}
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to init Pubfinity advertising: " + ex);
                m_adManager.ReportManager.ReportEvent("PUBFINITY_EXCEPTION", ex.ToString());
                m_pubfinityAdControl = null;
                s_inited = false;
            }

            return true;
        }

        private void ShowBanner()
        {
			try
			{
                if (m_pubfinityAdControl == null)
                {
                    BannerAd adControl = new BannerAd();
                    adControl.Width = 728;
                    adControl.Height = 90;
                    adControl.TagId = m_unitId;

                    adControl.VerticalAlignment = VerticalAlignment.Bottom;             // Vertical Alignment
                    adControl.HorizontalAlignment = HorizontalAlignment.Center;         // Horizontal Alignment

                    adControl.AdDisplayed += onPubfinity_AdImpressionEvent;
                    adControl.AdClicked += onPubfinity_AdClickEvent;
                    adControl.AdError += onPubfinity_AdErrorEvent;

                    if (m_pubfinityAdControl != null)
                        m_pubfinityAdControl.Reset();

                    m_bannerGrid.Children.Clear();
                    m_bannerGrid.Children.Add(adControl);

                    m_pubfinityAdControl = adControl;
                }

				m_pubfinityAdControl.LoadAndShow(BannerAd.RunMode.AutoRefresh);
			}
			catch (Exception ex)
			{
				Debug.WriteLine("Failed to show Pubfinity advertising: " + ex);
				m_adManager.ReportManager.ReportEvent("PUBFINITY_EXCEPTION", ex.ToString());
				m_pubfinityAdControl = null;
				s_inited = false;
			}
		}

		private void onPubfinity_AdErrorEvent(object sender, BaseAdEventArgs e)
        {
            if (e is AdErrorEventArgs)
				m_adManager.AdError(m_wrapper, "Error " + ((AdErrorEventArgs)e).ErrorMessage);
            else
			    m_adManager.AdError(m_wrapper, "Error " + e);
        }

        private void onPubfinity_AdClickEvent(object sender, BaseAdEventArgs e)
        {
            m_adManager.AdClicked(m_wrapper);
        }

        private void onPubfinity_AdImpressionEvent(object sender, BaseAdEventArgs e)
        {
            m_adManager.AdLoaded(m_wrapper);
        }

        private void onPubfinity_SDKStatus(object sender, SdkInitStatusEventArgs arg)
        {
            if (!arg.Initialized)
            {
                Debug.WriteLine("Failed to use Pubfinity advertising: " + arg.ErrorMessage);
                m_adManager.ReportManager.ReportEvent("PUBFINITY_ERROR", arg.ErrorMessage);
                Hide();
				s_inited = false;
            } else
				m_adManager.RunOnUiThread(() => ShowBanner());
		}

		public override void Hide()
        {
            m_adManager.RunOnUiThread(() =>
            {
                if (m_pubfinityAdControl != null)
                {                   
                    m_pubfinityAdControl.Reset();
                    m_pubfinityAdControl = null;
                }
            });
        }

        public override void Display()
        {
            m_adManager.RunOnUiThread(() =>
            {
                if (m_pubfinityAdControl != null)
                {
                    m_pubfinityAdControl.Visibility = Visibility.Visible;
                    m_pubfinityAdControl.Opacity = 1;
                }
            }
            );
        }
    }
}
#endif