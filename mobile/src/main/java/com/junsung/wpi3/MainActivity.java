package com.junsung.wpi3;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RadioButton;
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
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends Activity
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener {

    // View
    private TextView mReceivedDataView;
    private TextView mCurrentSettingView;

    // RadioButtonSensor
    private RadioButton mRBSensorRotationVector;
    private RadioButton mRBSensorAccelerometer;
    private RadioButton mRBSensorGameRotationVector;

    // Sensor Setting
    private int mSensorType;

    private ArrayList<SensorDataSet> mSensorDataSets;

    // Graph
    static LinearLayout sLinearLayout;
    static GraphView sGraphView;
    static LineGraphSeries<DataPoint>[] sLineSeries;
    static int[] sLineColors = {Color.CYAN, Color.RED, Color.YELLOW};
    float mXAxisBound = 0.0f;

    // Data point
    private DataPoint[] mAxisDataPoints;

    // 구글 플레이 서비스 API 객체
    private GoogleApiClient mGoogleApiClient;

    @Override // Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 구글 플레이 서비스 객체를 시계 설정으로 초기화
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        graphInit();
        viewInit();

        mSensorDataSets = new ArrayList<>();

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

    // 액티비티가 종료될 때 실행
    @Override // Activity
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    // Exit 버튼 클릭할 때
    public void onExit(View view) {
        moveTaskToBack(true);
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    // 구글 플레이 서비스에 접속 됐을 때 실행
    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle bundle) {
        Toast.makeText(this, "Google API Connected", Toast.LENGTH_SHORT).show();

        // 노드, 메시지, 데이터 이벤트를 활용할 수 있도록 이벤트 리스너 지정
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
    }

    // 시계로 데이터 및 메시지를 전송 후 실행되는 메소드
    private ResultCallback resultCallback = new ResultCallback() {
        @Override
        public void onResult(@NonNull Result result) {
            String resultString = "Sending Result : " + result.getStatus().isSuccess();

            Toast.makeText(getApplication(), resultString, Toast.LENGTH_SHORT).show();
        }
    };

    // 메시지가 수신되면 실행되는 메소드
    @Override // MessageApi.MessageListener
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals("/MESSAGE_PATH")) {
            final String msg = new String(messageEvent.getData(), 0, messageEvent.getData().length);
            final String[] strSplit = msg.split(":::");
            final String finalMsg =
                    String.valueOf(Double.parseDouble(strSplit[0])) + "  " +
                    String.valueOf(Double.parseDouble(strSplit[1])) + "  " +
                    String.valueOf(Double.parseDouble(strSplit[2]));
            final double[] sensorData = new double[SensorDataSet.NUMBER_OF_AXIS];

            for(int i = 0 ; i < sensorData.length; i++)
                sensorData[i] = Double.parseDouble(strSplit[i]);

            // UI 스레드를 실행하여 텍스트 뷰의 값을 수정한다.
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mReceivedDataView.setText(msg);

                    // 그래프 그려보자
                    mXAxisBound += 0.1f;
                    DataPoint[] dataPoints = new DataPoint[SensorDataSet.NUMBER_OF_AXIS];
                    for(int i = 0 ; i < SensorDataSet.NUMBER_OF_AXIS; i++) {
                        dataPoints[i] = new DataPoint(mXAxisBound, sensorData[i]);
                        sLineSeries[i].appendData(dataPoints[i], false, 10000);
                        mAxisDataPoints[i] = dataPoints[i];
                    }
                    sGraphView.getViewport().setMinX(mXAxisBound - 2);
                    sGraphView.getViewport().setMaxX(mXAxisBound + 2);

                    sLinearLayout.removeView(sGraphView);
                    sLinearLayout.addView(sGraphView);
                }
            });
        }
    }

    // Apply Message 버튼을 클릭했을 때 실행
    public void onApplySetting(View view) {
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
                            String message = String.valueOf(mSensorType);
                            byte[] sentMsg = message.getBytes();

                            // TODO: 옵션

                            // 메시지 전송 및 전송 후 실행 될 콜백 함수 지정
                            Wearable.MessageApi.sendMessage(mGoogleApiClient,
                                    node.getId(), "/MESSAGE_PATH", sentMsg)
                                    .setResultCallback(resultCallback);
                        }
                    }
                });

    }

    // 시계 작동을 핸드폰에서 실행
    public void onCaptureWear(View view) {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(@NonNull NodeApi.GetConnectedNodesResult
                                                 getConnectedNodesResult) {
                        for(final Node node : getConnectedNodesResult.getNodes()) {
                            String message = "capture";
                            byte[] bytes = message.getBytes();

                            Wearable.MessageApi.sendMessage(mGoogleApiClient,
                                    node.getId(), "/CONTROL_PATH", bytes)
                                    .setResultCallback(resultCallback);
                        }
                    }
                });
    }


    public void onSave(View view) {
        String mStrPath, mStrSensor;
        File mPathFile, mSensorFile;

        /*
        mStrPath = Environment.getExternalStorageDirectory()
                .getAbsolutePath()
                + "/"
                + new SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).
                        format(new Date()) +
                "_path.txt";

        mPathFile = new File(mStrPath);

        int pointSetLength = mPointSets.size();
        for(int i = 0 ; i < pointSetLength; i++) {
            String tmp = String.valueOf(mPointSets.get(i).x) + "\t"
                    + String.valueOf(mPointSets.get(i).y);
            writeLog(mPathFile, tmp);
        }

        mStrSensor = Environment.getExternalStorageDirectory()
                .getAbsolutePath()
                + "/"
                + new SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).
                        format(new Date()) +
                "_sensor.txt";

        mSensorFile = new File(mStrSensor);

        int sensorDataLength = mSensorDataSets.size();
        for(int i = 0 ; i < sensorDataLength; i++) {
            String tmp = String.valueOf(mSensorDataSets.get(i).sensorData[0]) + "\t"
                    + String.valueOf(mSensorDataSets.get(i).sensorData[1]) + "\t"
                    + String.valueOf(mSensorDataSets.get(i).sensorData[2]);
            writeLog(mSensorFile, tmp);
        }

        */
        Toast.makeText(getApplicationContext(), "File save success", Toast.LENGTH_SHORT).show();
    }

    private void graphInit() {
        sLinearLayout = findViewById(R.id.graphLayout);
        sLineSeries = (LineGraphSeries<DataPoint>[]) new LineGraphSeries[SensorDataSet.NUMBER_OF_AXIS];

        // 그래프의 시작점을 (0, 0)으로 초기화
        // 그래프 정보 초기화
        for(int i = 0; i < SensorDataSet.NUMBER_OF_AXIS; i++) {
            sLineSeries[i] = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
            sLineSeries[i].setTitle(String.valueOf(i));
            sLineSeries[i].setColor(sLineColors[i]);
        }

        // 그래프뷰 초기화
        sGraphView = new GraphView(this);

        // 뷰포트 설정 초기화
        sGraphView.getViewport().setScalable(true);
        sGraphView.getViewport().setScrollable(true);
        sGraphView.getViewport().setScrollableY(false);
        sGraphView.getViewport().setYAxisBoundsManual(true);
        sGraphView.getViewport().setMaxY(10.0); // TODO: 센서별로 바꿔야함
        sGraphView.getViewport().setMinY(-10.0);
        sGraphView.setBackgroundColor(Color.DKGRAY);
        sGraphView.setTitleColor(Color.WHITE);

        sGraphView.getGridLabelRenderer().setGridColor(Color.WHITE);
        sGraphView.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
        sGraphView.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);

        sGraphView.getLegendRenderer().setVisible(true);
        sGraphView.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        sGraphView.getLegendRenderer().setTextColor(Color.WHITE);

        mAxisDataPoints = new DataPoint[SensorDataSet.NUMBER_OF_AXIS];
        for(int i = 0; i < SensorDataSet.NUMBER_OF_AXIS; i++) {
            sGraphView.addSeries(sLineSeries[i]);
            mAxisDataPoints[i] = new DataPoint(0, 0);
        }

        sLinearLayout.addView(sGraphView);
    }

    private void viewInit() {
        // 시계로부터 전송받은 텍스트뷰
        mReceivedDataView = findViewById(R.id.dataView);

        // 현재 세팅
        mCurrentSettingView = findViewById(R.id.settingView);

        // RadioButtonSensor
        mRBSensorRotationVector = findViewById(R.id.sensorRV);
        mRBSensorRotationVector.setOnClickListener(RBOnClickListener);
        mRBSensorAccelerometer = findViewById(R.id.sensorAcc);
        mRBSensorAccelerometer.setOnClickListener(RBOnClickListener);
        mRBSensorGameRotationVector = findViewById(R.id.sensorGRV);
        mRBSensorGameRotationVector.setOnClickListener(RBOnClickListener);
    }

    RadioButton.OnClickListener RBOnClickListener = new RadioButton.OnClickListener() {
        @Override
        public void onClick(View v) {
            String sensorTypeStr = null;

            if (mRBSensorRotationVector.isChecked()) {
                mSensorType = Sensor.TYPE_ROTATION_VECTOR;
                sensorTypeStr = "RV";
            } else if (mRBSensorAccelerometer.isChecked()) {
                //mSensorType = Sensor.TYPE_ACCELEROMETER;
                mSensorType = Sensor.TYPE_LINEAR_ACCELERATION;
                sensorTypeStr = "ACC";
            } else if (mRBSensorGameRotationVector.isChecked()) {
                mSensorType = Sensor.TYPE_GAME_ROTATION_VECTOR;
                sensorTypeStr = "GRV";
            }

            mCurrentSettingView.setText(sensorTypeStr);
        }
    };


    /**
     * 파일에 문자열을 적는 메소드
     *
     * @param file 문자열을 write 할 파일 객체
     * @param str 적을 문자열
     */
    private void writeLog(File file, String str) {
        if (file.exists()) {
        } else {
            try {
                file.createNewFile();
                PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(file)));

                printWriter.print("file writing start\n");

                printWriter.flush();
                printWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
            pw.write(str + "\n");
            pw.flush();
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 소수점 2번 자리에서 자르는 메소드
    public double cutDouble(double target) {
        String s = String.format("%.2f", target);
        return Double.parseDouble(s);
    }

}
