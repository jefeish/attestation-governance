package com.github.jefeish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HelloWorldTest {
    @Test
    void returnsGreeting() {
        assertEquals("Hello, attested world!", HelloWorld.greeting());
    }
}
