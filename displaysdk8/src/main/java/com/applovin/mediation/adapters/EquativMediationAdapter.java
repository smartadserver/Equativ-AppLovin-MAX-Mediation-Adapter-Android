package com.applovin.mediation.adapters;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.sdk.AppLovinSdk;
import com.equativ.displaysdk.ad.banner.SASBannerView;
import com.equativ.displaysdk.ad.interstitial.SASInterstitialManager;
import com.equativ.displaysdk.ad.nativead.SASNativeAdView;
import com.equativ.displaysdk.ad.nativead.SASNativeAdViewBinder;
import com.equativ.displaysdk.exception.SASException;
import com.equativ.displaysdk.model.SASAdInfo;
import com.equativ.displaysdk.model.SASAdPlacement;
import com.equativ.displaysdk.model.SASAdStatus;
import com.equativ.displaysdk.model.SASNativeAdAssets;
import com.equativ.displaysdk.util.SASConfiguration;
import com.equativ.displaysdk.util.SASLibraryInfo;
import com.equativ.displaysdk.util.SASSecondaryImplementationInfo;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * This class is an implementation of all AppLovin mediation adapters compatible with Equativ Display SDK v8.0+.
 * If you are using an older version of the SDK (formerly Smart Display SDK), with a 7.x version, please check the adapter dedicated to this version here:
 * https://github.com/smartadserver/Equativ-AppLovin-MAX-Mediation-Adapter-Android/displaysdk7
 */
public class EquativMediationAdapter extends MediationAdapterBase implements MaxAdViewAdapter, MaxInterstitialAdapter {

    private static final String ADAPTER_VERSION = "2.1";

    @Nullable
    private SASBannerView bannerView = null;

    @Nullable
    private SASInterstitialManager interstitialManager = null;

    @Nullable
    private SASNativeAdView nativeAdView = null;

    @Nullable
    private EquativMaxNativeAd equativMaxNativeAd = null;

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

