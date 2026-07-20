package net.preibisch.rsfish.spark.aws.tools;


import picocli.CommandLine;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
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
    private String region = "us-east-1";

    public GetNonZeroFiles() {
    }

    @Override
    public Void call() throws Exception {
        S3Client s3 = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(credPublicKey, credPrivateKey)))
                .region(Region.of(region))
                .build();

        S3Uri inputUri = s3.utilities().parseUri(URI.create(input));
        String bucket = inputUri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid input URI " + inputUri));
        String key = inputUri.key().orElseThrow(() -> new IllegalArgumentException("Invalid input URI " + inputUri));
        int startWith = 0;
        int endWith = 0;
        int validSize = 0;
        List<S3Object> allFiles = S3Utils.getList(s3, bucket);
        System.out.println(allFiles.size());
        List<S3Object> filtered = new ArrayList<>();
        for (S3Object os : allFiles) {
            String name = os.key();
            if (name.startsWith(key)) {
                startWith++;
                if (name.endsWith(exto)) {
                    endWith++;
                    if (os.size() > 0) {
                        validSize++;
                        System.out.println(os.key() + " " + os.size());
                        filtered.add(os);
                    }
                }
            }
        }
        System.out.println("StartWith: " + startWith + " EndWith: " + endWith + " validSize: " + validSize);
        System.out.println("Total: " + allFiles.size() + " Final: " + filtered.size());

        return null;
    }

    public static void main(String[] args) throws InterruptedException {
        new CommandLine(new GetNonZeroFiles()).execute(args);
    }
}
