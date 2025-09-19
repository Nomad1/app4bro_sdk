#if USE_MG
using MiracleGames;
using MiracleGames.Models;
using System;
using System.Diagnostics;


namespace Apps4Bro.Networks
{
	internal class MgInterNetwork : BaseNetwork
	{
		public override string Network
		{
			get { return "MGInter"; }
		}

		public MgInterNetwork(AdManager manager)
			: base(manager)
		{
		}

		public override bool Show(AdWrapper wrapper, object data)
		{
			base.Show(wrapper, data);

			try
			{
				m_adManager.RunOnUiThread(initAndShow);
				//initAndShow(); // block execution to show the ad on startup
			}
			catch (Exception ex)
			{
				Debug.WriteLine("Failed to init MG advertising: " + ex);
				m_adManager.ReportManager.ReportEvent("MG_EXCEPTION", ex.ToString());
				m_inited = AdNetworkInitStatus.Error;
			}

			return true;
		}

		private async void initAndShow()
		{
			if (m_inited == AdNetworkInitStatus.Unknown)
			{
				if (!ApplicationManager.SetupCompletedSuccessfully)
				{
					var initResult = await ApplicationManager.SetupAsync(m_appId);
					if (!initResult.ReturnValue)
					{
						Debug.WriteLine("Failed to init MG advertising: " + initResult.ErrorMessage);
						m_adManager.ReportManager.ReportEvent("MG_EXCEPTION", initResult.ErrorMessage);
						m_inited = AdNetworkInitStatus.Error;
						return;
					}

					Debug.WriteLine("MG advertising inited");
					m_inited = AdNetworkInitStatus.Inited;

					AdvertisingManager.ClickFullScreenAdEvent += oMg_AdClickEvent;
				}
			}

			try
			{
				var bannerAd = await AdvertisingManager.ShowAd(m_unitId, AdType.FullScreen, new FullScreenAdSettingOptions
				{
					DisplayCloseButton = true
				});

				if (bannerAd.ReturnValue) // Trigger the advert close event when closing the advert
				{
					m_adManager.AdShown(m_wrapper, bannerAd);
				}

			}
			catch (Exception ex)
			{
				Debug.WriteLine("Failed to init MG advertising: " + ex);
				m_adManager.ReportManager.ReportEvent("MG_EXCEPTION", ex.ToString());
			}
		}

		private void oMg_AdClickEvent(object sender, AdClickEventArgs e)
		{
			m_adManager.AdClicked(m_wrapper);
		}

		public override void Hide()
		{
		}

		public override void Display()
		{
		}
	}
}
#endif