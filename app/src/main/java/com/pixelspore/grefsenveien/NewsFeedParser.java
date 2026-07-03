package com.pixelspore.grefsenveien;

import android.text.Html;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NewsFeedParser {

    private static final Pattern SCHIBSTED_BLOCK_PATTERN = Pattern.compile(
            "<track-element[^>]*data-track-event=\"([^\"]*)\"[^>]*>(.*?)</track-element>",
            Pattern.DOTALL);
    private static final Pattern SCHIBSTED_TITLE_PATTERN =
            Pattern.compile("<h3 class=\"_title[^\"]*\">([^<]+)");
    private static final Pattern SCHIBSTED_TIME_PATTERN =
            Pattern.compile("<span data-nosnippet=\"true\">([^<]+)</span>");
    private static final Pattern SCHIBSTED_PAGE_IMAGE_PATTERN =
            Pattern.compile("<img[^>]+src=\"(https://akamai\\.vgc\\.no/[^\"]+)\"");

    private static final Pattern NRK_TITLE_PATTERN =
            Pattern.compile("<h3 class=\"kur-newsfeed__message-title\">([^<]+)");
    private static final Pattern NRK_TIME_PATTERN = Pattern.compile(
            "<time[^>]*class=\"kur-newsfeed__message-time\"[^>]*datetime=\"([^\"]+)\"[^>]*>\\s*([^<]+)");

    private NewsFeedParser() {}

    @NonNull
    static List<NewsItem> parseSchibsted(@NonNull String html) {
        List<String> pageImages = parsePageImages(html);
        List<NewsItem> items = new ArrayList<>();
        int imageIndex = 0;
        Matcher blockMatcher = SCHIBSTED_BLOCK_PATTERN.matcher(html);
        while (blockMatcher.find()) {
            try {
                String blockHtml = blockMatcher.group(2);
                Matcher titleMatcher = SCHIBSTED_TITLE_PATTERN.matcher(blockHtml);
                if (!titleMatcher.find()) {
                    continue;
                }
                String title = decodeHtml(titleMatcher.group(1).trim());
                String timestamp = "";
                Matcher timeMatcher = SCHIBSTED_TIME_PATTERN.matcher(blockHtml);
                if (timeMatcher.find()) {
                    timestamp = decodeHtml(timeMatcher.group(1).trim());
                }
                String imageUrl = extractCreativeUrl(blockMatcher.group(1));
                if (imageUrl == null && imageIndex < pageImages.size()) {
                    imageUrl = pageImages.get(imageIndex);
                }
                if (imageUrl != null) {
                    imageIndex++;
                }
                items.add(new NewsItem(title, timestamp,
                        NewsTimestampParser.parsePublishedMs(timestamp, null), normalizeImageUrl(imageUrl)));
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    @NonNull
    static List<NewsItem> parseNrk(@NonNull String html) {
        List<NewsItem> items = new ArrayList<>();
        Matcher matcher = NRK_TIME_PATTERN.matcher(html);
        Matcher titleMatcher = NRK_TITLE_PATTERN.matcher(html);
        List<String> titles = new ArrayList<>();
        while (titleMatcher.find()) {
            titles.add(titleMatcher.group(1));
        }
        int titleIndex = 0;
        while (matcher.find()) {
            if (titleIndex >= titles.size()) {
                break;
            }
            String isoDateTime = matcher.group(1);
            String timestamp = decodeHtml(matcher.group(2).trim());
            String title = decodeHtml(titles.get(titleIndex).trim());
            items.add(new NewsItem(title, timestamp,
                    NewsTimestampParser.parsePublishedMs(timestamp, isoDateTime), null));
            titleIndex++;
        }
        return items;
    }

    private static List<String> parsePageImages(String html) {
        List<String> images = new ArrayList<>();
        Matcher matcher = SCHIBSTED_PAGE_IMAGE_PATTERN.matcher(html);
        while (matcher.find()) {
            String url = normalizeImageUrl(matcher.group(1));
            if (url != null) {
                images.add(url);
            }
        }
        return images;
    }

    private static String extractCreativeUrl(@NonNull String trackEventAttr) {
        if (!trackEventAttr.contains("creativeURL")) {
            return null;
        }
        try {
            String json = trackEventAttr.replace("&quot;", "\"");
            JSONObject event = new JSONObject(json);
            JSONObject object = event.optJSONObject("object");
            if (object == null) {
                return null;
            }
            String url = object.optString("creativeURL", "");
            return url.isEmpty() ? null : url;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String normalizeImageUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        return url.replace("&amp;", "&");
    }

    private static List<String> parseAll(String html, Pattern pattern) {
        List<String> results = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            results.add(matcher.group(1));
        }
        return results;
    }

    private static String decodeHtml(String raw) {
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString();
    }
}
