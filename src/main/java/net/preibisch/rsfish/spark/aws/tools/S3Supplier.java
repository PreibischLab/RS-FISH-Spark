package net.preibisch.rsfish.spark.aws.tools;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.Serializable;

public class S3Supplier implements Serializable {
    private final String credPublicKey;
    private final String credPrivateKey;
    private final String region;

    public S3Supplier( String credPublicKey, String credPrivateKey, String region) {
        this.credPublicKey = credPublicKey;
        this.credPrivateKey = credPrivateKey;
        this.region = region;
    }

    public AmazonS3 getS3() {
        AWSCredentials credentials = new BasicAWSCredentials(
                credPublicKey, credPrivateKey
        );
        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.fromName(region))
                .build();
    }

    public String getCredPublicKey() {
        return credPublicKey;
    }

    public String getCredPrivateKey() {
        return credPrivateKey;
    }

    public String getRegion() {
        return region;
    }
}
