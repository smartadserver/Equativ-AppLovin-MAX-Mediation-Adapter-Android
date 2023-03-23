package com.applovin.mediation.adapters;

import android.app.Activity;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.model.SASReward;
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager;
import com.smartadserver.android.library.ui.SASAdView;
import com.smartadserver.android.library.ui.SASBannerView;
import com.smartadserver.android.library.ui.SASInterstitialManager;
import com.smartadserver.android.library.util.SASConfiguration;
import com.smartadserver.android.library.util.SASLibraryInfo;
import com.smartadserver.android.library.util.SASUtil;

public class EquativMediationAdapter extends MediationAdapterBase implements MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter {

    @Nullable
    private SASBannerView bannerView = null;

    @Nullable
    private SASInterstitialManager interstitialManager = null;

    @Nullable
    private SASRewardedVideoManager rewardedVideoManager = null;

    public EquativMediationAdapter(AppLovinSdk appLovinSdk) {
        super(appLovinSdk);
    }

    @Override
    public void initialize(MaxAdapterInitializationParameters maxAdapterInitializationParameters, Activity activity, OnCompletionListener onCompletionListener) {
        SASConfiguration.getSharedInstance().setPrimarySdk(false);
        onCompletionListener.onCompletion(InitializationStatus.DOES_NOT_APPLY, null);
    }

    @Override
    public String getSdkVersion() {
        return SASLibraryInfo.getSharedInstance().getVersion();
    }

    @Override
    public String getAdapterVersion() {
        return "1.0";
    }

    @Override
    public void onDestroy() {
        if (bannerView != null) {
            bannerView.reset();
            bannerView = null;
        }

        if (interstitialManager != null) {
            interstitialManager.reset();
            interstitialManager = null;
        }

        if (rewardedVideoManager != null) {
            rewardedVideoManager.reset();
            interstitialManager = null;
        }
    }

