/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * WearableActivity 를 확장하고 onEnterAmbient, onUpdateAmbient, onExitAmbient 메서드를 오버라이딩 해서
 * 구현한 대기 화면 지원 기능 데모.
 *
 * 모드는 크게 대화 모드와 대기 모드로 구분된다. 데이터와 화면 갱신을 위해 대화 모드에선 커스텀 핸들러를 사용하고,
 * 대기 모드에선 알람을 사용한다.
 *
 * 왜 한가지 방식만 사용하지 않았을까? 일반적으로 핸들러는 배터리 절약을 염두에 두지 않으며, 1초 간격으로도 실행될 수 있다.
 * 하지만 핸들러는 프로세서를 깨우지 못한다. (대기 모드에선 프로세스가 슬립 모드일 경우가 많다)
 *
 * 알람은 프로세서를 깨울 수 있기 때문에 대기 모드에선 알람을 사용해야 한다. 하지만 빠른 업데이트에는 적합하지 않고,
 * 이런 경우에는 핸들러를 써야 한다.
 *
 * 따라서 대화 모드에선 핸들러를 사용하고(매 초 실행되며 배터리를 더 많이 사용한다.),
 * 대기 모드에선 알람을 사용한다(20초 간격으로만 실행되어도 되고, 프로세서를 깨울 수 있다).
 *
 * 대기 모드에서 액티비티는 배터리를 절약하기 위해 데이터 가져오기, 화면 갱신 등의 각 작업마다 20초의 대기 시간을 기잔다.
 * 이 대기 시간 동안 프로세서는 슬립 모드에 들어갈 수 있다.
 * 1분 마다 화면과 데이터를 갱신해도 된다면 알람과 관련된 코드는 모두 지우고 onUpdateAmbient() 만 쓰면 된다. 이 경우
 * 베터리를 더 많이 절약할 수 있다.
 *
 * 대기 모드에서도 워치 페이스 문서의 성능 가이드라인을 준수해야 한다.
 *
 * 이 액티비티의 대기 모드에는 워치 페이스 API 문서에서 제시하는 대부분의 픽셀을 검정색으로 칠하기,
 * 가급적 흰색 픽셀로 채우는 영역을 갖지 않기, 흑백 색상만 사용하기,
 * 안티 앨리어싱 사용하지 않기 등의 우수 실천 사례를 적용했다.
 */
public class DailyTotalActivity extends WearableActivity {

    private static final String TAG = DailyTotalActivity.class.getSimpleName();

    /** 핸들러에 전달하는 메시지의 'what' 값 */
    private static final int MSG_UPDATE_SCREEN = 0;

    /** 상태 별 업데이트 주기. 단위는 밀리초 */
    private static final long ACTIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(20);


    private TextView mTimeTextView;
    private TextView mTimeStampTextView;
    private TextView mStateTextView;
    private TextView mUpdateRateTextView;
    private TextView mDrawCountTextView;

    private final SimpleDateFormat sDateFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private volatile int mDrawCount = 0;


    /**
     * 핸들러는 디바이스가 대기 모드이면서 충전중이 아닐 경우 프로세서를 깨우지 못하기 때문에 대화 모드에서만 사용한다.
     * 대신 1분 보다 자주 정보를 갱신하기 위해 대기 모드에선 알람을 이용한다.
     * 1분 보다 자주 갱신할 필요가 없다면, 알람 관련된 코드는 모두 지우고 onUpdateAmbient() 메서드만 이용해도 된다.
     */
    private AlarmManager mAmbientStateAlarmManager;
    private PendingIntent mAmbientStatePendingIntent;