        if (nativeAdView != null) {
            nativeAdView.onDestroy();
            nativeAdView = null;
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

        // Configure Smart Display SDK
        SASConfiguration.INSTANCE.configure(activity);

        // Execute in UI thread
        mainLooperHandler.post(() -> {
            if (bannerView == null) {
                bannerView = new SASBannerView(activity);
            }

            bannerView.setBannerListener(new SASBannerView.BannerListener() {


                @Override
                public void onBannerAdCollapsed() {
                    maxAdViewAdapterListener.onAdViewAdCollapsed();
                }

                @Override
                public void onBannerAdExpanded() {
                    maxAdViewAdapterListener.onAdViewAdExpanded();
                }

                @Override
                public void onBannerAdAudioStart() {
                    // not supported by AppLovin
                }

                @Override
                public void onBannerAdAudioStop() {
                    // not supported by AppLovin
                }

                @Override
                public void onBannerAdRequestClose() {
                    // Nothing to do
                    maxAdViewAdapterListener.onAdViewAdHidden();
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

        // Configure Smart Display SDK
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
                public void onInterstitialAdAudioStop() {
                    // not supported by AppLovin
                }

                @Override
                public void onInterstitialAdAudioStart() {
                    // not supported by AppLovin
                }

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

    /// Native Ad Adapter Implementation

    @Override
    public void loadNativeAd(MaxAdapterResponseParameters maxAdapterResponseParameters,
                             Activity activity,
                             MaxNativeAdAdapterListener maxNativeAdAdapterListener) {
        SASAdPlacement adPlacement = convertToAdPlacement(maxAdapterResponseParameters.getThirdPartyAdPlacementId());

        if (adPlacement == null) {
            Log.e(mediationTag(), "The PlacementId found is not a valid Equativ placement. This placement should be formatted like: <site id>/<page id>/<format id>[/<targeting string> (optional)] (ex: 123/456/789/targetingString or 123/456/789). The invalid found PlacementId string: " + maxAdapterResponseParameters.getThirdPartyAdPlacementId());
            maxNativeAdAdapterListener.onNativeAdLoadFailed(new MaxAdapterError(MaxAdapterError.ERROR_CODE_INVALID_CONFIGURATION));
            return;
        }

        // Configure Smart Display SDK
        SASConfiguration.INSTANCE.configure(activity);

        // Clean up if needed
        if (equativMaxNativeAd != null) {
            equativMaxNativeAd.unregisterView();
            equativMaxNativeAd = null;
        }

        nativeAdView = new SASNativeAdView(activity);

        nativeAdView.setNativeAdListener(new SASNativeAdView.NativeAdListener() {
            @Override
            public void onNativeAdLoaded(@NonNull SASAdInfo adInfo, @NonNull SASNativeAdAssets nativeAdAssets) {

                // need to spawn a thread different from main thread to perform bitmap downloads
                Thread renderThread = new Thread(() -> {
                    Bitmap iconBitmap = null;
                    if (nativeAdAssets.getIconImage() != null) {
                        SASNativeAdAssets.ViewAsset iconAsset = nativeAdAssets.getIconImage();
                        if (iconAsset.getUrl() != null && !iconAsset.getUrl().isEmpty()) {
                            iconBitmap = EquativMediationAdapter.scaledBitmapFromUrl(
                                    iconAsset.getUrl(),
                                    iconAsset.getWidth() == null? 0 : iconAsset.getWidth(),
                                    iconAsset.getHeight() == null? 0 : iconAsset.getHeight());
                        }
                    }

                    Bitmap coverBitmap = null;
                    if (nativeAdAssets.getMainView() != null) {
                        SASNativeAdAssets.ViewAsset mainViewAsset = nativeAdAssets.getMainView();
                        if (mainViewAsset.getUrl() != null && !mainViewAsset.getUrl().isEmpty()) {
                            coverBitmap = EquativMediationAdapter.scaledBitmapFromUrl(
                                    mainViewAsset.getUrl(),
                                    mainViewAsset.getWidth() == null? 0 : mainViewAsset.getWidth(),
                                    mainViewAsset.getHeight() == null? 0 : mainViewAsset.getHeight());
                        }
                    }

                    Bitmap finalIconBitmap = iconBitmap;
                    Bitmap finalCoverBitmap = coverBitmap;

                    mainLooperHandler.post(() -> {
                        MaxNativeAd.MaxNativeAdImage iconImage = null;
                        if (finalIconBitmap != null) {
                            Drawable iconDrawable = new BitmapDrawable(activity.getResources(), finalIconBitmap);
                            iconImage = new MaxNativeAd.MaxNativeAdImage(iconDrawable);
                        }

                        ImageView coverImageView = null;
                        if (finalCoverBitmap != null) {
                            coverImageView = new ImageView(activity);
                            coverImageView.setImageBitmap(finalCoverBitmap);
                        }

                        MaxNativeAd.Builder maxNativeAdBuilder = new MaxNativeAd.Builder()
                                .setTitle(nativeAdAssets.getTitle())
                                .setBody(nativeAdAssets.getBody())
                                .setCallToAction(nativeAdAssets.getCallToAction())
                                .setStarRating(nativeAdAssets.getRating())
                                .setIcon(iconImage)
                                .setMediaView(coverImageView);

                        EquativMediationAdapter.this.equativMaxNativeAd =
                                new EquativMaxNativeAd(maxNativeAdBuilder, nativeAdView, maxNativeAdAdapterListener);

                        maxNativeAdAdapterListener.onNativeAdLoaded(equativMaxNativeAd, null);
                    });
                });

                renderThread.start();
            }

            @Override
            public void onNativeAdFailedToLoad(@NonNull SASException e) {
                if (e.getType() == SASException.Type.NO_AD) {
                    maxNativeAdAdapterListener.onNativeAdLoadFailed(MaxAdapterError.NO_FILL);
                } if (e.getType() == SASException.Type.TIMEOUT) {
                    maxNativeAdAdapterListener.onNativeAdLoadFailed(MaxAdapterError.TIMEOUT);
                } else {
                    maxNativeAdAdapterListener.onNativeAdLoadFailed(MaxAdapterError.UNSPECIFIED);
                }
            }

            @Override
            public void onNativeAdClicked() {
                maxNativeAdAdapterListener.onNativeAdClicked();
            }

            @Override
            public void onNativeAdRequestClose() {
                // not supported by Applovin
            }

            @Nullable
            @Override
            public SASNativeAdViewBinder onNativeAdViewBinderRequested(@NonNull SASNativeAdAssets sasNativeAdAssets) {
                return null;
            }
        });

        nativeAdView.loadAd(adPlacement);
    }

    @Nullable
    private static Bitmap scaledBitmapFromUrl(@Nullable String url, int targetWidth, int targetHeight) {
        Bitmap result = null;
        try {
            InputStream inputStream = (InputStream) new URL(url).getContent();
            result = BitmapFactory.decodeStream(inputStream);
            if (targetWidth > 0 && targetHeight > 0) {
                double bWidth = result.getWidth();
                double bHeight = result.getHeight();
                double resizeRatio = Math.min(targetWidth / bWidth, targetHeight / bHeight);

                result = Bitmap.createScaledBitmap(
                        result,
                        (int)(bWidth * resizeRatio),
                        (int)(bHeight * resizeRatio),
                        true
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static class EquativMaxNativeAd extends MaxNativeAd {

        @NonNull
        private final SASNativeAdView nativeAdView;

        @NonNull
        private final MaxNativeAdAdapterListener maxNativeAdAdapterListener;

        public EquativMaxNativeAd(@NonNull Builder builder,
                                  @NonNull SASNativeAdView nativeAdView,
                                  @NonNull MaxNativeAdAdapterListener maxNativeAdListener) {
            super(builder);
            this.nativeAdView = nativeAdView;
            this.maxNativeAdAdapterListener = maxNativeAdListener;
        }

        @Override
        public boolean prepareForInteraction(List<View> clickableView, ViewGroup container) {
            nativeAdView.trackMediationView(container);

            // add a proxy click listener on all specified clickable views to forward click to
            // the SASNativeAdView
            View.OnClickListener proxyListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    container.performClick();
                }
            };

            for (View v: clickableView) {
                v.setOnClickListener(proxyListener);
            }

            maxNativeAdAdapterListener.onNativeAdDisplayed(null);
            return true;
        }

        private void unregisterView() {
            nativeAdView.onDestroy();
        }
    }
}
