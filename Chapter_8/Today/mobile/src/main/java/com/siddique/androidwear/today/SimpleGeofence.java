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

public class SimpleGeofence {

    // 인스턴스 변수
    private final String mId;
    private final double mLatitude;
    private final double mLongitude;
    private final float mRadius;
    private long mExpirationDuration;

    public SimpleGeofence(String geofenceId, double latitude, double longitude) {
        // 생성자가 전달받은 값을 인스턴스 필드에 할당
        this.mId = geofenceId;
        this.mLatitude = latitude;
        this.mLongitude = longitude;
        this.mRadius = 50; //단위는 미터
        this.mExpirationDuration = com.google.android.gms.location.Geofence.NEVER_EXPIRE;
    }


    public com.google.android.gms.location.Geofence toGeofence() {
        // 새로운 SimpleGeofence 객체를 만든다
        return new com.google.android.gms.location.Geofence.Builder()
                .setRequestId(mId)
                .setTransitionTypes(com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER | com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT)
                .setCircularRegion(mLatitude, mLongitude, mRadius)
                .setExpirationDuration(mExpirationDuration)
                .build();
    }

}
