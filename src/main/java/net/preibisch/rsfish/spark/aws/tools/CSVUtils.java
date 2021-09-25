package net.preibisch.rsfish.spark.aws.tools;

import benchmark.TextFileAccess;

import java.io.PrintWriter;
import java.util.ArrayList;

public class CSVUtils {
    public static void writeCSV(final ArrayList<double[]> points, final String file) {
        PrintWriter out = TextFileAccess.openFileWrite(file);

        if (points.get(0).length == 4)
            out.println("x,y,z,t,c,intensity");
        else
            out.println("x,y,t,c,intensity");

        for (double[] spot : points) {
            for (int d = 0; d < spot.length - 1; ++d)
                out.print(String.format(java.util.Locale.US, "%.4f", spot[d]) + ",");

            out.print("1,1,");

            out.println(String.format(java.util.Locale.US, "%.4f", spot[spot.length - 1]));
        }

        System.out.println(points.size() + " spots written to " + file);
        out.close();
    }
}
