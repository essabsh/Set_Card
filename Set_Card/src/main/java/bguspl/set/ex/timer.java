package bguspl.set.ex;

import bguspl.set.Env;

public class timer implements Runnable {
    public long time;
    public Env env;
    public volatile boolean timerterminate;

    public timer(Env e) {
        time = e.config.turnTimeoutMillis;
        env = e;
    }

    public timer(long time, Env env) {
        this.time = time;
        this.env = env;
    }

    public void run() {
        while (time >= 0 & !timerterminate) {
            if (time > env.config.turnTimeoutWarningMillis) {
                env.ui.setCountdown(time, false);
                if (time < 1000) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                    }
                    time -= 10;
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                    }
                    time = time - 1000;
                }
            } else {
                env.ui.setCountdown(time, true);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                }
                time = time - 10;
            }
        }
    }
}
