package com.siddique.androidwear.today;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.support.wearable.view.CardFrame;
import android.util.Log;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class StepCounterActivity extends WearableActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mSensor;

    // 마지막 부팅 이후 측정된 걸음 수
    private int mSteps = 0;

    private static final String TAG = StepCounterActivity.class.getName();

    private BoxInsetLayout stepCounterLayout;
    private CardFrame cardFrame;
    private TextView title, desc;

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

    /** 핸들러에 전달하는 메시지의 'what' 값 */
    private static final int MSG_UPDATE_SCREEN = 0;

    /** 상태 별 업데이트 주기. 단위는 밀리초 */
    private static final long ACTIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(20);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_step_counter);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        setAmbientEnabled();

        mAmbientStateAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent ambientStateIntent = new Intent(getApplicationContext(), DailyTotalActivity.class);

        mAmbientStatePendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0 /* 요청 코드 */,
                ambientStateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        stepCounterLayout = (BoxInsetLayout) findViewById(R.id.step_counter_layout);
        cardFrame = (CardFrame) findViewById(R.id.step_counter_card_frame);
        title = (TextView) findViewById(R.id.daily_step_count_title);
        desc = (TextView) findViewById(R.id.daily_step_count_desc);

        refreshDisplayAndSetNextUpdate();
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

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.i(TAG,
                "onSensorChanged - " + event.values[0]);
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            Log.i(TAG,
                    "Total step count: " + mSteps);

            mSteps = (int) event.values[0];

            refreshDisplayAndSetNextUpdate();
        }
    }

    private void refreshStepCount() {
        desc.setText(getString(R.string.daily_step_count_desc, mSteps));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG,
                "onAccuracyChanged - " + sensor);
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

        stepCounterLayout.setBackgroundColor(Color.BLACK);
        cardFrame.setBackgroundColor(Color.BLACK);

        desc.setTextColor(Color.WHITE);
        desc.getPaint().setAntiAlias(false);

        title.setTextColor(Color.WHITE);
        title.getPaint().setAntiAlias(false);

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

        stepCounterLayout.setBackgroundResource(R.drawable.jogging);
        cardFrame.setBackgroundColor(Color.WHITE);

        desc.setTextColor(Color.BLACK);
        desc.getPaint().setAntiAlias(true);

        title.setTextColor(Color.BLACK);
        title.getPaint().setAntiAlias(true);

        refreshDisplayAndSetNextUpdate();
    }

    /**
     * 데이터를 읽고 화면을 갱신한다. 또한 다음 갱신을 위한 준비 작업을 한다
     * (대화 모드에선 핸들러에 메시지 등록, 대기 모드에선 알람 등록)
     */
    private void refreshDisplayAndSetNextUpdate() {

        Log.i(TAG, "Refresh display and set next update ");

        refreshStepCount();

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
     * 메모리 누수를 막기 위해 핸들러는 별도의 정적 클래스로 선언한다.
     */
    private static class UpdateHandler extends Handler {
        private final WeakReference<StepCounterActivity> mMainActivityWeakReference;

        public UpdateHandler(StepCounterActivity reference) {
            mMainActivityWeakReference = new WeakReference<StepCounterActivity>(reference);
        }

        @Override
        public void handleMessage(Message message) {
            StepCounterActivity mainActivity = mMainActivityWeakReference.get();

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
