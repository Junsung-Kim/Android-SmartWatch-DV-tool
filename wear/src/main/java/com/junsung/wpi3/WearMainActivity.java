package com.junsung.wpi3;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class WearMainActivity extends Activity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener {

    private TextView mTextView; // 텍스트를 출력할 뷰
    private View mLayout; // 배경을 출력할 레이아웃

    private GoogleApiClient mGoogleApiClient; // 구글 플레이 서비스 API 객체

    // Sensor capture flag
    private boolean mCaptureFlag = false;

    // sensor data capture interval
    private static final int CAPTURE_INTERVAL = 100;

    // for MotionSensor
    private MotionSensor mMotionSensor;
    private int mSensorType;
    private String mOption = null;

    // Thread handler
    @SuppressLint("HandlerLeak")
    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {}
    };

    @Override // Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mLayout = stub.findViewById(R.id.layout);
            }
        });

        // 구글 플레이 서비스 객체를 시계 설정으로 초기화
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorType = Sensor.TYPE_STEP_DETECTOR;
        mOption = "null";
        mMotionSensor = new MotionSensor(sensorManager, mSensorType, mOption);

        LogThread logThread = new LogThread();
        logThread.start();

        // 액티비티 화면 안꺼지도록
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // 액티비티가 시작할 때 실행
    @Override // Activity
    protected void onStart() {
        super.onStart();

        // 구글 플레이 서비스에 접속돼 있지 않다면 접속한다.
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMotionSensor.register();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMotionSensor.unregister();
    }

    // 액티비티가 종료될 때 실행
    @Override // Activity
    protected void onStop() {
        // 구글 플레이 서비스 접속 해제
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    // 구글 플레이 서비스에 접속 됐을 때 실행
    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle bundle) {
        Toast.makeText(this, "Google API Connected", Toast.LENGTH_SHORT).show();

        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    // 구글 플레이 서비스에 접속이 일시정지 됐을 때 실행
    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int i) {
        Toast.makeText(this, "Google API Connection Suspended", Toast.LENGTH_SHORT).show();
    }

    // 구글 플레이 서비스에 접속을 실패했을 때 실행
    @Override // GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Google API Connection Failed", Toast.LENGTH_SHORT).show();

        // 노드, 메시지, 데이터 이벤트 리스너 해제
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
    }

    // 메시지가 수신되면 실행되는 메소드
    @Override // MessageApi.MessageListener
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals("/MESSAGE_PATH")) {
            // 텍스트뷰에 적용 될 문자열을 지정한다.
            final String msg = new String(messageEvent.getData(), 0, messageEvent.getData().length);
            String[] strSplit = msg.split(":::");
            mSensorType = Integer.parseInt(strSplit[0]);
//            mOption = strSplit[1];
            mOption = "kalman";

            mMotionSensor.unregister(mSensorType);
            mMotionSensor.register(mSensorType, mOption);


            // UI 스레드를 실행하여 텍스트 뷰의 값을 수정한다.
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextView.setText(msg);
                }
            });
        }

        if (messageEvent.getPath().equals("/CONTROL_PATH")) {
            changeCaptureFlag();
        }
    }

    public void changeCaptureFlag() {
        if (mCaptureFlag) {
            mCaptureFlag = false;
            ((Button)findViewById(R.id.captureBtn)).setText("Start Capture");
        }
        else {
            mCaptureFlag = true;
            ((Button)findViewById(R.id.captureBtn)).setText("Stop Capture");
        }

    }


    public void onCapture(View view) {
        changeCaptureFlag();
    }

    public void onExit(View view) {
        moveTaskToBack(true);
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void sendSensorData(final String msg) {
        // 페어링 기기들을 지칭하는 노드를 가져온다.
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {

                    // 노드를 가져온 후 실행된다.
                    @Override
                    public void onResult(@NonNull NodeApi.GetConnectedNodesResult
                                                 getConnectedNodesResult) {

                        // 노드를 순회하며 메시지를 전송한다.
                        for (final Node node : getConnectedNodesResult.getNodes()) {

                            // 전송할 메시지 텍스트 생성
                            byte[] bytes = msg.getBytes();


                            // 메시지 전송 및 전송 후 실행 될 콜백 함수 지정
                            Wearable.MessageApi.sendMessage(mGoogleApiClient,
                                    node.getId(), "/MESSAGE_PATH", bytes)
                                    .setResultCallback(resultCallback);
                        }
                    }
                });
    }

    // 핸드폰으로 데이터 및 메시지를 전송 후 실행되는 메소드
    private ResultCallback resultCallback = new ResultCallback() {
        @Override
        public void onResult(@NonNull Result result) { }
    };

    private class LogThread extends Thread {
        @Override
        public void run() {
            while(true) {
                if(mCaptureFlag) {
                    float[] tmp;
                    tmp = mMotionSensor.getResult();
                    String tmpString = String.valueOf(tmp[0]+":::"+tmp[1]+":::"+tmp[2]);
                    sendSensorData(tmpString);
                }
                try {
                    Thread.sleep(CAPTURE_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mHandler.sendEmptyMessage(0);
            }
        }
    }
}
