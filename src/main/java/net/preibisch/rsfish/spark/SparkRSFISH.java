package net.preibisch.rsfish.spark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;

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
	public enum StorageType { N5, ZARR, HDF5 }

	// input file
	@Option(names = {"-i", "--image"}, required = true, description = "N5/HDF5/ZARR container path, e.g. -i '/home/smFish.n5' or -i '/home/smFish.h5' or -i '/home/smFish.zarr'")
	private String image = null;

	@Option(names = {"-d", "--dataset"}, required = true, description = "dataset within the N5/HDF5/ZARR, e.g. -d 'embryo_5_ch0/c0/s0'")
	private String dataset = null;

	// output file
	@Option(names = {"-o", "--output"}, required = true, description = "output CSV file, e.g. -o 'embryo_5_ch0.csv'")
	private String output = null;

	@Option(names = {"--storage"}, required = false, showDefaultValue = CommandLine.Help.Visibility.ALWAYS, description = "Dataset input type, currently supported N5, ZARR, HDF5")
	private StorageType storageType = null;

	// processing options
	@Option(names = "--blockSize", required = false, description = "Blocksize for processing, e.g. 128,128,64 or 512,512 (default: as listed under e.g.)")
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
		final N5Reader blockedFSReader;

		// the enum needs to be final to be serializable
		final StorageType storageLocal;

		if ( StorageType.N5.equals(storageType) ||
			image.toLowerCase().endsWith(".n5") ) {
			System.out.printf("Instantiate N5 FS reader for %s\n", image);
			storageLocal = StorageType.N5;
			blockedFSReader = new N5FSReader(image);
		} else if ( StorageType.ZARR.equals(storageType) ||
					image.toLowerCase().endsWith(".zarr") ) {
			System.out.printf("Instantiate ZARR reader for %s\n", image);
			storageLocal = StorageType.ZARR;
			blockedFSReader = new N5ZarrReader(image);
		} else if ( StorageType.HDF5.equals(storageType) ||
					image.toLowerCase().endsWith(".hdf5") ||
					image.toLowerCase().endsWith(".h5") ) {
			System.out.printf("Instantiate HDF5 reader for %s\n", image);
			storageLocal = StorageType.HDF5;
			blockedFSReader = new N5HDF5Reader(image);
		} else {
			throw new IllegalArgumentException("Unsupported storage " + image +
					", storageType " + storageType);
		}

		System.out.printf("Image: %s:%s => exists: %b\n", image, dataset, blockedFSReader.datasetExists(dataset));
		final DatasetAttributes att = blockedFSReader.getDatasetAttributes( dataset );
		final long[] dimensions = att.getDimensions();

		System.out.printf( "N5/HDF5/ZARR dataset dimensionality: %d\n", att.getNumDimensions() );
		System.out.printf( "N5/HDF5/ZARR dataset size: %s (%s)\n", Util.printCoordinates( dimensions ), dimensions);

		minInterval = new long[ att.getNumDimensions() ];
		maxInterval = new long[ att.getNumDimensions() ];

		if ( this.min != null )
			parseCSLongArray( min, minInterval );

		if ( this.max != null )
			parseCSLongArray(max, maxInterval);
		else
			for ( int d = 0; d < maxInterval.length; ++d )
				maxInterval[ d ] = dimensions[ d ] - 1;

		final Interval interval = new FinalInterval(minInterval, maxInterval);

		System.out.println( "Processing interval: " + Util.printInterval( interval ));

		if ( this.blockSizeString == null )
		{
			if ( att.getNumDimensions() == 2 )
				this.blockSize = defaultBlockSize2d.clone();
			else if (att.getNumDimensions() == 3 )
				this.blockSize = defaultBlockSize3d.clone();
			else {
				this.blockSize = new int[ att.getNumDimensions() ];
				Arrays.fill(this.blockSize, 1);
				System.arraycopy(defaultBlockSize3d, 0, this.blockSize, 0, defaultBlockSize3d.length);
			}
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

		final JavaSparkContext sc = new JavaSparkContext( sparkConf );

		// only 2 pixel overlap necessary to find local max/min to start - we then anyways load the full underlying image for each block
		final List< Block > blocks = Block.splitIntoBlocks( interval, blockSize, 2 );
		System.out.printf("Split %s interval into %d %s blocks\n", Util.printInterval(interval), blocks.size(), Arrays.toString(blockSize));

		final String imageName = image;
		final String datasetName = dataset;
		// RS-FISH only supports up to 3D so we only need min and max spatial coordinates for processing
		final long[] minCoords = minInterval.length > 3 ? Arrays.copyOf(minInterval, 3) : minInterval.clone();
		final long[] maxCoords = maxInterval.length > 3 ? Arrays.copyOf(maxInterval, 3) : maxInterval.clone();

		// do not store local results
		params.resultsFilePath = "";

		// single-threaded within each block
		params.numThreads = 1;

		final JavaRDD<Block> rddIds = sc.parallelize( blocks );
		final JavaPairRDD<Block, List<double[]> > rddResults = rddIds.mapToPair( block -> {

			System.out.println( "Processing block " + block.id() );

			final N5Reader localBlockReader;

			if ( StorageType.N5.equals(storageLocal) )
				localBlockReader = new N5FSReader(imageName);
			else if ( StorageType.ZARR.equals(storageLocal) )
				localBlockReader = new N5ZarrReader(imageName);
			else
				// the only left option is HDF5 - otherwise an exception would have been thrown earlier
				localBlockReader = new N5HDF5Reader(imageName);

			final RandomAccessibleInterval<?> img = N5Utils.open( localBlockReader, datasetName );

			System.out.printf(
					"Read block %s from %s image\n",
					Util.printInterval(block.createInterval()), Util.printInterval(img)
			);

			// RS-FISH only supports 3D images so if image is >3D take a 3D slice
			RandomAccessibleInterval<?> img3D = img;
			if ( img.numDimensions() > 3 ) {
				// I assume the channel and timepoint block dimensions are just 1,
				// so I am getting the corresponding spatial 3D hyperslice
				for (int d = img.numDimensions() - 1; d >= 3; d--) {
					img3D = Views.hyperSlice(img3D, d, block.min()[d]);
				}
			}
			HelperFunctions.headless = true;
			List<double[]> points = Radial_Symmetry.runRSFISH(
					(RandomAccessible)Views.extendMirrorSingle( img3D ),
					new FinalInterval(minCoords, maxCoords),
					new FinalInterval(block.minCoords(), block.maxCoords()),
					params );

			System.out.println( "block " + block.id() + " found " + points.size() + " spots.");

			return new Tuple2<>(block, points );
		});

		rddResults.cache();

        final List<Tuple2<Block, List<double[]>>> results =
				new ArrayList<>(
						rddResults
							.filter(r -> r != null && r._2 != null && !r._2.isEmpty())
							.collect()
				);

		sc.close();

		if (!results.isEmpty() )  {
			long spotsCount = results.stream().mapToLong( t -> t._2.size() ).sum();
			System.out.printf("Write %d points to %s\n", spotsCount, output );

			CSVUtils.writeCSV( results, output );
		} else {
			System.out.println( "No points found!" );
		}

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
		System.out.println(String.join(" ",args));
		new CommandLine( new SparkRSFISH() ).execute( args );
	}
}
