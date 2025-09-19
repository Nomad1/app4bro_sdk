#if USE_MG
using MiracleGames;
using MiracleGames.Models;
using System;
using System.Diagnostics;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;


namespace Apps4Bro.Networks
{
	internal class MgNetwork : BaseNetwork
	{
		private bool m_bannerShown;
		public override string Network
		{
			get { return "MG"; }
		}

		public MgNetwork(AdManager manager)
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

				initAndShow(bannerGrid);

			}
			catch (Exception ex)
			{
				Debug.WriteLine("Failed to init Pubfinity advertising: " + ex);
				m_adManager.ReportManager.ReportEvent("PUBFINITY_EXCEPTION", ex.ToString());
			}

			return true;
		}

		private async void initAndShow(Panel parent)
		{
			if (!ApplicationManager.SetupCompletedSuccessfully)
			{
				var initResult = await ApplicationManager.SetupAsync(m_appId);
				if (!initResult.ReturnValue)
				{
					Debug.WriteLine("Failed to init MG advertising: " + initResult.ErrorMessage);
					m_adManager.ReportManager.ReportEvent("MG_EXCEPTION", initResult.ErrorMessage);
					m_bannerShown = false;
				}

				AdvertisingManager.ClickBannerAdEvent += (o, args) =>//banner ad click event
				{
					m_adManager.AdClicked(m_wrapper);
				};

				//AdvertisingManager.UniversalGoogleAdEvent
			}

			var bannerAd = await AdvertisingManager.ShowAd(m_unitId, AdType.Banner, new BannerAdSettingOptions
			{
				DisplayCloseButton = true,//whether to enable the close button
										  //Control the position of display adverts
				HorizontalAlignment = HorizontalAlignment.Center,
				VerticalAlignment = VerticalAlignment.Bottom,
			});
			m_bannerShown = true;

			if (bannerAd.ReturnValue) // Trigger the advert close event when closing the advert
			{
				m_bannerShown = true;
			}


			//bannerGrid.Children.Add(adControl);
			//	m_pubfinityAdControl = adControl;
		}
		/*
		private void onPubfinity_AdErrorEvent(object sender, BaseAdEventArgs e)
        {
            m_adManager.AdError(m_wrapper, "Error " + e);
        }

        private void onPubfinity_AdClickEvent(object sender, BaseAdEventArgs e)
        {
            m_adManager.AdClicked(m_wrapper);
        }

        private void onPubfinity_AdImpressionEvent(object sender, BaseAdEventArgs e)
        {
            m_adManager.AdLoaded(m_wrapper);
        }*/


		public override void Hide()
		{
			if (m_bannerShown)
			{
				AdvertisingManager.ShowAd(string.Empty, AdType.Banner);
			}


			Panel bannerGrid = m_data as Panel;
			if (bannerGrid != null)
				bannerGrid.Children.Clear();
		}

		public override void Display()
		{
			//if (m_pubfinityAdControl != null)
			//{
			//    m_pubfinityAdControl.Visibility = Visibility.Visible;
			//    m_pubfinityAdControl.Opacity = 1;
			//}
		}
	}
}
#endif