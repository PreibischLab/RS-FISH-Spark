package net.preibisch.rsfish.spark.aws.tools;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.io.CharStreams;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class S3Utils {
    public static boolean uploadFile(AmazonS3 s3, File file, AmazonS3URI s3uri) throws InterruptedException {
        System.out.println("Uploading file: " + file.getAbsolutePath() + " to " + s3uri.getURI());
        TransferManager tm = TransferManagerBuilder.standard().withS3Client(s3).build();
        Upload upload = tm.upload(s3uri.getBucket(), s3uri.getKey(), file);
        upload.waitForCompletion();
        return true;
    }

    public static File download(AmazonS3 s3, File localFolder, String uri) {
        try {
            TransferManager tm = TransferManagerBuilder.standard().withS3Client(s3).build();
            AmazonS3URI amazonS3URI = new AmazonS3URI(uri);
            System.out.println("File  " + uri);
            File localFile = new File(localFolder, amazonS3URI.getKey());
            System.out.println("Local file: " + localFile);
            Download upload = tm.download(amazonS3URI.getBucket(), amazonS3URI.getKey(), localFile);
            upload.waitForCompletion();
            return localFile;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("File: " + uri + " not found!");
        }
        return null;
    }

    public static String get(AmazonS3 s3, String uri) throws IOException {
        AmazonS3URI amazonS3URI = new AmazonS3URI(uri);
        GetObjectRequest request = new GetObjectRequest(amazonS3URI.getBucket(), amazonS3URI.getKey());
        System.out.println("Getting file: " + request.getKey() + " from bucket " + request.getBucketName());
        S3Object object = s3.getObject(request);
        InputStream objectData = object.getObjectContent();
        String text;
        try (Reader reader = new InputStreamReader(objectData)) {
            text = CharStreams.toString(reader);
        }
        objectData.close();
        return text;
    }

    public static AmazonS3 initS3(String publicKey, String privateKey, String region) {
        AWSCredentials credentials = new BasicAWSCredentials(publicKey, privateKey);
        return initS3(credentials, Regions.fromName(region));
    }

    public static AmazonS3 initS3(AWSCredentials credentials, Regions region) {
        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();
    }

    public static void showAll(AmazonS3 s3, String bucketName) {
        ListObjectsV2Result result = s3.listObjectsV2(bucketName);
        List<S3ObjectSummary> objects = result.getObjectSummaries();
        for (S3ObjectSummary os : objects) {
            System.out.println("* " + os.getKey());
        }
    }

    public static void savePoints(AmazonS3 s3, ArrayList<double[]> allPoints, String output) throws InterruptedException {
        AmazonS3URI s3uri = new AmazonS3URI(output);
        String localFile = new File(s3uri.getKey()).getAbsolutePath();
        CSVUtils.writeCSV(allPoints, localFile);
        //save output to s3
        S3Utils.uploadFile(s3, new File(output), s3uri);
    }


}
