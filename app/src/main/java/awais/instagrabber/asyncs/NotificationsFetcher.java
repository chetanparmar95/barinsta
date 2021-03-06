package awais.instagrabber.asyncs;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import awais.instagrabber.BuildConfig;
import awais.instagrabber.interfaces.FetchListener;
import awais.instagrabber.models.NotificationModel;
import awais.instagrabber.models.enums.NotificationType;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.LocaleUtils;
import awais.instagrabber.utils.NetworkUtils;
import awaisomereport.LogCollector;

import static awais.instagrabber.utils.Utils.logCollector;

public final class NotificationsFetcher extends AsyncTask<Void, Void, List<NotificationModel>> {
    private static final String TAG = "NotificationsFetcher";

    private final FetchListener<List<NotificationModel>> fetchListener;

    public NotificationsFetcher(final FetchListener<List<NotificationModel>> fetchListener) {
        this.fetchListener = fetchListener;
    }

    @Override
    protected List<NotificationModel> doInBackground(final Void... voids) {
        List<NotificationModel> result = new ArrayList<>();
        final String url = "https://www.instagram.com/accounts/activity/?__a=1";
        try {
            final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept-Language", LocaleUtils.getCurrentLocale().getLanguage() + ",en-US;q=0.8");
            conn.connect();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                final JSONObject page = new JSONObject(NetworkUtils.readFromConnection(conn))
                        .getJSONObject("graphql")
                        .getJSONObject("user");
                final JSONObject ewaf = page.getJSONObject("activity_feed")
                                            .optJSONObject("edge_web_activity_feed");
                final JSONObject efr = page.optJSONObject("edge_follow_requests");
                JSONObject data;
                JSONArray media;
                if (ewaf != null
                        && (media = ewaf.optJSONArray("edges")) != null
                        && media.length() > 0
                        && media.optJSONObject(0).optJSONObject("node") != null) {
                    for (int i = 0; i < media.length(); ++i) {
                        data = media.optJSONObject(i).optJSONObject("node");
                        if (data == null) continue;
                        final String type = data.getString("__typename");
                        final NotificationType notificationType = NotificationType.valueOfType(type);
                        if (notificationType == null) continue;
                        final JSONObject user = data.getJSONObject("user");
                        result.add(new NotificationModel(
                                data.getString(Constants.EXTRAS_ID),
                                data.optString("text"), // comments or mentions
                                data.getLong("timestamp"),
                                user.getString("id"),
                                user.getString("username"),
                                user.getString("profile_pic_url"),
                                !data.isNull("media") ? data.getJSONObject("media").getString("shortcode") : null,
                                !data.isNull("media") ? data.getJSONObject("media").getString("thumbnail_src") : null, notificationType));
                    }
                }

                if (efr != null
                        && (media = efr.optJSONArray("edges")) != null
                        && media.length() > 0
                        && media.optJSONObject(0).optJSONObject("node") != null) {
                    for (int i = 0; i < media.length(); ++i) {
                        data = media.optJSONObject(i).optJSONObject("node");
                        if (data == null) continue;
                        result.add(new NotificationModel(
                                data.getString(Constants.EXTRAS_ID),
                                data.optString("full_name"),
                                0L,
                                data.getString(Constants.EXTRAS_ID),
                                data.getString("username"),
                                data.getString("profile_pic_url"),
                                null,
                                null, NotificationType.REQUEST));
                    }
                }
            }
            conn.disconnect();
        } catch (final Exception e) {
            if (logCollector != null)
                logCollector.appendException(e, LogCollector.LogFile.ASYNC_NOTIFICATION_FETCHER, "doInBackground");
            if (BuildConfig.DEBUG) Log.e(TAG, "", e);
        }
        return result;
    }

    @Override
    protected void onPreExecute() {
        if (fetchListener != null) fetchListener.doBefore();
    }

    @Override
    protected void onPostExecute(final List<NotificationModel> result) {
        if (fetchListener != null) fetchListener.onResult(result);
    }
}