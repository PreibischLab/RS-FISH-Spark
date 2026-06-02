package net.preibisch.rsfish.spark.aws.tools;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;

public class AWSN5Supplier implements Serializable {

    private final String credPublicKey;
    private final String credPrivateKey;
    private final String file;

    public AWSN5Supplier(String file, String credPublicKey, String credPrivateKey) {
        this.credPublicKey = credPublicKey;
        this.credPrivateKey = credPrivateKey;
        this.file = file;
        System.out.println("Supplier init " + file);
    }

    public S3Client getS3() {
        return S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(credPublicKey, credPrivateKey)))
            .region(Region.EU_CENTRAL_1)
            .build();
    }

    public N5Reader getN5() throws IOException {
        return new N5Factory().openReader(file);
    }

    public boolean exists() {
        S3Client s3 = getS3();
        S3Uri uri = s3.utilities().parseUri(URI.create(file));
        String bucket = uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid S3 uri: " + file));
        String key = uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid S3 uri: " + file));

        return getS3().listObjectsV2(
            ListObjectsV2Request.builder().bucket(bucket).prefix(key).build()
        ).keyCount() > 0;
    }
}
