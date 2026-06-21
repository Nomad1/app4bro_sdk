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
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.mediation.Adapter;
import com.google.android.gms.ads.mediation.InitializationCompleteCallback;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationConfiguration;
import com.google.android.gms.ads.mediation.MediationInterstitialAd;
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback;
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration;

import java.util.List;

public class GAMAdapter extends Adapter implements MediationInterstitialAd {

    private InterstitialAd interstitialAd;
    private MediationInterstitialAdCallback adCallback;

    @NonNull
    @Override
    public VersionInfo getVersionInfo() {
        // Defines the version of your adapter (0.0.1)
        return new VersionInfo(0, 0, 1);
    }

    @NonNull
    @Override
    public VersionInfo getSDKVersionInfo() {
        // Defines the version of the "3rd party SDK" (also 0.0.1 since you are the author)
        return new VersionInfo(0, 0, 1);
    }

    @Override
    public void initialize(@NonNull Context context, 
                           @NonNull InitializationCompleteCallback initializationCompleteCallback, 
                           @NonNull List<MediationConfiguration> list) {
        initializationCompleteCallback.onInitializationSucceeded();
    }

    @Override
    public void loadInterstitialAd(
            @NonNull MediationInterstitialAdConfiguration config,
            @NonNull MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback> loadCallback) {

        Bundle serverParameters = config.getServerParameters();
        String targetAdUnitId = serverParameters != null ? serverParameters.getString("parameter") : null;

        if (targetAdUnitId == null || targetAdUnitId.isEmpty()) {
            loadCallback.onFailure(new AdError(0, "Missing Target Ad Unit ID", "GAMAdapter"));
            return;
        }

        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(config.getContext(), targetAdUnitId, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd ad) {
                interstitialAd = ad;
                adCallback = loadCallback.onSuccess(GAMAdapter.this);

                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
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
                // Instantly triggers the next network down in your AdMob web waterfall
                loadCallback.onFailure(loadAdError);
            }
        });
    }

    @Override
    public void showAd(@NonNull Context context) {
        if (context instanceof Activity && interstitialAd != null) {
            interstitialAd.show((Activity) context);
        }
    }
}