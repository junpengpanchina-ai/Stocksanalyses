package com.stocksanalyses.service.matching;

import java.time.Duration;

public class BarClock {
  private long currentBarId;
  private long barStartMs;
  private long barEndMs;
  private final long barLengthMs;

  public BarClock(long startBarId, long startMs, Duration barLength) {
    this.currentBarId = startBarId;
    this.barStartMs = startMs;
    this.barLengthMs = barLength.toMillis();
    this.barEndMs = startMs + barLengthMs;
  }

  public long getCurrentBarId() { return currentBarId; }
  public long getBarStartMs() { return barStartMs; }
  public long getBarEndMs() { return barEndMs; }

  public void advanceToNextBar() {
    currentBarId += 1;
    barStartMs = barEndMs;
    barEndMs = barStartMs + barLengthMs;
  }
}


