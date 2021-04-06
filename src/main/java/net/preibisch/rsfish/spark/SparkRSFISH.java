package net.preibisch.rsfish.spark;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import benchmark.TextFileAccess;
import gui.Radial_Symmetry;
import gui.interactive.HelperFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import parameters.RadialSymParams;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import scala.Tuple2;

public class SparkRSFISH implements Callable<Void>
{
	// input file
	@Option(names = {"-i", "--image"}, required = true, description = "N5 container path, e.g. -i /home/smFish.n5")
	private String image = null;

	@Option(names = {"-d", "--dataset"}, required = true, description = "dataset within the N5, e.g. -d 'embryo_5_ch0/c0/s0'")
	private String dataset = null;

	// output file
	@Option(names = {"-o", "--output"}, required = true, description = "output CSV file, e.g. -o 'embryo_5_ch0.csv'")
	private String output = null;

	// processing options
	@Option(names = "--blockSize", required = false, description = "Size of output blocks, e.g. 128,128,64 or 512,512 (default: as listed before)")
	private String blockSizeString = null;
	private int[] blockSize;
	private static int[] defaultBlockSize2d = new int[] { 512, 512 };
	private static int[] defaultBlockSize3d = new int[] { 128, 128, 64 };

	@Option(names = "--min", required = false, description = "Min coordinates of an OPTIONALLY defined subset of the entire image to be processed, e.g. 100,100,200 or 400,500 (default: entire image)")
	private String min = null;
	private long[] minInterval;

	@Option(names = "--max", required = false, description = "Max coordinates of an OPTIONALLY defined subset of the entire image to be processed, e.g. 1000,800,300 or 1400,800 (default: entire image)")
	private String max = null;
	private long[] maxInterval;

	// intensity settings
	@Option(names = {"-i0", "--minIntensity"}, required = true, description = "minimal intensity of the image, if min=max will be computed from the image per-block(!) (default: 0.0)")
	private double minIntensity = 0.0;

	@Option(names = {"-i1", "--maxIntensity"}, required = true, description = "maximal intensity of the image, if min=max will be computed from the image per-block(!) (default: 0.0)")
	private double maxIntensity = 0.0;

	// RS settings
	@Option(names = {"-a", "--anisotropy"}, required = true, description = "the anisotropy factor (scaling of z relative to xy, can be determined using the anisotropy plugin), e.g. -a 0.8 (default: 1.0)")
	private double anisotropy = 1.0;

	@Option(names = {"-r", "--ransac"}, required = false, description = "which RANSAC type to use, 0 == No RANSAC, 1 == RANSAC, 2 == Multiconsensus RANSAC (default: 1 - RANSAC)")
	private int ransac = 1;

	@Option(names = {"-s", "--sigma"}, required = false, description = "sigma for Difference-of-Gaussian (DoG) (default: 1.5)")
	private double sigma = 1.5;

	@Option(names = {"-t", "--threshold"}, required = false, description = "threshold for Difference-of-Gaussian (DoG) (default: 0.007)")
	private double threshold = 0.007;

	@Option(names = {"-sr", "--supportRadius"}, required = false, description = "support region radius for RANSAC (default: 3)")
	private int supportRadius = 3;

	@Option(names = {"-ir", "--inlierRatio"}, required = false, description = "Minimal ratio of gradients that agree on a spot (inliers) for RANSAC (default: 0.1)")
	private double inlierRatio = 0.1;

	@Option(names = {"-e", "--maxError"}, required = false, description = "Maximum error for intersecting gradients of a spot for RANSAC (default: 1.5)")
	private double maxError = 1.5;

	@Option(names = {"-it", "--intensityThreshold"}, required = false, description = "intensity threshold for localized spots (default: 0.0)")
	private double intensityThreshold = 0.0;

	// background method
	@Option(names = {"-bg", "--background"}, required = false, description = "Background subtraction method, 0 == None, 1 == Mean, 2==Median, 3==RANSAC on Mean, 4==RANSAC on Median (default: 0 - None)")
	private int background = 0;

