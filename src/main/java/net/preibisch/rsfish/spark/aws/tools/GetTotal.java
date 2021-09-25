package net.preibisch.rsfish.spark.aws.tools;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;

public class GetTotal implements Callable<Void> {

    @CommandLine.Option(names = {"-i", "--input"}, required = true, description = "input folder to be processed (need to be ImageJ-readable), e.g. -i '3://bucket-name/folder'")
    private String input;

    @CommandLine.Option(names = {"-exo", "--extension_output"}, required = false, description = "Extension output e.g. '.csv'")
    private String exto = ".csv";

    @CommandLine.Option(names = {"-pk", "--publicKey"}, required = false, description = "Credential public key")
    private String credPublicKey;

    @CommandLine.Option(names = {"-pp", "--privateKey"}, required = false, description = "Credential private key")
    private String credPrivateKey;

    @CommandLine.Option(names = {"-reg", "--region"}, required = false, description = "S3 region Exmpl: us-east-1")
    private String region = Regions.US_EAST_1.getName();

    public GetTotal() {
    }

    @Override
    public Void call() throws Exception {
        int old_total = 0;
        AWSCredentials credentials = new BasicAWSCredentials(
                credPublicKey, credPrivateKey
        );
        AmazonS3 s3 = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.fromName(region))
                .build();

        AmazonS3URI inputUri = new AmazonS3URI(input);

        while (true) {
            ArrayList<AmazonS3URI> allFiles = S3Utils.getFilesList(s3, inputUri, exto);
            Date date = new Date();
            Timestamp ts = new Timestamp(date.getTime());
            String old  = "";
            int total = allFiles.size();
            if (old_total >0){
                old = ", processed:" + (total - old_total);
            }
            String str = ts + ": Total: " + total + old;
            old_total = total;
            System.out.println(str);
            Files.write(new File("/Users/Marwan/Desktop/myfile.txt").toPath(),
                    (str+"\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            Thread.sleep(60000);
        }
    }

    public static void main(String[] args) throws InterruptedException {

        new CommandLine(new GetTotal()).execute(args);
    }


}
