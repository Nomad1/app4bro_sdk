package net.runserver.apps4bro;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Locale;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.FormError;

import androidx.annotation.Nullable;

public final class Apps4BroSDK
{
    public static final int Version = 152;

    private static String s_advertisingId = "";
    /// Cached at init() — sent as `app=` analytics name in AdManagerUrl.
    private static String s_packageName = "";
    private static boolean s_inited = false;

    private static boolean s_adTrackingLimited = true;

    private static String s_platform = "unknown";

    public final static String App4BroTag = "app4bro";
    public final static String ReportUrl = "https://app4bro.runserver.net/app4bro/event.php?id=%1$s&app=%2$s&event=%3$s&param=%4$s&time=%5$d&eventid=%6$s";
    // /route/<App4Bro-ID>/ — path-embedded routing. Query params:
    //   id    = App4Bro app ID (same value as the path segment). Routing key.
    //   app   = analytics name (package name). Cosmetic.
    //   lang/sdk/os = metadata.
    //   did   = device advertising ID. Trailing; may be empty when SDK not inited.
    public final static String AdManagerUrl = "https://app4bro.runserver.net/route/%1$s/?id=%1$s&app=%2$s&lang=%3$s&sdk=%4$s&os=%5$s&did=%6$s";
    public final static String AdManagerUrlShort = "https://app4bro.runserver.net/route/%1$s/";

    // HouseAdUrl args (in positional order):
    //   1  id         — house zone ID (NOT the App4Bro app ID)
    //   2  app        — analytics name (package name — same convention as AdManagerUrl)
    //   3  brand      — device brand (Build.MANUFACTURER)
    //   4  model      — device model (Build.MODEL)
    //   5  operator   — carrier
    //   6  width, 7 height
    //   8  lang       — ISO 639-1 language code
    //   9  sdk        — Apps4BroSDK.Version
    //   10 os         — platform name
    //   11 osver      — OS version (Build.VERSION.RELEASE)
    //   12 appver     — host app versionName (from PackageInfo)
    //   13 did        — device advertising ID (trailing; may be empty)
    public final static String HouseAdUrl = "https://app4bro.runserver.net/app4bro/house.php?id=%1$s&app=%2$s&brand=%3$s&model=%4$s&operator=%5$s&width=%6$d&height=%7$d&lang=%8$s&sdk=%9$s&os=%10$s&osver=%11$s&appver=%12$s&did=%13$s";
    public final static int HouseAdTimeout = 10;

    public static String getAdvertisingId() throws Exception
    {
        if (!s_inited)
            throw new Exception("Apps4Bro SDK is not inited!");
        return s_advertisingId;
    }

    public static String getPlatform()
    {
        return s_platform;
    }

    public static boolean isAdTrackingLimited()
    {
        return s_adTrackingLimited;
    }

    public static void init(final Context context)
    {
        s_inited = true;
        s_platform = Build.MANUFACTURER.equals("Amazon") ? "amazon" : "android";
        s_packageName = context != null ? context.getPackageName() : "";

        if (isSystemLanguageUkrainian(context))
            return;

        if (context instanceof CardApplication)
        {
            ConsentManager.getInstance(context).showPrivacyOptionsForm(((CardApplication)context).getCurrentActivity(), new ConsentForm.OnConsentFormDismissedListener()
            {
                @Override
                public void onConsentFormDismissed(@Nullable FormError formError)
                {

                }
            });
        }

        // Initialize Google advertising id
        new Thread(() ->
        {
            try
            {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                s_adTrackingLimited = adInfo.isLimitAdTrackingEnabled();
                if (!s_adTrackingLimited)
                    s_advertisingId = URLEncoder.encode(adInfo.getId());
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }).start();

        // Initialize Google ads app-wide
        try
        {
            MobileAds.initialize(
                    context,
                    initializationStatus -> {
                    });
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private static boolean isSystemLanguageUkrainian(final Context context) {
        Locale currentLocale;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            currentLocale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            currentLocale = context.getResources().getConfiguration().locale;
        }
        return currentLocale.getLanguage().equals(new Locale("uk").getLanguage());
    }

//    @NonNull
    static String loadStreamText(InputStream stream)
    {
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 0x8000);

            StringBuilder result = new StringBuilder();
            String line = null;

            while ((line = reader.readLine()) != null)
            {
                result.append(line);
                // Log.d("Test", "Line: " + line);
            }

            return result.toString();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();

        }
        return "";
    }

    public static String getRequestUrl(String appId)
    {
        // New args (App4Bro ID in path + id=; analytics name in app=; did= trails):
        //   %1$s = App4Bro app ID
        //   %2$s = package name (analytics)
        //   %3$s = language code
        //   %4$s = SDK version
        //   %5$s = platform name
        //   %6$s = device advertising ID (may be empty)
        try
        {
            return String.format(Apps4BroSDK.AdManagerUrl,
                appId,
                s_packageName,
                Locale.getDefault().getLanguage(),
                Apps4BroSDK.Version,
                Apps4BroSDK.getPlatform(),
                s_advertisingId);
        }
        catch (Exception e)
        {
            Log.e(App4BroTag, "error: " + e.getMessage(), e);
        }

        return String.format(AdManagerUrlShort, appId);
    }
}