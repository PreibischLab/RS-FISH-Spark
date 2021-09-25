package net.preibisch.rsfish.spark.aws.tools;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class GetNonZeroFiles implements Callable<Void> {

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

    public GetNonZeroFiles() {
    }

    @Override
    public Void call() throws Exception {
        AWSCredentials credentials = new BasicAWSCredentials(
                credPublicKey, credPrivateKey
        );
        AmazonS3 s3 = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.fromName(region))
                .build();

        AmazonS3URI inputUri = new AmazonS3URI(input);
        int startWith = 0;
        int endWith = 0;
        int validSize = 0;
//        List<S3ObjectSummary> allFiles = S3Utils.getFilesListSummary(s3, inputUri, exto);
        List<S3ObjectSummary> allFiles = S3Utils.getList(s3,inputUri.getBucket());
        System.out.println(allFiles.size());
        List<S3ObjectSummary> filtered = new ArrayList<>();
        for (S3ObjectSummary os : allFiles){
            String name = os.getKey();
            if(name.startsWith(inputUri.getKey())){
                startWith++;
                if(name.endsWith(exto)){
                    endWith++;
                    if(os.getSize()>0){
                        validSize++;
                        System.out.println(os.getKey()+" "+os.getSize());
                        filtered.add(os);
                    }
                }
            }
        }
        System.out.println("StartWith: "+startWith+" EndWith: "+endWith+" validSize: "+validSize);
        System.out.println("Total: " + allFiles.size() + " Final: " + filtered.size());

        return null;
    }

    public static void main(String[] args) throws InterruptedException {

        new CommandLine(new GetNonZeroFiles()).execute(args);
    }


}
