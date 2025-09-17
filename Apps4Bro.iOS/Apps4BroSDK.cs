using System;
using System.Collections.Generic;
using System.Net;
using System.Runtime.Remoting.Contexts;
using Google.UserMessagingPlatform;
using UIKit;

#if __ANDROID__
using Android.Gms.Ads.Identifier;
using Android.Gms.Common;

#else
#endif

namespace Apps4Bro
{
    public enum Apps4BroSettings
    {
        None = 0,
        ShowBannerOnTop = 1,
        UseBannerSuperview = 2
    }

    public static class Apps4BroSDK
    {
        private static readonly string s_emptyId = "00000000-0000-0000-0000-000000000000";
        public static readonly int Version = 122;

        private static string s_advertisingId = s_emptyId;
        private static bool s_inited = false;

        private static bool s_useNonPersonalizedAds = false;

        private static string s_platform = "unknown";

        internal readonly static string ReportUrl = "https://app4bro.runserver.net/app4bro/event.php?id={0}&app={1}&event={2}&param={3}&time={4}&eventid={5}";
        internal readonly static string AdManagerUrl = "https://app4bro.runserver.net/app4bro/ad.php?id={0}&app={1}";

        internal readonly static string HouseAdUrl = "https://app4bro.runserver.net/app4bro/house.php?id={0}&app={1}&brand={2}&model={3}&operator={4}&width={5}&height={6}&lang={7}&sdk={8}&os={9}&did={10}";
        internal readonly static int HouseAdTimeout = 10;

        internal readonly static IDictionary<Apps4BroSettings, object> Settings = new Dictionary<Apps4BroSettings, object>();

        public static string Platform
        {
            get { return s_platform; }
        }

        public static bool UseNonPersonalizedAds
        {
            get { return s_useNonPersonalizedAds; }
            set { s_useNonPersonalizedAds = value; }
        }

        public static void InitCMP(UIViewController viewController)
        {
            var requestParameters = new RequestParameters();
            requestParameters.TagForUnderAgeOfConsent = false;

#if DEBUG
            requestParameters.DebugSettings = new DebugSettings
            {
                Geography = DebugGeography.Eea
            };
#endif

            ConsentStatus consentStatus = ConsentStatus.NotRequired;

            ConsentInformation.SharedInstance.RequestConsentInfoUpdate(requestParameters, (error) =>
            {
                if (error == null)
                {
                    consentStatus = ConsentInformation.SharedInstance.ConsentStatus;
                    // Check user location
                    if (consentStatus == ConsentStatus.Unknown
                    || consentStatus == ConsentStatus.Required) // IsRequestLocationInEeaOrUnknown
                    {
                        // Load consent message
                        ConsentForm.Load((consentForm, loadError) =>
                        {
                            if (loadError == null)
                            {
                                // Show consent message
                                consentForm.Present(viewController);
                            }
                        });
                    }
                }
            });
        }


        public static string AdvertisingId
        {
            get
            {

                if (!s_inited)
                {
#if __IOS__
                    Init(null); // we can init iOS sdk without context. Not possible for Android
#else
                    throw new ApplicationException("Apps4Bro SDK is not inited!");
#endif

                }

                if (string.IsNullOrEmpty(s_advertisingId) || s_advertisingId.Equals(s_emptyId))
                    return string.Empty; // conserve the traffic 

                return s_advertisingId;
            }
        }

        public static void SetSettings(Apps4BroSettings setting, object value)
        {
            Settings[setting] = value;
        }

        public static void Init(object context)
        {
            s_inited = true;
            try
            {
#if __IOS__

                s_platform = "ios";
                if (AdSupport.ASIdentifierManager.SharedManager.IsAdvertisingTrackingEnabled)
                    s_advertisingId = WebUtility.UrlEncode(AdSupport.ASIdentifierManager.SharedManager.AdvertisingIdentifier.AsString());

#elif __ANDROID__
                
                s_platform = Adroid.OS.Build.MANUFACTURER == "Amazon" ? "amazon" : "android";
                
                System.Threading.Thread thread= new System.Threading.Thread(delegate()
                    {
                        try
                        {
                            AdvertisingIdClient.Info adInfo = AdvertisingIdClient.GetAdvertisingIdInfo((Android.Content.Context)context);
                            s_advertisingId = WebUtility.UrlEncode(adInfo.Id);
                        }
                        catch(Exception ex)
                        {
                            Console.Error.WriteLine("Failed to get Advertising ID: " + ex);
                        }
                    }
                );
                thread.Start();
#elif NETFX_CORE
                s_platform = "win";
#endif
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("Failed to get Advertising ID: " + ex);
            }
        }
    }
}

