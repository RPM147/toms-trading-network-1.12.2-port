package com.tom.trading.remote;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionLifetimeTest {
    @Test public void pollingDoesNotExtendIdleAndActivityCannotResurrectExpiredSession() {
        SessionLifetime life = new SessionLifetime(-100, 30, 120);
        for (long now = -100; now < -70; now++) assertFalse(life.expired(now));
        assertTrue(life.expired(-70)); life.activity(-70); assertTrue(life.expired(-70));
    }
    @Test public void repeatedActivityCannotExtendAbsoluteLimit() {
        SessionLifetime life = new SessionLifetime(0, 30, 120);
        for (long now = 20; now < 120; now += 20) { assertFalse(life.expired(now)); life.activity(now); }
        assertFalse(life.expired(119)); assertTrue(life.expired(120));
    }
    @Test public void monotonicCounterWrapDoesNotPrematurelyExpireOrLoseDeadline() {
        long start = Long.MAX_VALUE - 10;
        SessionLifetime life = new SessionLifetime(start, 30, 120);
        assertFalse(life.expired(start + 29)); assertTrue(life.expired(start + 30));
    }
}
