package demo;

/** Tiny trusted sample library for trying AutoRESTifier. */
public final class Calculator {
    private Calculator() {}

    public static int add(int a, int b) {
        return a + b;
    }

    public static int multiply(int a, int b) {
        return a * b;
    }

    public static double divide(double a, double b) {
        if (b == 0.0) throw new IllegalArgumentException("Division by zero");
        return a / b;
    }

    public static String greet(String name) {
        return "Hello, " + name + "!";
    }

    public static boolean isEven(int number) {
        return number % 2 == 0;
    }
}