	@Option(names = {"-bge", "--backgroundMaxError"}, required = false, description = "RANSAC-based background subtraction max error (default: 0.05)")
	private double backgroundMaxError = 0.05;

	@Option(names = {"-bgir", "--backgroundMinInlierRatio"}, required = false, description = "RANSAC-based background subtraction min inlier ratio (default: 0.75)")
	private double backgroundMinInlierRatio = 0.75;

	// only for multiconsensus RANSAC
	@Option(names = {"-rm", "--ransacMinNumInliers"}, required = false, description = "minimal number of inliers for Multiconsensus RANSAC (default: 20)")
	private int ransacMinNumInliers = 20;

	@Option(names = {"-rn1", "--ransacNTimesStDev1"}, required = false, description = "n: initial #inlier threshold for new spot [avg - n*stdev] for Multiconsensus RANSAC (default: 8.0)")
	private double ransacNTimesStDev1 = 8.0;

	@Option(names = {"-rn2", "--ransacNTimesStDev2"}, required = false, description = "n: final #inlier threshold for new spot [avg - n*stdev] for Multiconsensus RANSAC (default: 6.0)")
	private double ransacNTimesStDev2 = 6.0;

	@Override
	public Void call() throws Exception
	{
		final N5Reader n5 = new N5FSReader( image );
		final DatasetAttributes att = n5.getDatasetAttributes( dataset );
		final long[] dimensions = att.getDimensions();

		System.out.println( "N5 dataset dimensionality: " + att.getNumDimensions() );
		System.out.println( "N5 dataset size: " + Util.printCoordinates( dimensions ));

		this.minInterval = new long[ att.getNumDimensions() ];
		this.maxInterval = new long[ att.getNumDimensions() ];

		if ( this.min != null )
			parseCSLongArray( min, minInterval );

		if ( this.max != null )
			parseCSLongArray( max, maxInterval );
		else
			for ( int d = 0; d < maxInterval.length; ++d )
				this.maxInterval[ d ] = dimensions[ d ] - 1;

		final Interval interval = new FinalInterval(minInterval, maxInterval);

		System.out.println( "Processing interval: " + Util.printInterval( interval ));

		if ( this.blockSizeString == null )
		{
			if ( att.getNumDimensions() == 2 )
				this.blockSize = defaultBlockSize2d.clone();
			else
				this.blockSize = defaultBlockSize3d.clone();
		}
		else
		{
			this.blockSize = new int[ att.getNumDimensions() ];
			parseCSIntArray( blockSizeString, blockSize );
		}

		System.out.println( "Processing blocksize: " + Util.printCoordinates( blockSize ));

		// create parameter object
		final RadialSymParams params = new RadialSymParams();

		// general
		params.anisotropyCoefficient = anisotropy;
		params.useAnisotropyForDoG = true;
		params.ransacSelection = ransac; //"No RANSAC", "RANSAC", "Multiconsensus RANSAC"

		if ( minIntensity == maxIntensity )
		{
			params.min = Double.NaN;
			params.max = Double.NaN;
			params.autoMinMax = true;
		}
		else
		{
			params.min = minIntensity;
			params.max = maxIntensity;
			params.autoMinMax = false;
		}

		// multiconsensus
		if ( ransac == 2 )
		{
			params.minNumInliers = ransacMinNumInliers;
			params.nTimesStDev1 = ransacNTimesStDev1;
			params.nTimesStDev2 = ransacNTimesStDev2;
		}

		// advanced
		params.sigma = (float)sigma;
		params.threshold = (float)threshold;
		params.supportRadius = supportRadius;
		params.inlierRatio = (float)inlierRatio;
		params.maxError = (float)maxError;
		params.intensityThreshold = intensityThreshold;
		params.bsMethod = background;
		params.bsMaxError = (float)backgroundMaxError;
		params.bsInlierRatio = (float)backgroundMinInlierRatio;
		params.resultsFilePath = output;

		final SparkConf sparkConf = new SparkConf().setAppName(SparkRSFISH.class.getSimpleName());
		//sparkConf.set("spark.driver.bindAddress", "127.0.0.1");
		final JavaSparkContext sc = new JavaSparkContext( sparkConf );

		// only 2 pixel overlap necessary to find local max/min to start - we then anyways load the full underlying image for each block
		final ArrayList< Block > blocks = Block.splitIntoBlocks( interval, blockSize );

		final String imageName = image;
		final String datasetName = dataset;
		final long[] min = minInterval.clone();
		final long[] max = maxInterval.clone();

		// do not store local results
		params.resultsFilePath = "";

		// single-threaded within each block
		params.numThreads = 1;

		final JavaRDD<Block> rddIds = sc.parallelize( blocks );
		final JavaPairRDD<Block, ArrayList<double[]> > rddResults = rddIds.mapToPair( block -> {

			System.out.println( "Processing block " + block.id() );

			final N5Reader n5Local = new N5FSReader( imageName );
			final RandomAccessibleInterval img = N5Utils.open( n5Local, datasetName );

			HelperFunctions.headless = true;
			ArrayList<double[]> points = Radial_Symmetry.runRSFISH(
					(RandomAccessible)(Object)Views.extendMirrorSingle( img ),
					new FinalInterval(min, max),
					block.createInterval(),
					params );

			System.out.println( "block " + block.id() + " found " + points.size() + " spots.");

			return new Tuple2<>(block, points );
		});

		rddResults.cache();

		final ArrayList<Tuple2<Block, ArrayList<double[]>> > results = new ArrayList<>();
		results.addAll( rddResults.collect() );

		sc.close();

		final ArrayList<double[]> allPoints = new ArrayList<>();

		for ( final Tuple2<Block, ArrayList<double[]>> r : results )
		{
			if ( r != null && r._2() != null )
				allPoints.addAll( r._2() );
		}

		System.out.println( "total points: " + allPoints.size() );

		writeCSV( allPoints, output );

		return null;
	}

