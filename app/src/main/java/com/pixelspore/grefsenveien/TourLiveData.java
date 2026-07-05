package com.pixelspore.grefsenveien;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TourLiveData {

    final int stage;
    final int year;
    final boolean simulated;
    final int kmLeft;
    final int progressPct;
    final String avgSpeedKmh;
    final String estimatedFinishLocal;
    final List<TourLiveVirtualRider> virtualStandings;
    final List<TourLiveJersey> jerseys;
    final List<TourLiveTrackGroup> trackGroups;
    final List<TourLiveTrackGap> trackGaps;
    final List<TourLiveFieldGroup> fieldGroups;

    TourLiveData(int stage, int year, boolean simulated, int kmLeft, int progressPct,
            String avgSpeedKmh, String estimatedFinishLocal,
            @NonNull List<TourLiveVirtualRider> virtualStandings,
            @NonNull List<TourLiveJersey> jerseys,
            @NonNull List<TourLiveTrackGroup> trackGroups,
            @NonNull List<TourLiveTrackGap> trackGaps,
            @NonNull List<TourLiveFieldGroup> fieldGroups) {
        this.stage = stage;
        this.year = year;
        this.simulated = simulated;
        this.kmLeft = kmLeft;
        this.progressPct = progressPct;
        this.avgSpeedKmh = avgSpeedKmh;
        this.estimatedFinishLocal = estimatedFinishLocal;
        this.virtualStandings = virtualStandings;
        this.jerseys = jerseys;
        this.trackGroups = trackGroups;
        this.trackGaps = trackGaps;
        this.fieldGroups = fieldGroups;
    }

    @NonNull
    List<TourLiveVirtualRider> getTopVirtual(int count) {
        int limit = Math.min(count, virtualStandings.size());
        return new ArrayList<>(virtualStandings.subList(0, limit));
    }
}

final class TourLiveVirtualRider {
    final int virtualRank;
    final String rider;
    final boolean isNorwegian;
    final String yesterdayGap;
    final String virtualGap;
    final int rankChange;
    final boolean telemetryEstimated;

    TourLiveVirtualRider(int virtualRank, String rider, boolean isNorwegian,
            String yesterdayGap, String virtualGap, int rankChange, boolean telemetryEstimated) {
        this.virtualRank = virtualRank;
        this.rider = rider;
        this.isNorwegian = isNorwegian;
        this.yesterdayGap = yesterdayGap;
        this.virtualGap = virtualGap;
        this.rankChange = rankChange;
        this.telemetryEstimated = telemetryEstimated;
    }
}

final class TourLiveJersey {
    final String code;
    final String title;
    final String rider;
    final boolean isNorwegian;
    final int groupNumber;
    final boolean telemetryEstimated;

    TourLiveJersey(String code, String title, String rider, boolean isNorwegian,
            int groupNumber, boolean telemetryEstimated) {
        this.code = code;
        this.title = title;
        this.rider = rider;
        this.isNorwegian = isNorwegian;
        this.groupNumber = groupNumber;
        this.telemetryEstimated = telemetryEstimated;
    }
}

final class TourLiveTrackGroup {
    final int number;
    final float positionPct;

    TourLiveTrackGroup(int number, float positionPct) {
        this.number = number;
        this.positionPct = positionPct;
    }
}

final class TourLiveTrackGap {
    final float midPct;
    final String gapText;

    TourLiveTrackGap(float midPct, String gapText) {
        this.midPct = midPct;
        this.gapText = gapText;
    }
}

final class TourLiveFieldGroup {
    final int number;
    final String gapText;
    @Nullable
    final String gapToPreviousGroupText;
    final int riderCount;
    final List<String> jerseyCodes;
    final List<TourLiveDisplayName> displayNames;

    TourLiveFieldGroup(int number, String gapText, @Nullable String gapToPreviousGroupText,
            int riderCount, @NonNull List<String> jerseyCodes,
            @NonNull List<TourLiveDisplayName> displayNames) {
        this.number = number;
        this.gapText = gapText;
        this.gapToPreviousGroupText = gapToPreviousGroupText;
        this.riderCount = riderCount;
        this.jerseyCodes = jerseyCodes;
        this.displayNames = displayNames;
    }
}

final class TourLiveDisplayName {
    final String rider;
    final boolean isNorwegian;

    TourLiveDisplayName(String rider, boolean isNorwegian) {
        this.rider = rider;
        this.isNorwegian = isNorwegian;
    }
}
