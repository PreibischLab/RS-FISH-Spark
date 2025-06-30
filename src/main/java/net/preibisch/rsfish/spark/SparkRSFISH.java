package net.preibisch.rsfish.spark;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import benchmark.TextFileAccess;
import com.google.gson.GsonBuilder;
import gui.Radial_Symmetry;
import gui.interactive.HelperFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformationAdapter;
import parameters.RadialSymParams;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import scala.Tuple2;

public class SparkRSFISH implements Callable<Void>
{
	private static final int[] DEFAULT_BLOCK_SIZE_2D = new int[] { 512, 512 };
	private static final int[] DEFAULT_BLOCK_SIZE_3D = new int[] { 128, 128, 64 };

	// input file
	@Option(names = {"-i", "--image"}, required = true, description = "N5/HDF5/ZARR container path, e.g. -i '/home/smFish.n5' or -i '/home/smFish.h5' or -i '/home/smFish.zarr'")
	private String image = null;

	@Option(names = {"-d", "--dataset"}, required = true, description = "dataset within the N5/HDF5/ZARR, e.g. -d 'embryo_5_ch0/c0/s0'")
	private String dataset = null;

	// output file
	@Option(names = {"-o", "--output"}, required = true, description = "output CSV file, e.g. -o 'embryo_5_ch0.csv'")
	private String output = null;

	@Option(names = {"--storage"}, required = false, showDefaultValue = CommandLine.Help.Visibility.ALWAYS, description = "Dataset input type, currently supported N5, ZARR, HDF5")
	private StorageFormat storageFormat = null;

	// processing options
	@Option(names = "--blockSize", required = false, description = "Blocksize for processing, e.g. 128,128,64 or 512,512 (default: as listed under e.g.)")
	private String blockSizeString = null;
	private int[] blockSize;

	@Option(names = "--min", required = false, description = "Min coordinates of an OPTIONALLY defined subset of the entire image to be processed, e.g. 100,100,200 or 400,500 (default: entire image)")
	private String min = null;
	private long[] minInterval;

	@Option(names = "--max", required = false, description = "Max coordinates of an OPTIONALLY defined subset of the entire image to be processed, e.g. 1000,800,300 or 1400,800 (default: entire image)")
	private String max = null;
	private long[] maxInterval;

	@Option(names = "--min-channel", description = "Min channel (inclusive). If value < 0 it is not used.")
	private int minChannel = -1;

	@Option(names = "--max-channel", description = "Max channel (exclusive). If value < 0 it is not used.")
	private int maxChannel = -1;

	@Option(names = {"--excluded-channels"}, split = ",", description = "Comma-separated list of (0-based) channel values")
	private Set<Integer> excludedChannels;

	@Option(names = "--min-timeindex", description = "Min timeindex (inclusive). If value < 0 it is not used.")
	private int minTimeIndex = -1;

	@Option(names = "--max-timeindex", description = "Max timeindex (exclusive). If value < 0 it is not used.")
	private int maxTimeIndex = -1;

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
		final N5Factory n5Factory = new N5Factory();
		// configure N5 factory
		n5Factory.gsonBuilder(new GsonBuilder().registerTypeAdapter(
				CoordinateTransformation.class,
				new CoordinateTransformationAdapter() )
		);
		n5Factory.preferredStorageFormat(storageFormat);

		N5Reader n5AttrsReader = n5Factory.openReader(image);

		System.out.printf("Image: %s:%s => exists: %b\n", image, dataset, n5AttrsReader.datasetExists(dataset));
		final RandomAccessibleInterval<?> img = N5Utils.open( n5AttrsReader, dataset );

		String[] datasetComps = dataset.split("/");
		String datasetParent;
		if (datasetComps.length <= 1)
			datasetParent = "";
		else
			datasetParent = String.join("/", Arrays.copyOf(datasetComps, datasetComps.length - 1));


		final long[] datasetDimensions = n5AttrsReader.getDatasetAttributes(dataset).getDimensions();

		System.out.printf( "N5/HDF5/ZARR dataset dimensionality: %d\n", datasetDimensions.length );
		System.out.printf( "N5/HDF5/ZARR dataset size: %s (%s)\n", Util.printCoordinates( datasetDimensions ), datasetDimensions);

