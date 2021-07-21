package net.preibisch.rsfish.spark.aws.tools.sparkOpt;

import java.util.HashMap;
import java.util.Map;

public class SparkDriverInstance {
    /*
          For memory-intensive applications, prefer R type instances over the other instance types.
          For compute-intensive applications, prefer C type instances.
          For applications balanced between memory and compute, prefer M type general-purpose instances.

          1- spark.executors.cores = 5 (vCPU)

          2- spark.executor.memory
          Number of executors per instance = (total number of virtual cores per instance - 1)/ spark.executors.cores
          Number of executors per instance = (48 - 1)/ 5 = 47 / 5 = 9 (rounded down)

          Total executor memory = total RAM per instance / number of executors per instance
          Total executor memory = 383 / 9 = 42 (rounded down)

          spark.executors.memory = total executor memory * 0.90
          spark.executors.memory = 42 * 0.9 = 37 (rounded down)

          3- spark.yarn.executor.memoryOverhead
          spark.yarn.executor.memoryOverhead = total executor memory * 0.10
          spark.yarn.executor.memoryOverhead = 42 * 0.1 = 5 (rounded up)
     */
    public String SPARK_EXECUTORS_MEMORY_KEY = "spark.driver.memory";
    public String SPARK_EXECUTORS_CORES = "spark.driver.cores";
    public String SPARK_MEMORY_OVERHEAD_KEY = "spark.driver.memoryOverhead";
    public static int DEFAULT_EXECUTORS_CORES = 5;
    protected final int memoryGb;
    protected final int cores;
    protected final int executorCores;

    public SparkDriverInstance(int memoryGb, int cores) {
       this(memoryGb,cores,DEFAULT_EXECUTORS_CORES);
    }

    public SparkDriverInstance(int memoryGb, int cores, int execCores) {
        this.memoryGb = memoryGb;
        this.cores = cores;
        this.executorCores=execCores;
    }


    public int getExecutorMemoryOverheadValue() {
        //        spark.yarn.executor.memoryOverhead - round up
        return (int) Math.ceil(getTotalMemoryPerExecutor() * 0.1);
    }


    public int getExecutorsMemoryValue() {
        //        spark.executors.memory -     spark.driver.memory - round down
        return (int) (getTotalMemoryPerExecutor() * 0.9);
    }

    public int getTotalMemoryPerExecutor() {
        return (memoryGb - 1) / getExecutorsPerInstance();
    }

    public int getExecutorsPerInstance() {
        return (cores - 1) / executorCores;
    }

    public Map<String, String> getParams() {
        Map<String, String> result = new HashMap<>();
        result.put(SPARK_EXECUTORS_MEMORY_KEY, formatMemory(getExecutorsMemoryValue()));
        result.put(SPARK_MEMORY_OVERHEAD_KEY, formatMemory(getExecutorMemoryOverheadValue()));
        result.put(SPARK_EXECUTORS_CORES, String.valueOf(executorCores));
        return result;
    }

    public static String formatMemory(int memoryGb) {
        return memoryGb * 1000 + "M";
    }

    @Override
    public String toString() {
        return "SparkDriverInstance{" +
                "memoryGb=" + memoryGb +
                ", cores=" + cores +
                '}';
    }

    public void print() {
        System.out.println(this.toString());

        for (Map.Entry<String, String> entry : getParams().entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
    }

    public static void main(String[] args) {
        SparkDriverInstance instance = new SparkDriverInstance(384, 48);

        System.out.println("Executors Per Instance : " + instance.getExecutorsPerInstance());

        System.out.println("Total Memory per executor : " + instance.getTotalMemoryPerExecutor());

        System.out.println("spark.executors.memory : " + instance.getExecutorsMemoryValue());

        System.out.println("spark.yarn.executor.memoryOverhead : " + instance.getExecutorMemoryOverheadValue());
    }
}

