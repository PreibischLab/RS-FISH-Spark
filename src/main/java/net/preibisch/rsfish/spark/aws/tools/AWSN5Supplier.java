package net.preibisch.rsfish.spark.aws.tools;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;

import java.io.IOException;
import java.io.Serializable;

public class AWSN5Supplier implements Serializable {
    private final String credPublicKey;
    private final String credPrivateKey;
    private final String file;

    public AWSN5Supplier(String file, String credPublicKey, String credPrivateKey) {
        this.credPublicKey = credPublicKey;
        this.credPrivateKey = credPrivateKey;
        this.file = file;

        AmazonS3URI uri = new AmazonS3URI(file);
        System.out.println("Supplier init " + file + " bucket: " + uri.getBucket() + " file: " + uri.getKey());
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
        AmazonS3 s3 = getS3();
        System.out.println("Got S3: " + s3.getRegionName());

        return new N5AmazonS3Reader(s3, new AmazonS3URI(file).getBucket(),new AmazonS3URI(file).getKey());
    }

    public boolean exists() {
        AmazonS3URI uri = new AmazonS3URI(file);
        if (getS3().listObjectsV2(uri.getBucket(), uri.getKey()).getKeyCount() > 0)
            return true;
        return false;
    }
}