		OmeNgffMultiScaleMetadata[] multiscales = n5AttrsReader.getAttribute(
				datasetParent, "multiscales", OmeNgffMultiScaleMetadata[].class
		);

		final Tuple2<int[], long[]> imageDimensions = getImageDimensions(multiscales, datasetDimensions);

		int nDims = imageDimensions._1.length - 2;
		minInterval = new long[ nDims ]; // only allocate interval for spatial dimensions
		maxInterval = new long[ nDims ];

		if ( this.min != null )
			parseCSLongArray( min, minInterval );

		if ( this.max != null )
			parseCSLongArray(max, maxInterval);
		else
			for ( int d = 0; d < nDims; ++d )
				maxInterval[ d ] = imageDimensions._2[2 + d] - 1;

		final Interval interval = new FinalInterval(minInterval, maxInterval);

		System.out.println( "Processing interval: " + Util.printInterval( interval ));

		if ( this.blockSizeString == null ) {
			if ( nDims == 2 )
				this.blockSize = DEFAULT_BLOCK_SIZE_2D.clone();
			else
				this.blockSize = DEFAULT_BLOCK_SIZE_3D.clone();
		} else {
			this.blockSize = new int[ nDims ];
			parseCSIntArray( blockSizeString, blockSize );
		}

		System.out.println( "Processing blocksize: " + Util.printCoordinates( blockSize ));

		// create parameter object
		final RadialSymParams params = new RadialSymParams();

		// general
		params.anisotropyCoefficient = anisotropy;
		params.useAnisotropyForDoG = true;
		params.ransacSelection = ransac; //"No RANSAC", "RANSAC", "Multiconsensus RANSAC"

		if ( minIntensity == maxIntensity ) {
			params.min = Double.NaN;
			params.max = Double.NaN;
			params.autoMinMax = true;
		} else {
			params.min = minIntensity;
			params.max = maxIntensity;
			params.autoMinMax = false;
		}

		// multiconsensus
		if ( ransac == 2 ) {
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

		// RS-FISH only supports up to 3D so we only need min and max spatial coordinates for processing
		// only 2 pixel overlap necessary to find local max/min to start - we then anyways load the full underlying image for each block
		final List< Block > blocks = Block.splitIntoBlocks( interval, blockSize);
		System.out.printf("Split %s interval into %d %s blocks\n", Util.printInterval(interval), blocks.size(), Arrays.toString(blockSize));


		// do not store local results
		params.resultsFilePath = "";

		// single-threaded within each block
		params.numThreads = 1;

		final List<double[]> results = new ArrayList<>();
		final int timeaxis = imageDimensions._1[0];
		final int channelaxis = imageDimensions._1[1];

		// only consider time and channel intervals if time or channel axes are defined
		int startTimeIndex = minTimeIndex >= 0 && timeaxis != -1 ? minTimeIndex : 0;
		int endTimeIndex = maxTimeIndex >= 0 && timeaxis != -1 ? maxTimeIndex : (int) imageDimensions._2[0];
		int startChannel = minChannel >= 0 && channelaxis != -1 ? minChannel : 0;
		int endChannel = maxChannel >= 0 && channelaxis != -1 ? maxChannel : (int) imageDimensions._2[1];

		System.out.printf("Timeinterval:[%d,%d), Channel interval: [%d, %d)\n",
				startTimeIndex, endTimeIndex, startChannel, endChannel);
		for (int t = startTimeIndex; t < endTimeIndex; t++) {
			for (int c = startChannel; c < endChannel; c++) {
				if (excludedChannels != null && excludedChannels.contains(c) ) {
					continue; // skip this channel
				}
				// process spatial blocks for the current timepoint and channel
				List<double[]> blockResults = processBlocks(
						image, dataset, t, timeaxis, c, channelaxis, minInterval, maxInterval, storageFormat, blocks, params, sc
				);
				results.addAll( blockResults );
			}
		}

		sc.close();

		if (!results.isEmpty() )  {
			System.out.printf("Write %d points to %s\n", results.size(), output );

			writeCSV( results, nDims, output );
		} else {
			System.out.println( "No points found!" );
		}

		return null;
	}

