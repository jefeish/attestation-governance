package com.github.jefeish;

public final class HelloWorld {
    private HelloWorld() {
    }

    public static String greeting() {
        return "Hello, attested world!";
    }

    public static void main(String[] args) {
        System.out.println(greeting());
    }
}
