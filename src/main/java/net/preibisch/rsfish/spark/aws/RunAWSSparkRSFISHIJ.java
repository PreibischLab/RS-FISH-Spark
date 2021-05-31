package net.preibisch.rsfish.spark.aws;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import net.preibisch.rsfish.spark.aws.tools.S3Utils;
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

    @Override
    public Void call() throws Exception {
        final AmazonS3 s3 = S3Utils.initS3(credPublicKey, credPrivateKey, region);
        String args = S3Utils.get(s3, params);
        new CommandLine(new AWSSparkRSFISH_IJ(credPublicKey, credPrivateKey, region)).execute(args.split(" "));
        return null;
    }

    public static void main(String[] args) {
         new CommandLine(new RunAWSSparkRSFISHIJ()).execute(args);
    }
}