	/**
	 * Get image dimensions as a tuple of 2 arrays. The first array has the axis position and the second
	 * array has the actual dimension. The first 2 positions are reserved for the time (0) and channel (1) values
	 * respectively even if these are not present in the dataset. If the time or channel is not present the
	 * corresponding axis position is -1.
	 * @param multiscales
	 * @param datasetDimensions
	 * @return
	 */
	private Tuple2<int[], long[]> getImageDimensions(OmeNgffMultiScaleMetadata[] multiscales, long[] datasetDimensions) {
		OmeNgffMultiScaleMetadata multiScaleMetadata = multiscales != null && multiscales.length > 0 ? multiscales[0] : null;
		if (multiScaleMetadata == null) {
			// no OME-NGFF
			return getNotNGFFImageDimensions(datasetDimensions);
		} else {
			return getNGFFImageDimensions(multiScaleMetadata, datasetDimensions);
		}
	}

	private Tuple2<int[], long[]> getNotNGFFImageDimensions(long[] datasetDimensions) {
		if (datasetDimensions.length > 3) {
			// if array.ndim > 3 then it must be OME-NGFF
			throw new IllegalArgumentException("Higher than 3D array require OME-NGFF metadata which is not set for " + dataset);
		}
		int[] axesPos = new int[2 + datasetDimensions.length];
		long[] dimensions = new long[2 + datasetDimensions.length];
		// timepoints and channels are not set
		axesPos[0] = -1;
		axesPos[1] = -1;
		// but consider the dimension to be 1
		dimensions[0] = 1;
		dimensions[1] = 1;
		for (int i = 0; i < datasetDimensions.length; i++) {
			axesPos[i + 2] = i;
			dimensions[i + 2] = datasetDimensions[i];
		}
		return new Tuple2<>(axesPos, dimensions);
	}

	private Tuple2<int[], long[]> getNGFFImageDimensions(OmeNgffMultiScaleMetadata multiScaleMetadata, long[] datasetDimensions) {
		// OME-NGFF metadata is present
		Axis[] datasetAxes = multiScaleMetadata.getAxes();
		if (datasetAxes == null || datasetAxes.length != datasetDimensions.length) {
			throw new IllegalArgumentException("Invalid OME attributes - the number of axes and the array dimensions for " + dataset + " are different");
		}
		int nAxes = datasetAxes.length;
		int[] timeAndChannelAxis = new int[] { -1, -1 };
		long[] timeAndChannelDims = new long[] { 1L, 1L };
		int[] spatialAxes = new int[datasetAxes.length];
		long[] spatialDimensions = new long[datasetDimensions.length];
		int nspatialDimensions = 0;
		for (int ai = datasetAxes.length -1; ai >= 0; ai--) {
			switch (datasetAxes[ai].getType()) {
				case Axis.TIME:
					timeAndChannelAxis[0] = nAxes - ai - 1;
					timeAndChannelDims[0] = datasetDimensions[nAxes - ai - 1];
					break;
				case Axis.CHANNEL:
					timeAndChannelAxis[1] = nAxes - ai - 1;
					timeAndChannelDims[1] = datasetDimensions[nAxes - ai - 1];
					break;
				case Axis.SPACE:
					// do not revert spatial axes
					spatialAxes[nspatialDimensions] = nAxes - ai - 1;
					spatialDimensions[nspatialDimensions] = datasetDimensions[nAxes - ai - 1];
					nspatialDimensions++;
					break;
				default:
					// don't know if I need to do anything for other axes types => simply continue
					break;
			}
		}
		int[] axesPos = new int[2 + nspatialDimensions];
		long[] dimensions = new long[2 + nspatialDimensions];
		for (int i = 0; i < 2; i ++) {
			axesPos[i] = timeAndChannelAxis[i];
			dimensions[i] = timeAndChannelDims[i];
		}
		for (int i = 0; i < nspatialDimensions; i ++) {
			axesPos[i + 2] = spatialAxes[i];
			dimensions[i + 2] = spatialDimensions[i];
		}
		return new Tuple2<>(axesPos, dimensions);
	}

