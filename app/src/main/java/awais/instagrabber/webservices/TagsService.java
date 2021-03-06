package awais.instagrabber.webservices;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import awais.instagrabber.models.FeedModel;
import awais.instagrabber.repositories.TagsRepository;
import awais.instagrabber.repositories.responses.PostsFetchResponse;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.ResponseBodyUtils;
import awais.instagrabber.utils.TextUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class TagsService extends BaseService {

    private static final String TAG = "TagsService";

    private static TagsService instance;

    private final TagsRepository webRepository;
    private final TagsRepository repository;

    private TagsService() {
        final Retrofit webRetrofit = getRetrofitBuilder()
                .baseUrl("https://www.instagram.com/")
                .build();
        webRepository = webRetrofit.create(TagsRepository.class);
        final Retrofit retrofit = getRetrofitBuilder()
                .baseUrl("https://i.instagram.com/")
                .build();
        repository = retrofit.create(TagsRepository.class);
    }

    public static TagsService getInstance() {
        if (instance == null) {
            instance = new TagsService();
        }
        return instance;
    }

    public void follow(@NonNull final String tag,
                       @NonNull final String csrfToken,
                       final ServiceCallback<Boolean> callback) {
        final Call<String> request = webRepository.follow(Constants.USER_AGENT,
                                                          csrfToken,
                                                          tag);
        request.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull final Call<String> call, @NonNull final Response<String> response) {
                final String body = response.body();
                if (body == null) {
                    callback.onFailure(new RuntimeException("body is null"));
                    return;
                }
                try {
                    final JSONObject jsonObject = new JSONObject(body);
                    final String status = jsonObject.optString("status");
                    callback.onSuccess(status.equals("ok"));
                } catch (JSONException e) {
                    Log.e(TAG, "onResponse: ", e);
                }
            }

            @Override
            public void onFailure(@NonNull final Call<String> call, @NonNull final Throwable t) {
                // Log.e(TAG, "onFailure: ", t);
                callback.onFailure(t);
            }
        });
    }

    public void unfollow(@NonNull final String tag,
                         @NonNull final String csrfToken,
                         final ServiceCallback<Boolean> callback) {
        final Call<String> request = webRepository.unfollow(Constants.USER_AGENT,
                                                            csrfToken,
                                                            tag);
        request.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull final Call<String> call, @NonNull final Response<String> response) {
                final String body = response.body();
                if (body == null) {
                    callback.onFailure(new RuntimeException("body is null"));
                    return;
                }
                try {
                    final JSONObject jsonObject = new JSONObject(body);
                    final String status = jsonObject.optString("status");
                    callback.onSuccess(status.equals("ok"));
                } catch (JSONException e) {
                    Log.e(TAG, "onResponse: ", e);
                }
            }

            @Override
            public void onFailure(@NonNull final Call<String> call, @NonNull final Throwable t) {
                // Log.e(TAG, "onFailure: ", t);
                callback.onFailure(t);
            }
        });
    }

    public void fetchPosts(@NonNull final String tag,
                           final String maxId,
                           final ServiceCallback<PostsFetchResponse> callback) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        if (!TextUtils.isEmpty(maxId)) {
            builder.put("max_id", maxId);
        }
        final Call<String> request = repository.fetchPosts(tag, builder.build());
        request.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull final Call<String> call, @NonNull final Response<String> response) {
                try {
                    if (callback == null) {
                        return;
                    }
                    final String body = response.body();
                    if (TextUtils.isEmpty(body)) {
                        callback.onSuccess(null);
                        return;
                    }
                    final PostsFetchResponse tagPostsFetchResponse = parseResponse(body);
                    callback.onSuccess(tagPostsFetchResponse);
                } catch (JSONException e) {
                    Log.e(TAG, "onResponse", e);
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(@NonNull final Call<String> call, @NonNull final Throwable t) {
                if (callback != null) {
                    callback.onFailure(t);
                }
            }
        });
    }

    private PostsFetchResponse parseResponse(@NonNull final String body) throws JSONException {
        final JSONObject root = new JSONObject(body);
        final boolean moreAvailable = root.optBoolean("more_available");
        final String nextMaxId = root.optString("next_max_id");
        final int numResults = root.optInt("num_results");
        final String status = root.optString("status");
        final JSONArray itemsJson = root.optJSONArray("items");
        final List<FeedModel> items = parseItems(itemsJson);
        return new PostsFetchResponse(
                items,
                moreAvailable,
                nextMaxId
        );
    }

    private List<FeedModel> parseItems(final JSONArray items) throws JSONException {
        if (items == null) {
            return Collections.emptyList();
        }
        final List<FeedModel> feedModels = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            final JSONObject itemJson = items.optJSONObject(i);
            if (itemJson == null) {
                continue;
            }
            final FeedModel feedModel = ResponseBodyUtils.parseItem(itemJson);
            if (feedModel != null) {
                feedModels.add(feedModel);
            }
        }
        return feedModels;
    }
}
