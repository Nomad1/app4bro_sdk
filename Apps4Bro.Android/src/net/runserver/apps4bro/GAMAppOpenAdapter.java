package net.runserver.apps4bro;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VersionInfo;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.mediation.Adapter;
import com.google.android.gms.ads.mediation.InitializationCompleteCallback;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationAppOpenAd;
import com.google.android.gms.ads.mediation.MediationAppOpenAdCallback;
import com.google.android.gms.ads.mediation.MediationAppOpenAdConfiguration;
import com.google.android.gms.ads.mediation.MediationConfiguration;

import java.util.List;

public class GAMAppOpenAdapter extends Adapter implements MediationAppOpenAd {

    private AppOpenAd appOpenAd;
    private MediationAppOpenAdCallback adCallback;

    @NonNull
    @Override
    public VersionInfo getVersionInfo() {
        return new VersionInfo(0, 0, 1);
    }

    @NonNull
    @Override
    public VersionInfo getSDKVersionInfo() {
        return new VersionInfo(0, 0, 1);
    }

    @Override
    public void initialize(@NonNull Context context, 
                           @NonNull InitializationCompleteCallback initializationCompleteCallback, 
                           @NonNull List<MediationConfiguration> list) {
        initializationCompleteCallback.onInitializationSucceeded();
    }

    // This is the specific method called by the mediation orchestrator for App Open Ads
    @Override
    public void loadAppOpenAd(
            @NonNull MediationAppOpenAdConfiguration config,
            @NonNull MediationAdLoadCallback<MediationAppOpenAd, MediationAppOpenAdCallback> loadCallback) {

        Bundle serverParameters = config.getServerParameters();
        String targetAdUnitId = serverParameters != null ? serverParameters.getString("parameter") : null;

        if (targetAdUnitId == null || targetAdUnitId.isEmpty()) {
            loadCallback.onFailure(new AdError(0, "Missing Target Ad Unit ID", "GAMAppOpenAdapter"));
            return;
        }

        AdRequest adRequest = new AdRequest.Builder().build();

        // Standard request to Google's backend for the secondary App Open Ad
        AppOpenAd.load(config.getContext(), targetAdUnitId, adRequest, new AppOpenAd.AppOpenAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull AppOpenAd ad) {
                appOpenAd = ad;
                adCallback = loadCallback.onSuccess(GAMAppOpenAdapter.this);

                // Map the nested ad's lifecycle events back to the parent mediation waterfall
                appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        if (adCallback != null) {
                            adCallback.onAdOpened();
                            adCallback.reportAdImpression();
                        }
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        if (adCallback != null) {
                            adCallback.onAdClosed();
                        }
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        if (adCallback != null) {
                            adCallback.onAdFailedToShow(adError);
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        if (adCallback != null) {
                            adCallback.reportAdClicked();
                        }
                    }
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                // Fails cleanly and tells the waterfall to try the next App Open ID
                loadCallback.onFailure(loadAdError);
            }
        });
    }

    // Called when the App Open Ad is actually ready to be displayed to the user
    @Override
    public void showAd(@NonNull Context context) {
        if (context instanceof Activity && appOpenAd != null) {
            appOpenAd.show((Activity) context);
        }
    }
}