    /**
     * Convert the raw placement string to a SASAdPlacement model object.
     * The raw placement string is the value set in PlacementId section of your custom network
     * in the AppLovin platform.
     *
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
        SASConfiguration.getSharedInstance().configure(activity, (int) adPlacement.getSiteId());

        // Execute in UI thread
        SASUtil.getMainLooperHandler().post(() -> {
            if (bannerView == null) {
                bannerView = new SASBannerView(activity);
            }

            bannerView.setBannerListener(new SASBannerView.BannerListener() {
                @Override
                public void onBannerAdLoaded(@NonNull SASBannerView sasBannerView, @NonNull SASAdElement sasAdElement) {
                    maxAdViewAdapterListener.onAdViewAdLoaded(sasBannerView);
                    maxAdViewAdapterListener.onAdViewAdDisplayed();
                }

                @Override
                public void onBannerAdFailedToLoad(@NonNull SASBannerView sasBannerView, @NonNull Exception e) {
                    if (e instanceof SASNoAdToDeliverException) {
                        maxAdViewAdapterListener.onAdViewAdLoadFailed(MaxAdapterError.NO_FILL);
                    } if (e instanceof SASAdTimeoutException) {
                        maxAdViewAdapterListener.onAdViewAdLoadFailed(MaxAdapterError.TIMEOUT);
                    } else {
                        maxAdViewAdapterListener.onAdViewAdLoadFailed(MaxAdapterError.UNSPECIFIED);
                    }
                }

                @Override
                public void onBannerAdClicked(@NonNull SASBannerView sasBannerView) {
                    maxAdViewAdapterListener.onAdViewAdClicked();
                }

                @Override
                public void onBannerAdExpanded(@NonNull SASBannerView sasBannerView) {
                    maxAdViewAdapterListener.onAdViewAdExpanded();
                }

                @Override
                public void onBannerAdCollapsed(@NonNull SASBannerView sasBannerView) {
                    maxAdViewAdapterListener.onAdViewAdCollapsed();
                }

                @Override
                public void onBannerAdResized(@NonNull SASBannerView sasBannerView) {
                    // Do nothing, no equivalent
                }

                @Override
                public void onBannerAdClosed(@NonNull SASBannerView sasBannerView) {
                    maxAdViewAdapterListener.onAdViewAdHidden();
                }

                @Override
                public void onBannerAdVideoEvent(@NonNull SASBannerView sasBannerView, int i) {
                    // Do nothing, no equivalent
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
        SASConfiguration.getSharedInstance().configure(activity, (int) adPlacement.getSiteId());

        // Execute in UI thread
        SASUtil.getMainLooperHandler().post(() -> {
            if (interstitialManager != null) {
                interstitialManager.reset();
                interstitialManager = null;
            }

            interstitialManager = new SASInterstitialManager(activity, adPlacement);

            interstitialManager.setInterstitialListener(new SASInterstitialManager.InterstitialListener() {

                @Override
                public void onInterstitialAdLoaded(@NonNull SASInterstitialManager sasInterstitialManager, @NonNull SASAdElement sasAdElement) {
                    maxInterstitialAdapterListener.onInterstitialAdLoaded();
                }

                @Override
                public void onInterstitialAdFailedToLoad(@NonNull SASInterstitialManager sasInterstitialManager, @NonNull Exception e) {
                    if (e instanceof SASNoAdToDeliverException) {
                        maxInterstitialAdapterListener.onInterstitialAdLoadFailed(MaxAdapterError.NO_FILL);
                    } if (e instanceof SASAdTimeoutException) {
                        maxInterstitialAdapterListener.onInterstitialAdLoadFailed(MaxAdapterError.TIMEOUT);
                    } else {
                        maxInterstitialAdapterListener.onInterstitialAdLoadFailed(MaxAdapterError.UNSPECIFIED);
                    }
                }

                @Override
                public void onInterstitialAdShown(@NonNull SASInterstitialManager sasInterstitialManager) {
                    maxInterstitialAdapterListener.onInterstitialAdDisplayed();
                }

                @Override
                public void onInterstitialAdFailedToShow(@NonNull SASInterstitialManager sasInterstitialManager, @NonNull Exception e) {
                    maxInterstitialAdapterListener.onInterstitialAdDisplayFailed(MaxAdapterError.INTERNAL_ERROR);
                }

                @Override
                public void onInterstitialAdClicked(@NonNull SASInterstitialManager sasInterstitialManager) {
                    maxInterstitialAdapterListener.onInterstitialAdClicked();
                }

                @Override
                public void onInterstitialAdDismissed(@NonNull SASInterstitialManager sasInterstitialManager) {
                    maxInterstitialAdapterListener.onInterstitialAdHidden();
                }

                @Override
                public void onInterstitialAdVideoEvent(@NonNull SASInterstitialManager sasInterstitialManager, int i) {
                    // Do nothing, no equivalent
                }
            });

            interstitialManager.loadAd();
        });
    }

    @Override
    public void showInterstitialAd(MaxAdapterResponseParameters maxAdapterResponseParameters, Activity activity, MaxInterstitialAdapterListener maxInterstitialAdapterListener) {
        if (interstitialManager != null && interstitialManager.isShowable()) {
            interstitialManager.show();
        } else {
            maxInterstitialAdapterListener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
        }
    }

    /// Rewarded adapter implementation

    @Override
    public void loadRewardedAd(MaxAdapterResponseParameters maxAdapterResponseParameters, Activity activity, MaxRewardedAdapterListener maxRewardedAdapterListener) {
        SASAdPlacement adPlacement = convertToAdPlacement(maxAdapterResponseParameters.getThirdPartyAdPlacementId());

        if (adPlacement == null) {
            Log.e(mediationTag(), "The PlacementId found is not a valid Equativ placement. This placement should be formatted like: <site id>/<page id>/<format id>[/<targeting string> (optional)] (ex: 123/456/789/targetingString or 123/456/789). The invalid found PlacementId string: " + maxAdapterResponseParameters.getThirdPartyAdPlacementId());
            maxRewardedAdapterListener.onRewardedAdLoadFailed(new MaxAdapterError(MaxAdapterError.ERROR_CODE_INVALID_CONFIGURATION));
            return;
        }

        // Configure Smart Display SDK with siteid
        SASConfiguration.getSharedInstance().configure(activity, (int) adPlacement.getSiteId());

        // Execute on UI thread
        SASUtil.getMainLooperHandler().post(() -> {
            if (rewardedVideoManager != null) {
                rewardedVideoManager.reset();
                rewardedVideoManager = null;
            }

            rewardedVideoManager = new SASRewardedVideoManager(activity, adPlacement);

            rewardedVideoManager.setRewardedVideoListener(new SASRewardedVideoManager.RewardedVideoListener() {
                @Override
                public void onRewardedVideoAdLoaded(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull SASAdElement sasAdElement) {
                    maxRewardedAdapterListener.onRewardedAdLoaded();
                }

                @Override
                public void onRewardedVideoAdFailedToLoad(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull Exception e) {
                    if (e instanceof SASNoAdToDeliverException) {
                        maxRewardedAdapterListener.onRewardedAdLoadFailed(MaxAdapterError.NO_FILL);
                    } if (e instanceof SASAdTimeoutException) {
                        maxRewardedAdapterListener.onRewardedAdLoadFailed(MaxAdapterError.TIMEOUT);
                    } else {
                        maxRewardedAdapterListener.onRewardedAdLoadFailed(MaxAdapterError.UNSPECIFIED);
                    }
                }

                @Override
                public void onRewardedVideoAdShown(@NonNull SASRewardedVideoManager sasRewardedVideoManager) {
                    maxRewardedAdapterListener.onRewardedAdDisplayed();
                }

                @Override
                public void onRewardedVideoAdFailedToShow(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull Exception e) {
                    maxRewardedAdapterListener.onRewardedAdDisplayFailed(MaxAdapterError.INTERNAL_ERROR);
                }

                @Override
                public void onRewardedVideoAdClosed(@NonNull SASRewardedVideoManager sasRewardedVideoManager) {
                    maxRewardedAdapterListener.onRewardedAdHidden();
                }

                @Override
                public void onRewardReceived(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull SASReward sasReward) {
                    MaxReward maxReward = new MaxReward() {
                        @Override
                        public String getLabel() {
                            return sasReward.getCurrency();
                        }

                        @Override
                        public int getAmount() {
                            return (int) sasReward.getAmount();
                        }
                    };

                    maxRewardedAdapterListener.onUserRewarded(maxReward);
                }

                @Override
                public void onRewardedVideoAdClicked(@NonNull SASRewardedVideoManager sasRewardedVideoManager) {
                    maxRewardedAdapterListener.onRewardedAdClicked();
                }

                @Override
                public void onRewardedVideoEvent(@NonNull SASRewardedVideoManager sasRewardedVideoManager, int eventId) {
                    switch (eventId) {
                        case SASAdView.VideoEvents.VIDEO_START:
                            maxRewardedAdapterListener.onRewardedAdVideoStarted();
                            break;

                        case SASAdView.VideoEvents.VIDEO_COMPLETE:
                            maxRewardedAdapterListener.onRewardedAdVideoCompleted();
                            break;
                    }
                }

                @Override
                public void onRewardedVideoEndCardDisplayed(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull ViewGroup viewGroup) {
                    // Do nothing, no equivalent
                }
            });

            rewardedVideoManager.loadRewardedVideo();
        });
    }

    @Override
    public void showRewardedAd(MaxAdapterResponseParameters maxAdapterResponseParameters, Activity activity, MaxRewardedAdapterListener maxRewardedAdapterListener) {
        if (rewardedVideoManager != null && rewardedVideoManager.hasRewardedVideo()) {
            rewardedVideoManager.showRewardedVideo();
        } else {
            maxRewardedAdapterListener.onRewardedAdDisplayFailed(MaxAdapterError.AD_NOT_READY);
        }
    }
}
