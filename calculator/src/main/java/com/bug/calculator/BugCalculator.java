package com.bug.calculator;

public class BugCalculator {

    /**
     * 这个方法有问题，参数为两个浮点数，返回值为整型，导致精度丢失，且方法体中打印了log
     * @param a
     * @param b
     * @return
     */
    public static int add(float a, float b) {
        System.out.println("result is " + (a + b));
        return (int) (a + b);
    }
}