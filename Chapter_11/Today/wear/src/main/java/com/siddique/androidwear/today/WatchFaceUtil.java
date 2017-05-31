/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.siddique.androidwear.today;

import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

public final class WatchFaceUtil {
    private static final String TAG = WatchFaceUtil.class.getSimpleName();

    /**
     * {@link DataMap} 의 {@link TodayWatchFaceService} 배경색 키 값.
     * 색 값은 {@link Color#parseColor} 이 해석할 수 있는 {@link String} 값이어야 함.
     */
    public static final String KEY_BACKGROUND_COLOR = "BACKGROUND_COLOR";

    /**
     * {@link DataMap} 의 {@link TodayWatchFaceService} 시간 글씨 색 키 값.
     * 색 값은 {@link Color#parseColor} 이 해석할 수 있는 {@link String} 값이어야 함.
     */
    public static final String KEY_HOURS_COLOR = "HOURS_COLOR";

    /**
     * {@link DataMap} 의 {@link TodayWatchFaceService} 분 글씨 색 키 값.
     * 색 값은 {@link Color#parseColor} 이 해석할 수 있는 {@link String} 값이어야 함.
     */
    public static final String KEY_MINUTES_COLOR = "MINUTES_COLOR";

    /**
     * {@link DataMap} 의 {@link TodayWatchFaceService} 초 글씨 색 키 값.
     * 색 값은 {@link Color#parseColor} 이 해석할 수 있는 {@link String} 값이어야 함.
     */
    public static final String KEY_SECONDS_COLOR = "SECONDS_COLOR";

    /**
     * {@link TodayWatchFaceService} 설정 값을 담은 {@link DataItem} 의 경로.
     */
    public static final String PATH_WITH_FEATURE = "/watch_face_config/Digital";

    /**
     * 대화 모드와 대기 모드의 기본 배경 색.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND = "Black";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND);

    /**
     * 대화 모드와 대기 모드의 기본 시간 글씨 색.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS = "White";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS);

    /**
     * 대화 모드와 대기 모드의 기본 분 글씨 색.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS = "White";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);

    /**
     * 대화 모드와 대기 모드의 기본 초 글씨 색.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS = "Gray";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS);

    /**
     * {@link TodayWatchFaceService}의 {@link DataMap} 설정 관련 콜백 인터페이스
     */
    public interface FetchConfigDataMapCallback {
        /**
         * Callback invoked with the current config {@link DataMap} for
         * {@link TodayWatchFaceService}.
         */
        void onConfigDataMapFetched(DataMap config);
    }

    private static int parseColor(String colorName) {
        return Color.parseColor(colorName.toLowerCase());
    }

    /**
     * {@link TodayWatchFaceService} 의 현재 {@link DataMap} 설정을 비동기로 읽어들이고, 콜백을 호출한다.
     * <p>
     * {@link DataItem} 설정이 존재하지 않는다면, 빈 DataMap 이 콜백에 전달된다.
     */
    public static void fetchConfigDataMap(final GoogleApiClient client,
            final FetchConfigDataMapCallback callback) {
        Wearable.NodeApi.getLocalNode(client).setResultCallback(
                new ResultCallback<NodeApi.GetLocalNodeResult>() {
                    @Override
                    public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                        String localNode = getLocalNodeResult.getNode().getId();
                        Uri uri = new Uri.Builder()
                                .scheme("wear")
                                .path(WatchFaceUtil.PATH_WITH_FEATURE)
                                .authority(localNode)
                                .build();
                        Wearable.DataApi.getDataItem(client, uri)
                                .setResultCallback(new DataItemResultCallback(callback));
                    }
                }
        );
    }

    /**
     * 설정 값을 담은 {@link DataItem} 중 {@link DataMap}의 키와 값으로 설정 값을 덮어씌운다.
     * DataItem 이 생성되지 않았다면 새로 생성한다.
     * <p>
     * DataItem 의 내용 중 {@code configKeysToOverwrite} 에서 명시한 키에 해당하는 값만 변경된다.
     * 나머지 값들은 변경되지 않는다.
     */
    public static void overwriteKeysInConfigDataMap(final GoogleApiClient googleApiClient,
            final DataMap configKeysToOverwrite) {

        WatchFaceUtil.fetchConfigDataMap(googleApiClient,
                new FetchConfigDataMapCallback() {
                    @Override
                    public void onConfigDataMapFetched(DataMap currentConfig) {
                        DataMap overwrittenConfig = new DataMap();
                        overwrittenConfig.putAll(currentConfig);
                        overwrittenConfig.putAll(configKeysToOverwrite);
                        WatchFaceUtil.putConfigDataItem(googleApiClient, overwrittenConfig);
                    }
                }
        );
    }

    /**
     * 설정 값을 담은  {@link DataItem}의 {@link DataMap} 을 {@code newConfig}로 교체한다.
     * DataItem 이 생성되지 않았다면 새로 생성한다.
     */
    public static void putConfigDataItem(GoogleApiClient googleApiClient, DataMap newConfig) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WITH_FEATURE);
//        putDataMapRequest.setUrgent();
        DataMap configToPut = putDataMapRequest.getDataMap();
        configToPut.putAll(newConfig);
        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "putDataItem result status: " + dataItemResult.getStatus());
                        }
                    }
                });
    }

    private static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

        private final FetchConfigDataMapCallback mCallback;

        public DataItemResultCallback(FetchConfigDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem configDataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
                    DataMap config = dataMapItem.getDataMap();
                    mCallback.onConfigDataMapFetched(config);
                } else {
                    mCallback.onConfigDataMapFetched(new DataMap());
                }
            }
        }
    }

    private WatchFaceUtil() { }
}
