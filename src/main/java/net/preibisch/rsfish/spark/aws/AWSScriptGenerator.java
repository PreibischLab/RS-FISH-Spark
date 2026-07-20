package net.preibisch.rsfish.spark.aws;

import benchmark.TextFileAccess;
import com.google.common.io.Files;
import net.preibisch.rsfish.spark.aws.tools.S3Utils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import org.apache.commons.io.FilenameUtils;
import picocli.CommandLine;
import software.amazon.awssdk.services.s3.S3Uri;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class AWSScriptGenerator implements Callable<Void> {

    @CommandLine.Option(names = {"-i", "--input"}, required = true, description = "input folder to be processed (need to be ImageJ-readable), e.g. -i '3://bucket-name/folder'")
    private String input;

    @CommandLine.Option(names = {"-o", "--output"}, required = true, description = "output folder URI")
    private String output;

    @CommandLine.Option(names = {"-t", "--task_params"}, required = false, description = "Params of the task e.g. intensity, .. ")
    private String taskp;

    @CommandLine.Option(names = {"-exi", "--extension_input"}, required = false, description = "Extension Input e.g. '.tif'")
    private String exti = ".tif";

    @CommandLine.Option(names = {"-exo", "--extension_output"}, required = false, description = "Extension output e.g. '.csv'")
    private String exto = ".csv";

    @CommandLine.Option(names = {"-pk", "--publicKey"}, required = false, description = "Credential public key")
    private String credPublicKey;

    @CommandLine.Option(names = {"-pp", "--privateKey"}, required = false, description = "Credential private key")
    private String credPrivateKey;

    @CommandLine.Option(names = {"-reg", "--region"}, required = false, description = "S3 region Exmpl: us-east-1")
    private String region = "us-east-1";

    @CommandLine.Option(names = {"-c", "--checkoutput"}, required = false, description = "Check existent outputs, to use if you have already processed some files 0 : false , 1 : true")
    private boolean checkOutput = false;

    @CommandLine.Option(names = {"-ch", "--chunks"}, required = false, description = "Split task in chunks sizes default: 0")
    private int chunkSize = 0;
    private String prefix = "setting_2";
    private boolean checkGoodEmbroysFile = false;
    private static final String GoodEmbryosPath = "/Users/Marwan/Downloads/filenames_good_embryos.txt";

    public AWSScriptGenerator() {
    }

    @Override
    public Void call() throws Exception {
        S3Client s3 = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(credPublicKey, credPrivateKey)))
                .region(Region.of(region))
                .build();

        S3Uri inputUri = s3.utilities().parseUri(URI.create(input));

        ArrayList<S3Uri> allFiles = S3Utils.getFilesList(s3, inputUri, exti);
        System.out.println("Total raw size = " + allFiles.size());
        if (checkOutput)
            allFiles = getDifference(allFiles, S3Utils.getFilesNamesOnly(s3, s3.utilities().parseUri(URI.create(output))));

        if (checkGoodEmbroysFile)
            allFiles = getRightFiles(allFiles, new File(GoodEmbryosPath));



        File tmpFolder = Files.createTempDir();
        if (chunkSize == 0) {
            File outputFile = new File(tmpFolder, prefix + ".txt");
            generateScript(outputFile, allFiles);
        } else {
            int total = allFiles.size();
            int nbChunks = total / chunkSize;
            if (total % chunkSize > 0)
                nbChunks++;

            for (int i = 0; i < nbChunks; i++) {
                int startPos = i * chunkSize;
                int endPos = (i + 1) * chunkSize;
                if (endPos >= total)
                    endPos = total - 1;
                File outputFile = new File(tmpFolder, prefix + "_" + i + ".txt");
                System.out.println("[" + startPos + "-" + endPos + "]-" + outputFile.getAbsolutePath());
                generateScript(outputFile, allFiles.subList(startPos, endPos));
            }
        }

        return null;
    }

    private ArrayList<S3Uri> getRightFiles(ArrayList<S3Uri> allFiles, File file) throws IOException {

        List<String> rightFiles = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(GoodEmbryosPath));
        String line = br.readLine();
        while ((line = br.readLine()) != null)
        {
            String elm = line.split(",")[1].replace("\"", "");
            rightFiles.add(elm);
        }
        allFiles.removeIf(n -> (
                n.key().map(fn -> !rightFiles.contains(FilenameUtils.getName(fn).replaceFirst("(^c0_|c1_|c2_|c3_)", ""))).orElse(false)
        ));
        System.out.println("Size after getting only right files = " + allFiles.size());
        return allFiles;
    }

    private ArrayList<S3Uri> getDifference(ArrayList<S3Uri> allFiles, ArrayList<String> filesList) {
        allFiles.removeIf(n -> (
                n.key().map(fn -> filesList.contains(FilenameUtils.getBaseName(fn))).orElse(false)
        ));
        System.out.println("Size after clean = " + allFiles.size());
        return allFiles;
    }

    private void generateScript(File outputFile, List<S3Uri> allInputs) {
        PrintWriter out = TextFileAccess.openFileWrite(outputFile);

        out.print(taskp);
        for (int i = 0; i < allInputs.size(); ++i) {
            String currentInput = allInputs.get(i).toString();
            String currentOutput = currentInput.replace(input, output).replace(exti, exto);
            out.print(" -i " + currentInput +
                    " -o " + currentOutput);
        }
        out.close();
        System.out.println("File generated: " + outputFile.getAbsolutePath());
    }

    public static void main(String[] args) {
        new CommandLine(new AWSScriptGenerator()).execute(args);
    }
}
