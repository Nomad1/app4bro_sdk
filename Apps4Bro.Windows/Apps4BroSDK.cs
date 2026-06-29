using System;
using System.Collections.Generic;
using System.Diagnostics;
using Windows.Storage;


#if __ANDROID__
using Android.Gms.Ads.Identifier;
using Android.Gms.Common;
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
        public static readonly int Version = 130;

        private static string s_advertisingId = s_emptyId;
        private static bool s_inited = false;

        private static bool s_useNonPersonalizedAds = false;

        private static string s_platform = "unknown";

        internal readonly static string ReportUrl = "https://app4bro.runserver.net/app4bro/event.php?id={0}&app={1}&event={2}&param={3}&time={4}&eventid={5}";

        // /route/<App4Bro-ID>/ — path-embedded routing. Query params:
        //   id    = App4Bro app ID (same value as the path segment). Routing key.
        //   app   = analytics name (package family name). Cosmetic.
        //   lang/sdk/os = metadata.
        //   did   = device advertising ID. Trailing; may be empty.
        internal readonly static string AdManagerUrl = "https://app4bro.runserver.net/route/{0}/?id={0}&app={1}&lang={2}&sdk={3}&os={4}&did={5}";
        internal readonly static string AdManagerUrlShort = "https://app4bro.runserver.net/route/{0}/";

        // HouseAdUrl args:
        //   {0}  id         — house zone ID (NOT the App4Bro app ID)
        //   {1}  app        — analytics name (package family name — same as AdManagerUrl)
        //   {2}  brand
        //   {3}  model
        //   {4}  operator
        //   {5}  width, {6} height
        //   {7}  lang
        //   {8}  sdk        — Apps4BroSDK.Version
        //   {9}  os
        //   {10} osver      — OS version (AnalyticsInfo.VersionInfo.DeviceFamilyVersion)
        //   {11} appver     — host app version (Package.Current.Id.Version stringified)
        //   {12} did        — device advertising ID (trailing; may be empty)
        //   {13} noclose    — banner vs interstitial close-button flag
        internal readonly static string HouseAdUrl = "https://app4bro.runserver.net/app4bro/house.php?id={0}&app={1}&brand={2}&model={3}&operator={4}&width={5}&height={6}&lang={7}&sdk={8}&os={9}&osver={10}&appver={11}&did={12}&noclose={13}";
        internal readonly static int HouseAdTimeout = 10;

        internal static IDictionary<Apps4BroSettings, object> Settings = new Dictionary<Apps4BroSettings, object>();

        public static string Platform
        {
            get { return s_platform; }
        }

        public static bool UseNonPersonalizedAds
        {
            get { return s_useNonPersonalizedAds; }
            set { s_useNonPersonalizedAds = value; }
        }

        public static string AdvertisingId
        {
            get
            {

                if (!s_inited)
                {
#if __IOS__ || NETFX_CORE
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

                if (context == null)
                    throw new ApplicationException("Apps4Bro SDK is not explicitly inited!");
                
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

                object id;

                if (!ApplicationData.Current.LocalSettings.Values.TryGetValue("did", out id))
                {
                    s_advertisingId = Guid.NewGuid().ToString("N");
                    ApplicationData.Current.LocalSettings.Values["did"] = s_advertisingId;
                }
                else
                    s_advertisingId = id.ToString();

				s_platform = "win";
#endif
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Failed to get Advertising ID: " + ex);
            }
        }
    }
}

