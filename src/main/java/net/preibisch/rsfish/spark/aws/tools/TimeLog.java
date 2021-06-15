package net.preibisch.rsfish.spark.aws.tools;

import java.sql.Timestamp;

public class TimeLog {
    private static final String BASE_LOG = "TimeLog:";
    private final String task;
    private final long ts;

    public TimeLog(String task) {
        this.task = task;
        this.ts = getTime();
        System.out.println(formatLogString(task, "START", ts));
    }

    public void done() {
        long ts2 = getTime();
        long processTime = ts2 - ts;
        System.out.println(BASE_LOG + task + ";DONE;" + ts2 + ";" + processTime);
    }

    private static String formatLogString(String input, String state, long ts) {
        return BASE_LOG + input + ";" + state + ";" + ts;
    }

    private static long getTime() {
        return new Timestamp(System.currentTimeMillis()).getTime();
    }
}
