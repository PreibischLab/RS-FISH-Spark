package net.preibisch.rsfish.spark.aws;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import net.preibisch.rsfish.spark.aws.tools.S3Supplier;
import net.preibisch.rsfish.spark.aws.tools.S3Utils;
import net.preibisch.rsfish.spark.aws.tools.sparkOpt.SparkInstancesConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

public class RunAWSSparkRSFISHIJ implements Callable<Void> {
    @Option(names = {"-p", "--params"}, required = true, description = "param file for the taks, e.g. -i '3://bucket-name/cmdlineargs.txt'")
    private String params = null;

    @Option(names = {"-pk", "--publicKey"}, required = true, description = "Credential public key")
    private String credPublicKey;

    @Option(names = {"-pp", "--privateKey"}, required = true, description = "Credential private key")
    private String credPrivateKey;

    @Option(names = {"-reg", "--region"}, required = false, description = "S3 region Exmpl: us-east-1")
    private String region = Regions.US_EAST_1.getName();


    @Option(names = {"-m", "--memory"}, required = false, description = "Memory size of the execution instances in Gb e.g.: 16 ")
    private int memory;


    @Option(names = {"-co", "--cores"}, required = false, description = "Total of cores in the execution instance e.g.: 8")
    private int cores;

    @Option(names = {"-i", "--instances"}, required = false, description = "Number of instances used for processing")
    private int instances;

    @Option(names = {"-cp", "--execCores"}, required = false, description = "Number of cores you want to dedicate to each task. default: 1")
    private int execCores  = 1 ;

    @Override
    public Void call() throws Exception {
        final AmazonS3 s3 = S3Utils.initS3(credPublicKey, credPrivateKey, region);
        String args = S3Utils.get(s3, params);
        SparkInstancesConfiguration sparkInstancesConfigurations = null;
        if (memory > 0 && cores > 0 && instances > 0)
            sparkInstancesConfigurations = new SparkInstancesConfiguration(memory, cores, instances, execCores);
        else
            sparkInstancesConfigurations = new SparkInstancesConfiguration();
        sparkInstancesConfigurations.print();
        S3Supplier s3supplier = new S3Supplier(credPublicKey, credPrivateKey, region);
        new CommandLine(new AWSSparkRSFISH_IJ(s3supplier, sparkInstancesConfigurations)).execute(args.split(" "));
        return null;
    }

    public static void main(String[] args) {
        new CommandLine(new RunAWSSparkRSFISHIJ()).execute(args);
    }
}
