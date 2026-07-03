package com.pixelspore.grefsenveien;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

final class TourStandingsData {

    final List<TourStandingsRider> standings;
    final List<TourStandingsRider> norwegians;
    final int stage;
    final int year;

    TourStandingsData(@NonNull List<TourStandingsRider> standings,
            @NonNull List<TourStandingsRider> norwegians, int stage, int year) {
        this.standings = standings;
        this.norwegians = norwegians;
        this.stage = stage;
        this.year = year;
    }

    @NonNull
    List<TourStandingsRider> getTopRiders(int count) {
        int limit = Math.min(count, standings.size());
        return new ArrayList<>(standings.subList(0, limit));
    }

    @NonNull
    List<TourStandingsRider> getNorwegiansOutsideTop(int topCount) {
        List<TourStandingsRider> result = new ArrayList<>();
        for (TourStandingsRider rider : norwegians) {
            if (rider.rank > topCount) {
                result.add(rider);
            }
        }
        return result;
    }
}
