package com.junsung.wpi3;

public class SensorDataSet {
    static final int NUMBER_OF_AXIS = 3;
    double[] sensorData = new double[NUMBER_OF_AXIS];
    int dataLength = sensorData.length;

    SensorDataSet(double[] sensorData) {
        for (int i = 0; i < dataLength; i++)
            this.sensorData[i] = this.cutDouble(sensorData[i]);
    }

    // 소수점 2번째 자리에서 자르는 함수
    private double cutDouble(double target) {
        String s = String.format("%.2f", target);
        return Double.parseDouble(s);
    }

}
