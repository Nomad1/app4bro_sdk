package net.runserver.apps4bro;

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

public final class Apps4BroSDK
{
    public static final int Version = 152;

    private static String s_advertisingId = "";
    private static boolean s_inited = false;

    private static boolean s_adTrackingLimited = true;

    private static String s_platform = "unknown";

    public final static String App4BroTag = "app4bro";
    public final static String ReportUrl = "https://app4bro.runserver.net/app4bro/event.php?id=%1$s&app=%2$s&event=%3$s&param=%4$s&time=%5$d&eventid=%6$s";
    public final static String AdManagerUrl = "https://app4bro.runserver.net/app4bro/ad.php?id=%1$s&app=%2$s&lang=%3$s&sdk=%4$s&os=%5$s";
    public final static String AdManagerUrlShort = "https://app4bro.runserver.net/app4bro/ad.php?app=%1$s";

    public final static String HouseAdUrl = "https://app4bro.runserver.net/app4bro/house.php?id=%1$s&app=%2$s&brand=%3$s&model=%4$s&operator=%5$s&width=%6$d&height=%7$d&lang=%8$s&sdk=%9$s&os=%10$s&did=%11$s";
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
        try
        {
            return String.format(Apps4BroSDK.AdManagerUrl, s_advertisingId, appId, Locale.getDefault().getLanguage(), Apps4BroSDK.Version, Apps4BroSDK.getPlatform());
        }
        catch (Exception e)
        {
            Log.e(App4BroTag, "error: " + e.getMessage(), e);
        }

        return String.format(AdManagerUrlShort, appId);
    }
}