	private List<double[]> processBlocks(
			String imageUri, String datasetName,
			int timeindex, int timeAxis,
			int channel, int channelAxis,
			long[] minInterval,
			long[] maxInterval,
			StorageFormat storageFormat,
			List<Block> blocks,
			RadialSymParams params,
			JavaSparkContext sc) {

		final JavaRDD<Block> rddIds = sc.parallelize( blocks );

		final JavaPairRDD<Block, List<double[]> > rddResults = rddIds.mapToPair( block -> {

			System.out.printf( "Processing block %d:%d:%s (%s)\n",
					timeindex, channel, block.id(), Util.printInterval(block.createInterval()));

			N5Factory n5Factory = new N5Factory();
			n5Factory.preferredStorageFormat(storageFormat);

			final N5Reader localBlockReader = n5Factory.openReader(imageUri);
			final RandomAccessibleInterval<?> img = N5Utils.open( localBlockReader, datasetName );

			System.out.printf(
					"Read block %s from %s image\n",
					Util.printInterval(block.createInterval()), Util.printInterval(img)
			);

			// RS-FISH only supports 3D images so if image is >3D take a 3D slice
			RandomAccessibleInterval<?> img3D = img;
			if ( timeAxis != -1 ) {
				// there is a time axis
				img3D = Views.hyperSlice( img3D, timeAxis, timeindex );
			}
			if ( channelAxis != -1 ) {
				// there is a channel axis
				img3D = Views.hyperSlice( img3D, channelAxis, channel );
			}
			HelperFunctions.headless = true;
			@SuppressWarnings({"unchecked", "rawtypes"})
			List<double[]> points = Radial_Symmetry.runRSFISH(
					(RandomAccessible)Views.extendMirrorSingle( img3D ),
					new FinalInterval(minInterval, maxInterval),
					block.createInterval(),
					params );

			System.out.println(
					"Block " + block.id() + ":" + Util.printInterval(block.createInterval()) +
							" found " + points.size() + " spots."
			);

			// prepend timeindex and channel to each point
			List<double[]> pointsWithTimeAndChannel = points.stream()
					.map(p -> {
						double[] pWithTimeAndChannel = new double[2 + p.length];
						pWithTimeAndChannel[0] = timeindex;
						pWithTimeAndChannel[1] = channel;
						System.arraycopy(p, 0, pWithTimeAndChannel, 2, p.length);
						return pWithTimeAndChannel;
					})
					.collect(Collectors.toList());
			return new Tuple2<>(block, pointsWithTimeAndChannel);
		});

		rddResults.cache();

		// filter out block results that do not have any points
		return rddResults
				.filter(r -> r != null && r._2 != null && !r._2.isEmpty())
				.flatMap(r -> r._2.iterator())
				.collect();
	}

	// taken from: hot-knife repository (Saalfeld)
	private static boolean parseCSIntArray(final String csv, final int[] array) {

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
	private static boolean parseCSLongArray(final String csv, final long[] array) {

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

	private static void writeCSV(final List<double[]> allTimeAndChannelPoints, int ndims, final String file) {
		PrintWriter out = TextFileAccess.openFileWrite( file );

		// output CSV header
		if ( ndims == 3 )
			// if the condition throws an IndexOutOfBounds exception something is really wrong
			// because allPointsByBlocks should only have blocks that have spots
			out.println("x,y,z,t,c,intensity");
		else
			out.println("x,y,t,c,intensity");

		for (double[] spot : allTimeAndChannelPoints) {
			// output 1-based values for timepoint and channel
			int timeIndex = (int) spot[0] + 1;
			int channel = (int) spot[1] + 1;

			// output x,y[,z]
			for (int d = 2; d < spot.length - 1; ++d)
				out.print( String.format(java.util.Locale.US, "%.4f", spot[ d ] ) + "," );

			out.printf( "%d,%d,", timeIndex, channel );

			// output intensity
			out.println(String.format(java.util.Locale.US, "%.4f", spot[ spot.length - 1 ] ) );
		}

		System.out.println(allTimeAndChannelPoints.size() + " spots written to " + file );
		out.close();
	}

	public static void main(final String... args) {
		System.out.println(String.join(" ",args));
		new CommandLine( new SparkRSFISH() ).execute( args );
	}
}
