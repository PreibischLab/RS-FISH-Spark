package net.preibisch.rsfish.spark.aws.tools.sparkOpt;

import java.util.Map;

public class SparkExecutorInstance extends SparkDriverInstance {

    private final String SPARK_EXECUTORS_INSTANCES_KEY = "spark.executor.instances";
    private final String SPARK_PARALLELISM_KEY = "spark.default.parallelism";
    private final int instances;

    public SparkExecutorInstance(int memoryGb, int cores, int instances, int execCores) {
        super(memoryGb, cores, execCores);
        this.instances = instances;
        SPARK_EXECUTORS_MEMORY_KEY = "spark.executors.memory";
        SPARK_MEMORY_OVERHEAD_KEY = "spark.executor.memoryOverhead";
        SPARK_EXECUTORS_CORES = "spark.executors.cores";
    }

    public SparkExecutorInstance(int memoryGb, int cores, int instances) {
        this(memoryGb, cores, instances, DEFAULT_EXECUTORS_CORES);
    }

    public int getExecutorInstances() {
        return (getExecutorsPerInstance() * instances) - 1;
    }

    public int getParallelism() {
        return (getExecutorInstances() * executorCores * 2);
    }

    @Override
    public Map<String, String> getParams() {
        Map<String, String> params = super.getParams();
        params.put(SPARK_EXECUTORS_INSTANCES_KEY, String.valueOf(getExecutorInstances()));
        params.put(SPARK_PARALLELISM_KEY, String.valueOf(getParallelism()));
        return params;
    }

    public static void main(String[] args) {
        new SparkExecutorInstance(64, 16, 19).print();
    }

    @Override
    public String toString() {
        return "SparkExecutorInstance{" +
                "memoryGb=" + memoryGb +
                ", cores=" + cores +
                ", instances=" + instances +
                '}';
    }
}
