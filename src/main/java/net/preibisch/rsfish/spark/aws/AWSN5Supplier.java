package net.preibisch.rsfish.spark.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;

import java.io.IOException;
import java.io.Serializable;

public class AWSN5Supplier implements Serializable {
    private final String credPublicKey;
    private final String bucketName;
    private final String credPrivateKey;
    private final String file;

    public AWSN5Supplier( String bucketName, String file, String credPublicKey, String credPrivateKey) {
        this.credPublicKey = credPublicKey;
        this.credPrivateKey = credPrivateKey;
        this.bucketName = bucketName;
        this.file = file;
    }

    public AmazonS3 getS3() {
        AWSCredentials credentials = new BasicAWSCredentials(
                credPublicKey, credPrivateKey
        );
        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.EU_CENTRAL_1)
                .build();
    }

    public N5Reader getN5() throws IOException {
        return new N5AmazonS3Reader(getS3(), bucketName, file);
    }

}
