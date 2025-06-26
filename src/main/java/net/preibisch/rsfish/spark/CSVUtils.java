package net.preibisch.rsfish.spark;

import java.io.PrintWriter;
import java.util.List;

import benchmark.TextFileAccess;
import scala.Tuple2;

public class CSVUtils {

    public static void writeCSV(final List<Tuple2<Block, List<double[]>>> allPointsByBlocks, final String file) {
        PrintWriter out = TextFileAccess.openFileWrite( file );

        // output CSV header
        if ( allPointsByBlocks.get( 0 )._2.size() == 4 )
            out.println("x,y,z,t,c,intensity");
        else
            out.println("x,y,t,c,intensity");

        int totalSpots = 0;
        for (Tuple2<Block, List<double[]>> blockPoints : allPointsByBlocks) {
            totalSpots += writeBlockSpots(blockPoints, out);
        }

        System.out.println(totalSpots + " spots written to " + file );
        out.close();
    }

    private static int writeBlockSpots(Tuple2<Block, List<double[]>> blockPoints, PrintWriter out) {
        Block b = blockPoints._1();
        List<double[]> spots = blockPoints._2();
        int timeIndex;
        int channel;
        if (b.numDimensions() > 3) {
            channel = (int) (b.min()[3] + 1); // channel is 1 indexed
        } else {
            channel = 1;
        }
        if (b.numDimensions() > 4) {
            timeIndex = (int) (b.min()[4] + 1); // timepoint is also 1 indexed
        } else {
            timeIndex = 1;
        }

        for (double[] spot : spots) {

            // output x,y,z
            for (int d = 0; d < spot.length - 1; ++d)
                out.print( String.format(java.util.Locale.US, "%.4f", spot[ d ] ) + "," );

            out.printf( "%d,%d,", timeIndex, channel );

            // output intensity
            out.println(String.format(java.util.Locale.US, "%.4f", spot[ spot.length - 1 ] ) );
        }

        return spots.size();
    }

}
