package net.preibisch.rsfish.spark.aws.tools.sparkOpt;

import net.preibisch.rsfish.spark.aws.AWSSparkRSFISH_IJ;
import org.apache.spark.SparkConf;
import scala.Tuple2;

import java.util.Map;

public class TestSparkConf {
    public static void main(String[] args) {
        final SparkConf sparkConf = new SparkConf().setAppName(AWSSparkRSFISH_IJ.class.getSimpleName());

        for (Map.Entry<String, String> entry : new SparkInstancesConfiguration(64,16,19, 2).getAll().entrySet()) {
            sparkConf.set(entry.getKey(), entry.getValue());
        }

        for (Tuple2<String, String> st : sparkConf.getAll()) {
            System.out.println(st._1() + " : " + st._2());
        }
    }
}
