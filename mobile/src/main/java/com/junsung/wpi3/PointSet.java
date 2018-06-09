package com.junsung.wpi3;

// ArrayList for saving the data
class PointSet {
    double x, y;

    PointSet(double x, double y) {
        this.x = cutDouble(x);
        this.y = cutDouble(y);
    }

    private double cutDouble(double target) {
        String s = String.format("%.2f", target);
        return Double.parseDouble(s);
    }
}
