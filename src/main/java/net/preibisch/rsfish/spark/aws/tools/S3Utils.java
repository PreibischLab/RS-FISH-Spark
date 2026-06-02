package net.preibisch.rsfish.spark.aws.tools;

import com.google.common.io.CharStreams;
import org.apache.commons.io.FilenameUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class S3Utils {

    public static boolean uploadFile(S3Client s3, File file, S3Uri s3uri) {
        System.out.println("Uploading file: " + file.getAbsolutePath() + " to " + s3uri);
        String bucket = s3uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
        String key = s3uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
        s3.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).build(),
            RequestBody.fromFile(file)
        );
        return true;
    }

    public static File download(S3Client s3, File localFolder, String uri) {
        try {
            S3Uri s3uri = s3.utilities().parseUri(URI.create(uri));
            String s3Bucket = s3uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
            String s3Key =  s3uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
            System.out.println("File  " + uri);
            File localFile = new File(localFolder, s3Key);
            localFile.getParentFile().mkdirs();
            System.out.println("Local file: " + localFile);
            s3.getObject(
                GetObjectRequest.builder().bucket(s3Bucket).key(s3Key).build(),
                Paths.get(localFile.getPath())
            );
            return localFile;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("File: " + uri + " not found!");
        }
        return null;
    }

    public static String get(S3Client s3, String uri) throws IOException {
        S3Uri s3uri = s3.utilities().parseUri(URI.create(uri));
        String s3Bucket = s3uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
        String s3Key =  s3uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
        System.out.println("Getting file: " + s3uri.key() + " from bucket " + s3uri.bucket());
        try (Reader reader = new InputStreamReader(
                s3.getObject(GetObjectRequest.builder().bucket(s3Bucket).key(s3Key).build()))) {
            return CharStreams.toString(reader);
        }
    }

    public static S3Client initS3(String publicKey, String privateKey, String region) {
        return S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(publicKey, privateKey)))
            .region(Region.of(region))
            .build();
    }

    public static void showAll(S3Client s3, String bucketName) {
        ListObjectsV2Response result = s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(bucketName).build());
        for (S3Object obj : result.contents()) {
            System.out.println("* " + obj.key());
        }
    }

    public static List<S3Object> getList(S3Client s3, String bucket) {
        List<S3Object> keyList = new ArrayList<>();
        ListObjectsResponse response = s3.listObjects(
            ListObjectsRequest.builder().bucket(bucket).build());
        keyList.addAll(response.contents());
        while (Boolean.TRUE.equals(response.isTruncated())) {
            response = s3.listObjects(
                ListObjectsRequest.builder()
                    .bucket(bucket)
                    .marker(response.nextMarker())
                    .build());
            keyList.addAll(response.contents());
        }
        return keyList;
    }

    public static List<S3Object> getFilesListSummary(S3Client s3, S3Uri uri, String ext) {
        String s3Bucket = uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + uri));
        String s3Key =  uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + uri));
        List<S3Object> keyList = getList(s3, s3Bucket);
        System.out.println(keyList.size());
        keyList = keyList.stream()
            .filter(os -> os.key().contains(s3Key) && os.key().endsWith(ext))
            .collect(Collectors.toList());
        System.out.println(keyList.size());
        return keyList;
    }

    public static ArrayList<S3Uri> getFilesList(S3Client s3, S3Uri uri, String ext) {
        String s3Bucket = uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + uri));
        String s3Key =  uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + uri));
        List<S3Object> keyList = getList(s3, s3Bucket);
        ArrayList<S3Uri> result = new ArrayList<>();
        for (S3Object os : keyList) {
            if (os.key().contains(s3Key) && os.key().endsWith(ext)) {
                result.add(S3Uri.builder().uri(uri.uri())
                                .key(s3Key)
                                .build());
            }
        }
        return result;
    }

    public static ArrayList<String> getFilesNamesOnly(S3Client s3, S3Uri uri) {
        String s3Bucket = uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + uri));
        String s3Key =  uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + uri));
        List<S3Object> keyList = getList(s3, s3Bucket);
        ArrayList<String> result = new ArrayList<>();
        for (S3Object os : keyList) {
            if (os.key().contains(s3Key)) {
                result.add(FilenameUtils.getBaseName(os.key()));
            }
        }
        return result;
    }

    public static void savePoints(S3Client s3, ArrayList<double[]> allPoints, String output) {
        S3Uri s3uri = s3.utilities().parseUri(URI.create(output));
        String s3Bucket = s3uri.bucket().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
        String s3Key =  s3uri.key().orElseThrow(() -> new IllegalArgumentException("Invalid s3uri: " + s3uri));
        String localFile = new File(s3Key).getAbsolutePath();
        CSVUtils.writeCSV(allPoints, localFile);
        S3Utils.uploadFile(s3, new File(localFile), s3uri);
    }

    public static String getFileName(String uriString) {
        URI uri = URI.create(uriString);
        return new File(uri.getPath()).getName();
    }
}
