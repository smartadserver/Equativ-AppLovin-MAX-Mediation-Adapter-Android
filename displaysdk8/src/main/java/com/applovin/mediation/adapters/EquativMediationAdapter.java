package com.applovin.mediation.adapters;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;
import com.equativ.displaysdk.ad.banner.SASBannerView;
import com.equativ.displaysdk.ad.interstitial.SASInterstitialManager;
import com.equativ.displaysdk.exception.SASException;
import com.equativ.displaysdk.model.SASAdInfo;
import com.equativ.displaysdk.model.SASAdPlacement;
import com.equativ.displaysdk.model.SASAdStatus;
import com.equativ.displaysdk.util.SASConfiguration;
import com.equativ.displaysdk.util.SASLibraryInfo;
import com.equativ.displaysdk.util.SASSecondaryImplementationInfo;

/**
 * This class is an implementation of all AppLovin mediation adapters compatible with Equativ Display SDK v8.0+.
 * If you are using an older version of the SDK (formerly Smart Display SDK), with a 7.x version, please check the adapter dedicated to this version here:
 * https://github.com/smartadserver/Equativ-AppLovin-MAX-Mediation-Adapter-Android/displaysdk7
 */
public class EquativMediationAdapter extends MediationAdapterBase implements MaxAdViewAdapter, MaxInterstitialAdapter {

    private static final String ADAPTER_VERSION = "2.0";

    @Nullable
    private SASBannerView bannerView = null;

    @Nullable
    private SASInterstitialManager interstitialManager = null;

    @NonNull
    private final Handler mainLooperHandler = new Handler(Looper.getMainLooper());

    public EquativMediationAdapter(AppLovinSdk appLovinSdk) {
        super(appLovinSdk);
    }

    @Override
    public void initialize(MaxAdapterInitializationParameters maxAdapterInitializationParameters, Activity activity, OnCompletionListener onCompletionListener) {
        SASConfiguration.INSTANCE.setSecondaryImplementationInfo(new SASSecondaryImplementationInfo(
                "AppLovin",
                AppLovinSdk.VERSION,
                ADAPTER_VERSION
        ));
        onCompletionListener.onCompletion(InitializationStatus.DOES_NOT_APPLY, null);
    }

    @Override
    public String getSdkVersion() {
        return SASLibraryInfo.INSTANCE.getVersion();
    }

    @Override
    public String getAdapterVersion() {
        return ADAPTER_VERSION;
    }

    @Override
    public void onDestroy() {
        if (bannerView != null) {
            bannerView.onDestroy();
            bannerView = null;
        }

        if (interstitialManager != null) {
            interstitialManager.onDestroy();
            interstitialManager = null;
        }
    }

    /**
     * Convert the raw placement string to a SASAdPlacement model object.
     * The raw placement string is the value set in PlacementId section of your custom network
     * in the AppLovin platform.
     * <p>
     * This raw placement string should validate the following format: <site id>/<page id>/<format id>[/<targeting string> (optional)]
     * ex: 123/456/789/targeting=string or 123/456/789
     *
     * @param rawAdPlacement The raw placement string that will be used to create SASAdPlacement object.
     * @return a SASAdPlacement instance.
     */
    private SASAdPlacement convertToAdPlacement(@Nullable String rawAdPlacement) {
        // Quick fail if the given string is null or empty.
        if (rawAdPlacement == null || rawAdPlacement.isEmpty()) {
            return null;
        }

        String[] subStrings = rawAdPlacement.split("/");

        int siteId;
        int pageId;
        int formatId;
        String targeting = null;

        try {
            siteId = Integer.parseInt(subStrings[0].trim());
            pageId = Integer.parseInt(subStrings[1].trim());
            formatId = Integer.parseInt(subStrings[2].trim());

            if (subStrings.length > 3) {
                targeting = subStrings[3];
            }

        } catch (Exception ignored) {
            return null;
        }

        return new SASAdPlacement(siteId, pageId, formatId, targeting);
    }

    /// Banner adapter implementation