	// taken from: hot-knife repository (Saalfeld)
	protected static final boolean parseCSIntArray(final String csv, final int[] array) {

		final String[] stringValues = csv.split(",");
		if (stringValues.length != array.length)
			return false;
		try {
			for (int i = 0; i < array.length; ++i)
				array[i] = Integer.parseInt(stringValues[i]);
		} catch (final NumberFormatException e) {
			e.printStackTrace(System.err);
			return false;
		}
		return true;
	}

	public static void writeCSV( final ArrayList<double[]> points, final String file )
	{
		PrintWriter out = TextFileAccess.openFileWrite( file );

		if ( points.get( 0 ).length == 4 )
			out.println("x,y,z,t,c,intensity");
		else
			out.println("x,y,t,c,intensity");

		for (double[] spot : points) {
			for (int d = 0; d < spot.length - 1; ++d)
				out.print( String.format(java.util.Locale.US, "%.4f", spot[ d ] ) + "," );

			out.print( "1,1," );

			out.println(String.format(java.util.Locale.US, "%.4f", spot[ spot.length - 1 ] ) );
		}

		System.out.println(points.size() + " spots written to " + file );
		out.close();
	}

	// taken from: hot-knife repository (Saalfeld)
	protected static final boolean parseCSLongArray(final String csv, final long[] array) {

		final String[] stringValues = csv.split(",");
		if (stringValues.length != array.length)
			return false;
		try {
			for (int i = 0; i < array.length; ++i)
				array[i] = Long.parseLong(stringValues[i]);
		} catch (final NumberFormatException e) {
			e.printStackTrace(System.err);
			return false;
		}
		return true;
	}

	public static final void main(final String... args) {
		new CommandLine( new SparkRSFISH() ).execute( args );
	}
}
