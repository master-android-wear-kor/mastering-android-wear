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

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.Set;


/**
 * 지오펜스 변경 내역을 수신
 */
public class GeofenceTransitionsIntentService extends IntentService {


    private static final String TAG = GeofenceTransitionsIntentService.class.getName();

    public GeofenceTransitionsIntentService() {
        super(GeofenceTransitionsIntentService.class.getSimpleName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * 전달받은 인텐트 처리
     *
     * @param intent 로케이션 서비스가 보낸 인텐트. 이 인텐트는 addGeofences() 가 호출될 때 PendingIntent 에 포함된 형태로 로케이션 서비스에게 전달된다.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "Location changed " + intent);

        GeofencingEvent geoFenceEvent = GeofencingEvent.fromIntent(intent);
        if (geoFenceEvent.hasError()) {
            int errorCode = geoFenceEvent.getErrorCode();
            Log.e(TAG, "Location Services error: " + errorCode);
        } else {

            int transitionType = geoFenceEvent.getGeofenceTransition();
            // NotificationManager 서비스의 인스턴트를 가져옴
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(this);

            Log.i(TAG, "Notifying home todo items");
            String triggeredGeoFenceId = geoFenceEvent.getTriggeringGeofences().get(0)
                    .getRequestId();

            switch (triggeredGeoFenceId) {
                case Constants.HOME_GEOFENCE_ID:
                    if (Geofence.GEOFENCE_TRANSITION_ENTER == transitionType) {
                        Log.i(TAG, "Notifying home todo items");
                        notifyTodoItems(notificationManager, "집", Constants.HOME_TODO_NOTIFICATION_ID, R.drawable.white_house);
                    }
                    break;
                case Constants.WORK_GEOFENCE_ID:
                    if (Geofence.GEOFENCE_TRANSITION_ENTER == transitionType) {
                        Log.i(TAG, "Notifying work todo items");
                        notifyTodoItems(notificationManager, "회사", Constants.WORK_TODO_NOTIFICATION_ID, R.drawable.capitol_hill);
                    }
                    break;
            }
        }
    }

    private void notifyTodoItems(NotificationManagerCompat notificationManager, String todoItemType, int notificationId, int background) {
        Set<String> todoItems = TodoItems.readItems(this, todoItemType);
        Intent viewIntent = new Intent(this, TodoMobileActivity.class);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(this, 0, viewIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_today_notification)
                        .setLargeIcon(BitmapFactory.decodeResource(
                                getResources(), background))
                        .setContentTitle(todoItemType + " 할 일 항목 "+ todoItems.size() + "개 발견!")
                        .setContentText(todoItems.toString()    )
                        .setContentIntent(viewPendingIntent);

        // 알림을 만들고, 노티피케이션 매니저를 통해 등록함
        notificationManager.notify(notificationId, notificationBuilder.build());
    }
}
