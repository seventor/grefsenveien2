package com.pixelspore.grefsenveien;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

final class NewsItem {

    final String title;
    final String timestamp;
    final long publishedMs;
    @Nullable final String imageUrl;
    @Nullable Bitmap image;

    NewsItem(String title, String timestamp, long publishedMs, @Nullable String imageUrl) {
        this.title = title;
        this.timestamp = timestamp;
        this.publishedMs = publishedMs;
        this.imageUrl = imageUrl;
    }
}
