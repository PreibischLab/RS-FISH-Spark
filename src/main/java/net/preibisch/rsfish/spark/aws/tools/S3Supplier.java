package net.preibisch.rsfish.spark.aws.tools;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.Serializable;

public class S3Supplier implements Serializable {
    private final String credPublicKey;
    private final String credPrivateKey;
    private final String region;

    public S3Supplier(String credPublicKey, String credPrivateKey, String region) {
        this.credPublicKey = credPublicKey;
        this.credPrivateKey = credPrivateKey;
        this.region = region;
    }

    public S3Client getS3() {
        return S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(credPublicKey, credPrivateKey)))
            .region(Region.of(region))
            .build();
    }

    public String getCredPublicKey() { return credPublicKey; }
    public String getCredPrivateKey() { return credPrivateKey; }
    public String getRegion() { return region; }
}