    /**
     * 대화 모드에서 사용하는 커스텀 핸들러. 메모리 누수를 막기 위해 정적 클래스로 선언한다.
     */
    private final Handler mActiveModeUpdateHandler = new UpdateHandler(this);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.daily_total_activity_main);

        setAmbientEnabled();

        mAmbientStateAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent ambientStateIntent = new Intent(getApplicationContext(), DailyTotalActivity.class);

        mAmbientStatePendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0 /* requestCode */,
                ambientStateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);


        /** 워치가 원형인지 사각형인지 판단하고, 적절한 뷰를 적용한다. **/
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {

                mTimeTextView = (TextView) stub.findViewById(R.id.time);
                mTimeStampTextView = (TextView) stub.findViewById(R.id.time_stamp);
                mStateTextView = (TextView) stub.findViewById(R.id.state);
                mUpdateRateTextView = (TextView) stub.findViewById(R.id.update_rate);
                mDrawCountTextView = (TextView) stub.findViewById(R.id.draw_count);

                refreshDisplayAndSetNextUpdate();
            }
        });
    }

    /**
     * 대게 대기 모드에서 알람에 의해 호출되며, 데이터 처리와 화면 갱신을 수행한다.
     */
    @Override
    public void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent(): " + intent);
        super.onNewIntent(intent);

        setIntent(intent);

        refreshDisplayAndSetNextUpdate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        mActiveModeUpdateHandler.removeMessages(MSG_UPDATE_SCREEN);
        mAmbientStateAlarmManager.cancel(mAmbientStatePendingIntent);

        super.onDestroy();
    }

    /**
     * 대기 모드에서 사용할 UI를 준비한다.
     */
    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        Log.d(TAG, "onEnterAmbient()");
        super.onEnterAmbient(ambientDetails);


        /** 대기 모드에선 핸들러를 사용하지 않기 때문에 핸들러 큐를 정리한다. */
        mActiveModeUpdateHandler.removeMessages(MSG_UPDATE_SCREEN);

        /**
         * 워치페이스 API의 우수 실천 사례를 적용한다.
         * (대부분의 픽셀을 검정색으로 칠하기,
         * 가급적 흰색 픽셀로 채우는 영역을 갖지 않기, 흑백 색상만 사용하기,
         * 안티 앨리어싱 사용하지 않기 등)
         */
        mStateTextView.setTextColor(Color.WHITE);
        mUpdateRateTextView.setTextColor(Color.WHITE);
        mDrawCountTextView.setTextColor(Color.WHITE);

        mTimeTextView.getPaint().setAntiAlias(false);
        mTimeStampTextView.getPaint().setAntiAlias(false);
        mStateTextView.getPaint().setAntiAlias(false);
        mUpdateRateTextView.getPaint().setAntiAlias(false);
        mDrawCountTextView.getPaint().setAntiAlias(false);

        refreshDisplayAndSetNextUpdate();
    }

    /**
     * 대기 모드에서 매 1분 마다 UI를 갱신한다. 우리는 매 20초 마다 화면을 갱신해야 하기 때문에 별로도 알람을 사용해다.
     * 하지만 이 메서드가 불릴 때 프로세서는 깨어있는 상태이기 때문에, 여기서도 refreshDisplayAndSetNextUpdate() 를
     * 호출해서 화면을 갱신하고 알람을 리셋한다.
     *
     * 대기 모드에서 1분마다 화면을 갱신해도 충분하다면(대부분의 경우에 해당할 것이다), 이 메서드만 사용해서 화면을 갱신하고
     * 다른 알람 관련 코드는 지워버려도 된다.
     */
    @Override
    public void onUpdateAmbient() {
        Log.d(TAG, "onUpdateAmbient()");
        super.onUpdateAmbient();

        refreshDisplayAndSetNextUpdate();
    }

    /**
     * 대화 모드에서 사용할 UI를 준비한다.
     */
    @Override
    public void onExitAmbient() {
        Log.d(TAG, "onExitAmbient()");
        super.onExitAmbient();

        /** 알람은 대기 모드에서만 사용하므로 초기화한다. */
        mAmbientStateAlarmManager.cancel(mAmbientStatePendingIntent);

        mStateTextView.setTextColor(Color.GREEN);
        mUpdateRateTextView.setTextColor(Color.GREEN);
        mDrawCountTextView.setTextColor(Color.GREEN);

        mTimeTextView.getPaint().setAntiAlias(true);
        mTimeStampTextView.getPaint().setAntiAlias(true);
        mStateTextView.getPaint().setAntiAlias(true);
        mUpdateRateTextView.getPaint().setAntiAlias(true);
        mDrawCountTextView.getPaint().setAntiAlias(true);

        refreshDisplayAndSetNextUpdate();
    }

    /**
     * 데이터를 읽고 화면을 갱신한다. 또한 다음 갱신을 위한 준비 작업을 한다
     * (대화 모드에선 핸들러에 메시지 등록, 대기 모드에선 알람 등록)
     */
    private void refreshDisplayAndSetNextUpdate() {

        loadDataAndUpdateScreen();

        long timeMs = System.currentTimeMillis();

        if (isAmbient()) {
            /** 모드에 따른 다음 갱신 시각을 계산 */
            long delayMs = AMBIENT_INTERVAL_MS - (timeMs % AMBIENT_INTERVAL_MS);
            long triggerTimeMs = timeMs + delayMs;

            /**
             * 참고: 매니페스트에서 액티비티의 실행 모드를 singleInstance 로 설정해야 한다.
             * 이렇게 설정하지 않으면 매번 알람이 활성화 될 때 마다 AlarmManager 가 보낸 인텐트는
             * 기존 액티비티를 사용하지 않고 새로운 액티비티를 만든다.
             */
            mAmbientStateAlarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    mAmbientStatePendingIntent);

        } else {
            /** 모드에 따른 다음 갱신 시각을 계산 */
            long delayMs = ACTIVE_INTERVAL_MS - (timeMs % ACTIVE_INTERVAL_MS);

            mActiveModeUpdateHandler.removeMessages(MSG_UPDATE_SCREEN);
            mActiveModeUpdateHandler.sendEmptyMessageDelayed(MSG_UPDATE_SCREEN, delayMs);
        }
    }

    /**
     * 대기 모드 상태에 따라 화면을 갱신한다. 데이터를 가져와야 한다면 여기서 수행한다.
     */
    private void loadDataAndUpdateScreen() {

        mDrawCount += 1;
        long currentTimeMs = System.currentTimeMillis();
        Log.d(TAG, "loadDataAndUpdateScreen(): " + currentTimeMs + "(" + isAmbient() + ")");

        if (isAmbient()) {

            mTimeTextView.setText(sDateFormat.format(new Date()));
            mTimeStampTextView.setText(getString(R.string.timestamp_label, currentTimeMs));

            mStateTextView.setText(getString(R.string.mode_ambient_label));
            mUpdateRateTextView.setText(
                    getString(R.string.update_rate_label, (AMBIENT_INTERVAL_MS / 1000)));

            mDrawCountTextView.setText(getString(R.string.draw_count_label, mDrawCount));

        } else {
            mTimeTextView.setText(sDateFormat.format(new Date()));
            mTimeStampTextView.setText(getString(R.string.timestamp_label, currentTimeMs));

            mStateTextView.setText(getString(R.string.mode_active_label));
            mUpdateRateTextView.setText(
                    getString(R.string.update_rate_label, (ACTIVE_INTERVAL_MS / 1000)));

            mDrawCountTextView.setText(getString(R.string.draw_count_label, mDrawCount));
        }
    }

    /**
     * 메모리 누수를 막기 위해 핸들러는 별도의 정적 클래스로 선언한다.
     */
    private static class UpdateHandler extends Handler {
        private final WeakReference<DailyTotalActivity> mMainActivityWeakReference;

        public UpdateHandler(DailyTotalActivity reference) {
            mMainActivityWeakReference = new WeakReference<DailyTotalActivity>(reference);
        }

        @Override
        public void handleMessage(Message message) {
            DailyTotalActivity mainActivity = mMainActivityWeakReference.get();

            if (mainActivity != null) {
                switch (message.what) {
                    case MSG_UPDATE_SCREEN:
                        mainActivity.refreshDisplayAndSetNextUpdate();
                        break;
                }
            }
        }
    }
}