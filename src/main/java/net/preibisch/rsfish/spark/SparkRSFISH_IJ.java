package net.preibisch.rsfish.spark;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import benchmark.TextFileAccess;
import gui.Radial_Symmetry;
import gui.interactive.HelperFunctions;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.view.Views;
import parameters.RadialSymParams;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import scala.Tuple2;

public class SparkRSFISH_IJ implements Callable<Void>
{
	// TODO: support many images (many N5s and many ImageJ-readable ones)

	// input file
	@Option(names = {"-i", "--image"}, required = true, description = "input image(s) to be processed (need to be ImageJ-readable), e.g. -i 'embryo_5_ch0.tif'")
	private List< String > image = null;

	// output file
	@Option(names = {"-o", "--output"}, required = true, description = "output CSV file, e.g. -o 'embryo_5_ch0.csv'")
	private List< String > output = null;

	// intensity settings
	@Option(names = {"-i0", "--minIntensity"}, required = false, description = "minimal intensity of the image, if min=max will be computed from the image per-block(!) (default: 0.0)")
	private double minIntensity = 0.0;

	@Option(names = {"-i1", "--maxIntensity"}, required = false, description = "maximal intensity of the image, if min=max will be computed from the image per-block(!) (default: 0.0)")
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

	@Option(names = {"-ime", "--intensityMethod"}, required = false, description = "Intensity calculation method, 0 == Linear Interpolation, 1 == Gaussian fit (on inlier pixels), 2 == Integrate spot intensities (on candidate pixels) (default: 0 - Linear Interpolation)")
	private int intensityMethod = 0;

	@Override
	public Void call() throws Exception
	{
		if ( image.size() != output.size() )
		{
			System.out.println( "Number of input images (" + image.size() + ") and csv file outputs (" + output.size() + ") does not match. Stopping.");
			return null;
		}

		System.out.println( "Processing " + image.size() + " images ... " );

		final List< Tuple2< String, String > > toProcess =
				IntStream
					.range(0, image.size())
					.mapToObj(i -> new Tuple2<>(image.get(i), output.get(i)))
					.collect(Collectors.toList());

		final double minIntensity = this.minIntensity;
		final double maxIntensity = this.maxIntensity;

		// RS settings
		final double anisotropy = this.anisotropy;
		final int ransac = this.ransac;
		final double sigma = this.sigma;
		final double threshold = this.threshold;
		final int supportRadius = this.supportRadius;
		final double inlierRatio = this.inlierRatio;
		final double maxError = this.maxError;
		final double intensityThreshold = this.intensityThreshold;

		// background method
		final int background = this.background;
		final int intensityMethod = this.intensityMethod;
		final double backgroundMaxError = this.backgroundMaxError;
		final double backgroundMinInlierRatio = this.backgroundMinInlierRatio;

		// only for multiconsensus RANSAC
		final int ransacMinNumInliers = this.ransacMinNumInliers;
		final double ransacNTimesStDev1 = this.ransacNTimesStDev1;
		final double ransacNTimesStDev2 = this.ransacNTimesStDev2;

		final SparkConf sparkConf = new SparkConf().setAppName(SparkRSFISH_IJ.class.getSimpleName());
//		.setMaster("local[*]");
//		sparkConf.set("spark.driver.bindAddress", "127.0.0.1");
		final JavaSparkContext sc = new JavaSparkContext( sparkConf );

		final JavaRDD<Tuple2< String, String > > rddIds = sc.parallelize( toProcess );

		HelperFunctions.headless = true;

		rddIds.foreach( input -> {

			System.out.println( "Processing image " + input._1() + ", result written to " + input._2() );

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
			params.intensityMethod = intensityMethod;
			params.bsMaxError = (float)backgroundMaxError;
			params.bsInlierRatio = (float)backgroundMinInlierRatio;
			params.resultsFilePath = input._2();

			// single-threaded within each block
			params.numThreads = 1;

			final RandomAccessibleInterval img = ImagePlusImgs.from( new ImagePlus( input._1() ) );

			final int numSpots = Radial_Symmetry.runRSFISH(
					(RandomAccessible)(Object)Views.extendMirrorSingle( img ),
					new FinalInterval( img ),
					new FinalInterval( img ),
					params ).size();

			System.out.println( "image " + input._1() + " found "  + numSpots + " spots.");

		});

		sc.close();

		System.out.println( "done." );
		return null;
	}

	public static final void main(final String... args) {

//		PrintWriter out = TextFileAccess.openFileWrite( "/Users/spreibi/Documents/BIMSB/Publications/radialsymmetry/cmdline.txt" );
//
//		out.println("-i0 4166.0\n" +
//				"-i1 46562.0\n" +
//				"-a 1.0");
//		for ( int i = 0; i < 10000; ++i )
//		{
//			out.println("-i '/Users/spreibi/Documents/BIMSB/Publications/radialsymmetry/N2_702_cropped_1620 (high SNR)_ch0.tif'\n" +
//					"-o '/Users/spreibi/Documents/BIMSB/Publications/radialsymmetry/N2_702_cropped_1620 (high SNR)_ch0_" + i + ".tif.csv'");
//		}

//		out.close();
//		String[] arg = new String[]{
//				"-i0", "4166.0",
//				"-i1", "46562.0",
//				"-a", "1.0",
//				"-i", "/Users/zouinkhim/Desktop/java/RS-FISH-Spark/src/main/resources/multiple_dots_2D.tif",
//				"-o", "/Users/zouinkhim/Desktop/java/RS-FISH-Spark/src/main/resources/multiple_dots_2D.csv"
//		};

		new CommandLine( new SparkRSFISH_IJ() ).execute( args );
	}
}
