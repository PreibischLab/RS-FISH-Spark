package net.preibisch.rsfish.spark.aws.tools;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;

import java.io.File;

public class FileUploader {
    public static boolean uploadFile(AmazonS3 s3, File file, AmazonS3URI s3uri) throws InterruptedException {
        System.out.println("Uploading file: " + file.getAbsolutePath() + " to " + s3uri.getURI());
        TransferManager tm = TransferManagerBuilder.standard().withS3Client(s3).build();
        Upload upload = tm.upload(s3uri.getBucket(), s3uri.getKey(), file);
        upload.waitForCompletion();
        return true;
    }
}