    @Override
    public void loadAdViewAd(MaxAdapterResponseParameters maxAdapterResponseParameters, MaxAdFormat maxAdFormat, Activity activity, MaxAdViewAdapterListener maxAdViewAdapterListener) {
        SASAdPlacement adPlacement = convertToAdPlacement(maxAdapterResponseParameters.getThirdPartyAdPlacementId());

        if (adPlacement == null) {
            Log.e(mediationTag(), "The PlacementId found is not a valid Equativ placement. This placement should be formatted like: <site id>/<page id>/<format id>[/<targeting string> (optional)] (ex: 123/456/789/targetingString or 123/456/789). The invalid found PlacementId string: " + maxAdapterResponseParameters.getThirdPartyAdPlacementId());
            maxAdViewAdapterListener.onAdViewAdLoadFailed(new MaxAdapterError(MaxAdapterError.ERROR_CODE_INVALID_CONFIGURATION));
            return;
        }

        // Configure Smart Display SDK with siteid
        SASConfiguration.INSTANCE.configure(activity);

        // Execute in UI thread
        mainLooperHandler.post(() -> {
            if (bannerView == null) {
                bannerView = new SASBannerView(activity);
            }

            bannerView.setBannerListener(new SASBannerView.BannerListener() {
                @Override
                public void onBannerAdRequestClose() {
                    // Nothing to do
                }

                @Override
                public void onBannerAdLoaded(@NonNull SASAdInfo sasAdInfo) {
                    maxAdViewAdapterListener.onAdViewAdLoaded(bannerView);
                    maxAdViewAdapterListener.onAdViewAdDisplayed();
                }

                @Override
                public void onBannerAdFailedToLoad(@NonNull SASException e) {
                    if (e.getType() == SASException.Type.NO_AD) {
                        maxAdViewAdapterListener.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL);
                    } if (e.getType() == SASException.Type.TIMEOUT) {
                        maxAdViewAdapterListener.onAdViewAdLoadFailed(MaxAdapterError.TIMEOUT);
                    } else {
                        maxAdViewAdapterListener.onAdViewAdLoadFailed(MaxAdapterError.UNSPECIFIED);
                    }
                }

                @Override
                public void onBannerAdClicked() {
                    maxAdViewAdapterListener.onAdViewAdClicked();
                }
            });

            bannerView.loadAd(adPlacement);
        });
    }

    /// Interstitial adapter implementation

    @Override
    public void loadInterstitialAd(MaxAdapterResponseParameters maxAdapterResponseParameters, Activity activity, MaxInterstitialAdapterListener maxInterstitialAdapterListener) {
        SASAdPlacement adPlacement = convertToAdPlacement(maxAdapterResponseParameters.getThirdPartyAdPlacementId());

        if (adPlacement == null) {
            Log.e(mediationTag(), "The PlacementId found is not a valid Equativ placement. This placement should be formatted like: <site id>/<page id>/<format id>[/<targeting string> (optional)] (ex: 123/456/789/targetingString or 123/456/789). The invalid found PlacementId string: " + maxAdapterResponseParameters.getThirdPartyAdPlacementId());
            maxInterstitialAdapterListener.onInterstitialAdLoadFailed(new MaxAdapterError(MaxAdapterError.ERROR_CODE_INVALID_CONFIGURATION));
            return;
        }

        // Configure Smart Display SDK with siteid
        SASConfiguration.INSTANCE.configure(activity);

        // Execute in UI thread
        mainLooperHandler.post(() -> {
            if (interstitialManager != null) {
                interstitialManager.onDestroy();
                interstitialManager = null;
            }

            interstitialManager = new SASInterstitialManager(activity, adPlacement);

            interstitialManager.setInterstitialManagerListener(new SASInterstitialManager.InterstitialManagerListener() {

                @Override
                public void onInterstitialAdLoaded(@NonNull SASAdInfo sasAdInfo) {
                    maxInterstitialAdapterListener.onInterstitialAdLoaded();
                }

                @Override
                public void onInterstitialAdFailedToLoad(@NonNull SASException e) {
                    if (e.getType() == SASException.Type.NO_AD) {
                        maxInterstitialAdapterListener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL);
                    } if (e.getType() == SASException.Type.TIMEOUT) {
                        maxInterstitialAdapterListener.onInterstitialAdLoadFailed(MaxAdapterError.TIMEOUT);
                    } else {
                        maxInterstitialAdapterListener.onInterstitialAdLoadFailed(MaxAdapterError.UNSPECIFIED);
                    }
                }

                @Override
                public void onInterstitialAdShown() {
                    maxInterstitialAdapterListener.onInterstitialAdDisplayed();
                }

                @Override
                public void onInterstitialAdFailedToShow(@NonNull SASException e) {
                    maxInterstitialAdapterListener.onInterstitialAdDisplayFailed(MaxAdapterError.INTERNAL_ERROR);
                }

                @Override
                public void onInterstitialAdClosed() {
                    maxInterstitialAdapterListener.onInterstitialAdHidden();
                }

                @Override
                public void onInterstitialAdClicked() {
                    maxInterstitialAdapterListener.onInterstitialAdClicked();
                }
            });

            interstitialManager.loadAd();
        });
    }

    @Override
    public void showInterstitialAd(MaxAdapterResponseParameters maxAdapterResponseParameters, Activity activity, MaxInterstitialAdapterListener maxInterstitialAdapterListener) {
        if (interstitialManager != null && interstitialManager.getAdStatus() == SASAdStatus.READY) {
            interstitialManager.show();
        } else {
            maxInterstitialAdapterListener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
        }
    }
}
