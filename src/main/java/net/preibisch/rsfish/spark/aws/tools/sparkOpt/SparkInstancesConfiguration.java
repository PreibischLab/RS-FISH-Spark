package net.preibisch.rsfish.spark.aws.tools.sparkOpt;

import org.apache.spark.SparkConf;
import scala.Tuple2;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class SparkInstancesConfiguration {

    private final SparkExecutorInstance driverInstance;
    private final SparkDriverInstance executorInstance;
    private final boolean instancesConfigured;

    public SparkInstancesConfiguration(int memoryGb, int cores, int instances, int execCores) {
        this.driverInstance = new SparkExecutorInstance(memoryGb, cores, instances,execCores);
        this.executorInstance = new SparkDriverInstance(memoryGb, cores,execCores);
        this.instancesConfigured = true;
    }

    public SparkInstancesConfiguration() {
        this.driverInstance = null;
        this.executorInstance = null;
        this.instancesConfigured = false;
    }

    public SparkConf config(SparkConf sparkConf) {
        for (Map.Entry<String, String> entry : getAll().entrySet()) {
            sparkConf.set(entry.getKey(), entry.getValue());
        }
        return sparkConf;
    }


    public Map<String, String> getAll() {
        Map<String, String> all = new HashMap<>();
        if (instancesConfigured) {
            all.putAll(driverInstance.getParams());
            all.putAll(executorInstance.getParams());
        }
        all.put("spark.network.timeout", "800s");
        all.put("spark.executor.heartbeatInterval", "60s");
        all.put("spark.dynamicAllocation.enabled", "false");
        all.put("spark.memory.fraction", "0.80");
        all.put("spark.memory.storageFraction", "0.30");
        all.put("spark.executor.extraJavaOptions", "-XX:+UseConcMarkSweepGC -XX:OnOutOfMemoryError='kill -9 %p'");
        all.put("spark.driver.extraJavaOptions", "-XX:+UseConcMarkSweepGC -XX:OnOutOfMemoryError='kill -9 %p'");
        all.put("spark.yarn.scheduler.reporterThread.maxFailures", "5");
        all.put("spark.storage.level", "MEMORY_AND_DISK_SER");
        all.put("spark.rdd.compress", "true");
        all.put("spark.shuffle.compress", "true");
        all.put("spark.shuffle.spill.compress", "true");
        return all;
    }

    public Stream<Tuple2> getIterable() {
        return getAll().entrySet().stream().map(entry -> new Tuple2(entry.getKey(), entry.getValue()));
    }

    public void print() {
        for (Map.Entry<String, String> entry : getAll().entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
    }

    public static void main(String[] args) {

        new SparkInstancesConfiguration(61, 8, 10,2).print();
    }

}

