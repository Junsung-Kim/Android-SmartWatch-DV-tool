package com.junsung.wpi3;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class MotionSensor implements SensorEventListener{

    static final int NUMBER_OF_AXIS = 3;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private String mOption;

    // 가속도계 로우패스 필터
    static final float ALPHA = 0.8f;
    private float[] mGravity = new float[NUMBER_OF_AXIS];

    private float[] mResultValues = new float[NUMBER_OF_AXIS];

    // Kalman filter
    private float[] mAxis = new float[NUMBER_OF_AXIS];
    private KalmanFilter[] mKalmanFilter = new KalmanFilter[NUMBER_OF_AXIS];

    MotionSensor(SensorManager sensorManager, int sensorType, String option) {
        mSensorManager = sensorManager;
        mSensor = sensorManager.getDefaultSensor(sensorType);
        mOption = option;

        // 로우패스 필터에 사용되는 값 초기화
        for(int i = 0 ; i < NUMBER_OF_AXIS; i++) mGravity[i] = 0;

        // 칼만필터 초기화
        for(int i = 0 ; i < NUMBER_OF_AXIS; i++) {
            mKalmanFilter[i] = new KalmanFilter(0.0f);
        }
    }


    /**
     * call in onResume, MainActivity of wear
     */
    void register() {
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
    }

    void register(int sensorType, String option) {
        if(sensorType == Sensor.TYPE_LINEAR_ACCELERATION) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        else {
            mSensor = mSensorManager.getDefaultSensor(sensorType);
        }
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
        mOption = option;
    }

    /**
     * call in onPause, MainActivity of wear
     */
    void unregister() {
        mSensorManager.unregisterListener(this);
    }

    void unregister(int sensorType) {
        mSensor = mSensorManager.getDefaultSensor(sensorType);
        mSensorManager.unregisterListener(this, mSensor);
    }

    public float[] getResultValues() {
        return mResultValues;
    }

    float[] getResult() {
        float[] tmp = new float[NUMBER_OF_AXIS];
        System.arraycopy(mResultValues, 0, tmp, 0, 3);
        return tmp;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                for(int i = 0; i < NUMBER_OF_AXIS; i++) {
                    mResultValues[i] = event.values[i];
                }
                break;
            case Sensor.TYPE_ACCELEROMETER:
                // 저속 통과 필터를 적용한 중력 데이터를 구한다.
                // 직전 중력 값에 alpha 를 곱하고, 현재 데이터에 0.2 를 곱하여 두 값을 더한다.
                for(int i = 0; i < NUMBER_OF_AXIS; i++) {
                    mGravity[i] = ALPHA * mGravity[i] + (1 - ALPHA) * event.values[i];
                    mResultValues[i] = event.values[i] - mGravity[i];
                }
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                float[] rotationMatrix = new float[16];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                mResultValues = determineOrientation(rotationMatrix);
                break;
            default:
                Log.d("onSensorChanged", event.sensor.getType() + " is not available");
        }

        switch (mOption) {
            case "rad2deg":
                for(int i = 0 ; i < NUMBER_OF_AXIS; i++)
                    mResultValues[i] = (int)Math.toDegrees(mResultValues[i]);
                break;
            case "kalman":
                for(int i = 0 ; i < NUMBER_OF_AXIS; i++) {
                    mResultValues[i] = (float) mKalmanFilter[i].update(mResultValues[i]);
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private float[] determineOrientation(float[] rotationMatrix) {
        float[] orientationValues = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientationValues);

        return orientationValues;
    }
}
