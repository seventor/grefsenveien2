package com.pixelspore.grefsenveien;

final class TourStandingsRider {

    final int rank;
    final String rider;
    final String bib;
    final String team;
    final String nationality;
    final String time;
    final String gap;

    TourStandingsRider(int rank, String rider, String bib, String team, String nationality,
            String time, String gap) {
        this.rank = rank;
        this.rider = rider;
        this.bib = bib;
        this.team = team;
        this.nationality = nationality;
        this.time = time;
        this.gap = gap;
    }

    boolean isNorwegian() {
        return "NOR".equals(nationality);
    }
}
