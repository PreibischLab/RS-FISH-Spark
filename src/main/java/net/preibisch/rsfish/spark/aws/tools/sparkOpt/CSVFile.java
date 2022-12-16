package net.preibisch.rsfish.spark.aws.tools.sparkOpt;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class CSVFile {

    private static final String DEFAULT_DELIMITER = ",";
    private final String delimiter;
    private final File file;

    public CSVFile(File file) {
        this(file, DEFAULT_DELIMITER);
    }

    public CSVFile(File file, String delimiter) {
        this.delimiter = delimiter;
        this.file = file;
    }

    public static void main(String[] args) throws FileNotFoundException {
        CSVFile csv = new CSVFile(new File("/Users/Marwan/Downloads/mdh1_data.csv"));
        csv.print();

//        //sets the delimiter pattern
//        while (sc.hasNext())  //returns a boolean value
//        {
//            System.out.print(sc.next());  //find and returns the next complete token from this scanner
//        }
//        sc.close();
    }

    private void print() throws FileNotFoundException {
        Scanner sc = getScanner();
        while (sc.hasNextLine())
            System.out.println(sc.nextLine());
        sc.close();
    }

    private Scanner getScanner() throws FileNotFoundException {
        Scanner sc = new Scanner(file);
        sc.useDelimiter(",");
        return sc;
    }


//    public String[] getHeaders(){
//
//    }
}
