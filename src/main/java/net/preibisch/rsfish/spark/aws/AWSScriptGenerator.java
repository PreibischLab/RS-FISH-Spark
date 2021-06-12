package net.preibisch.rsfish.spark.aws;

import benchmark.TextFileAccess;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.common.io.Files;
import net.preibisch.rsfish.spark.aws.tools.S3Utils;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.Callable;

public class AWSScriptGenerator implements Callable<Void> {

    @CommandLine.Option(names = {"-i", "--input"}, required = true, description = "input folder to be processed (need to be ImageJ-readable), e.g. -i '3://bucket-name/folder'")
    private String input ;

    @CommandLine.Option(names = {"-o", "--output"}, required = true, description = "output folder URI")
    private String output ;

    @CommandLine.Option(names = {"-t", "--task_params"}, required = false, description = "Params of the task e.g. intensity, .. ")
    private String taskp ;

    @CommandLine.Option(names = {"-exi", "--extension_input"}, required = false, description = "Extension Input e.g. '.tif'")
    private String exti = ".tif" ;

    @CommandLine.Option(names = {"-exo", "--extension_output"}, required = false, description = "Extension output e.g. '.csv'")
    private String exto = ".csv" ;

    @CommandLine.Option(names = {"-pk", "--publicKey"}, required = false, description = "Credential public key")
    private String credPublicKey;

    @CommandLine.Option(names = {"-pp", "--privateKey"}, required = false, description = "Credential private key")
    private String credPrivateKey;

    @CommandLine.Option(names = {"-reg", "--region"}, required = false, description = "S3 region Exmpl: us-east-1")
    private String region = Regions.US_EAST_1.getName();

    public AWSScriptGenerator() {
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

        ArrayList<AmazonS3URI> allFiles = S3Utils.getFilesList(s3, inputUri, exti);

        File tmpFolder = Files.createTempDir();

        File outputFile = new File(tmpFolder, "output.txt");

        generateScript(outputFile,allFiles);

        return null;
    }

    private void generateScript(File outputFile, ArrayList<AmazonS3URI> allInputs) {
        PrintWriter out = TextFileAccess.openFileWrite( outputFile );

        out.print(taskp);
        for ( int i = 0; i < allInputs.size(); ++i )
        {
            String currentInput = allInputs.get(i).toString();
            String currentOutput = currentInput.replace(input,output).replace(exti,exto);
            out.print(" -i "+currentInput+
                    " -o "+currentOutput);
        }
        out.close();
        System.out.println("File generated: "+outputFile.getAbsolutePath());
    }

    public static void main(String[] args) {
        new CommandLine( new AWSScriptGenerator() ).execute( args );
    }
}
