#if USE_PUBFINITY
using System;
using System.Diagnostics;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;

using PubfinitySDK;
using PubfinitySDK.UI;

namespace Apps4Bro.Networks
{
    internal class PubfinityNetwork : BaseNetwork
    {
		private static bool s_inited = false;

        private BannerControl m_pubfinityAdControl;

        public override string Network
        {
            get { return "Pubfinity"; }
        }

        public PubfinityNetwork(AdManager manager)
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

                if (!s_inited && !Pubfinity.GetInstance().HasInitializationFinishedSuccessfully())
                {
                    PubfinitySDKCoreInitializationParameters pubfinityInitParams = new PubfinitySDKCoreInitializationParameters();

                    PubfinitySDKConsentDeskInitializationParameters consentDeskInitParams = new PubfinitySDKConsentDeskInitializationParameters();
                    consentDeskInitParams.PrivacySettingsPublisherCountryCode = "US";
                    consentDeskInitParams.PrivacySettingsDigitalPropertiesUrl = "https://runserver.net/apps/";
                    consentDeskInitParams.PrivacySettingsLogoUrl = "https://runserver.net/app4bro/apps/logo_new.png";
                    consentDeskInitParams.OptionalAppName = "Classic Solitaire"; // This is used for analytics 
                    consentDeskInitParams.OptionalPublisherName = "RunServer, LLC"; // This is used for analytics 

                    pubfinityInitParams.ConsentDeskInitializationParameters = consentDeskInitParams;

                    pubfinityInitParams.AppKey = m_appId;

                    Pubfinity.GetInstance().StatusEvent += onPubfinity_SDKStatus;
                    Pubfinity.GetInstance().Initialize(pubfinityInitParams);

					s_inited = true;
				}

                BannerControl adControl = new BannerControl();
                adControl.Width = 728;
                adControl.Height = 90;
                adControl.VerticalAlignment = VerticalAlignment.Bottom;             // Vertical Alignment
                adControl.HorizontalAlignment = HorizontalAlignment.Center;         // Horizontal Alignment

                adControl.AdImpressionEvent += onPubfinity_AdImpressionEvent;
                adControl.AdClickedEvent += onPubfinity_AdClickEvent;
                adControl.AdErrorEvent += onPubfinity_AdErrorEvent;

                //bannerGrid.Visibility = Visibility.Visible;
                //bannerGrid.Opacity = 1;
                bannerGrid.Children.Add(adControl);

                m_pubfinityAdControl = adControl;

                adControl.LoadAndShowAd(m_unitId, 728, 90, true);
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to init Pubfinity advertising: " + ex);
                m_adManager.ReportManager.ReportEvent("PUBFINITY_EXCEPTION", ex.ToString());
                m_pubfinityAdControl = null;
            }

            return true;
        }

        private void onPubfinity_AdErrorEvent(object sender, BannerErrorEventArg e)
        {
            m_adManager.AdError(m_wrapper, "Error " + e.ErrorMessage);
        }

        private void onPubfinity_AdClickEvent(object sender, EventArg e)
        {
            m_adManager.AdClicked(m_wrapper);
        }

        private void onPubfinity_AdImpressionEvent(object sender, EventArg e)
        {
            m_adManager.AdLoaded(m_wrapper);
        }

        private void onPubfinity_SDKStatus(object sender, PubfinityStatus arg)
        {
            if (arg.GetOptionalError() != null)
            {
                Debug.WriteLine("Failed to use Pubfinity advertising: " + arg.GetOptionalError().ErrorMessage);
                m_adManager.ReportManager.ReportEvent("PUBFINITY_ERROR", arg.GetOptionalError().ErrorMessage);
                m_pubfinityAdControl = null;
            }
        }

        public override void Hide()
        {
            if (m_pubfinityAdControl != null)
            {
                m_pubfinityAdControl.Visibility = Visibility.Collapsed;
                m_pubfinityAdControl.SuspendAd();
                m_pubfinityAdControl = null;
            }

            Panel bannerGrid = m_data as Panel;
            if (bannerGrid != null)
                bannerGrid.Children.Clear();
        }

        public override void Display()
        {
            if (m_pubfinityAdControl != null)
            {
                m_pubfinityAdControl.Visibility = Visibility.Visible;
                m_pubfinityAdControl.Opacity = 1;
            }
        }
    }
}
#endif