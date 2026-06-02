package net.preibisch.rsfish.util;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import bdv.ViewerImgLoader;
import com.google.gson.GsonBuilder;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudUtils;
import org.janelia.saalfeldlab.n5.FileSystemKeyValueAccess;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.s3.AmazonS3Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.coordinateTransformations.CoordinateTransformationAdapter;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.v3.ZarrV3KeyValueReader;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class URITools
{
    private final static Pattern HTTPS_SCHEME = Pattern.compile( "http(s)?", Pattern.CASE_INSENSITIVE );
    private final static Pattern FILE_SCHEME = Pattern.compile( "file", Pattern.CASE_INSENSITIVE );

    public static String s3Region = null;

    public static N5Reader instantiateN5Reader( final StorageFormat format, final URI uri )
    {
        final GsonBuilder builder = new GsonBuilder().registerTypeAdapter(
                CoordinateTransformation.class,
                new CoordinateTransformationAdapter() );

        if ( URITools.isFile( uri ) )
        {
            if ( format.equals( StorageFormat.N5 ))
                return new N5FSReader( URITools.fromURI( uri ) );
            else if ( format.equals( StorageFormat.ZARR ))
            {
                // Create Zarr v3 reader
                return new ZarrV3KeyValueReader(
                        new FileSystemKeyValueAccess(),
                        URITools.fromURI( uri ),
                        builder,
                        true // cacheAttributes
                );
            }
            else if ( format.equals( StorageFormat.ZARR2 ))
                return new N5ZarrReader( URITools.fromURI( uri ), builder );
            else if ( format.equals( StorageFormat.HDF5 ))
                return new N5HDF5Reader( URITools.fromURI( uri ) );
            else
                throw new RuntimeException( "Format: " + format + " not supported." );
        }
        else if (URITools.isS3( uri ) )
        {
            N5Reader n5r;

            try
            {
                final N5Factory factory = new N5Factory();
                factory.gsonBuilder( builder );
                factory.s3Configuration( b -> {
                    b.credentialsProvider( DefaultCredentialsProvider.create() );
                    if ( s3Region != null )
                        b.region( Region.of( s3Region ) );
                } );
                n5r = factory.openReader( format, uri );
            }
            catch ( Exception e )
            {
                System.out.println( "With credentials failed; trying anonymous with gson builder ..." );

                final N5Factory factory = new N5Factory();
                factory.gsonBuilder( builder );
                factory.s3Configuration( b -> {
                    b.credentialsProvider( AnonymousCredentialsProvider.create() );
                    if ( s3Region != null )
                        b.region( Region.of( s3Region ) );
                } );
                n5r = factory.openReader( format, uri );
            }

            return n5r;
        } else {
            throw new IllegalArgumentException( "Unsupported URI: " + uri  + " - only file and S3 are supported");
        }
    }

    public static boolean setNumFetcherThreads(final BasicImgLoader loader, final int threads )
    {
        if ( ViewerImgLoader.class.isInstance( loader ) )
        {
            ( (ViewerImgLoader) loader ).setNumFetcherThreads( threads );
            return true;
        }
        else
        {
            return false;
        }
    }

    public static boolean isGC( URI uri )
    {
        final String scheme = uri.getScheme();
        final boolean hasScheme = scheme != null;
        if ( !hasScheme )
            return false;
        if ( GoogleCloudUtils.GS_SCHEME.asPredicate().test( scheme ) )
            return true;
        return uri.getHost() != null && HTTPS_SCHEME.asPredicate().test( scheme ) && GoogleCloudUtils.GS_HOST.asPredicate().test( uri.getHost() );
    }

    public static boolean isS3( URI uri )
    {
        final String scheme = uri.getScheme();
        final boolean hasScheme = scheme != null;
        if ( !hasScheme )
            return false;
        if ( AmazonS3Utils.S3_SCHEME.asPredicate().test( scheme ) )
            return true;
        return uri.getHost() != null && HTTPS_SCHEME.asPredicate().test( scheme );
    }

    public static boolean isFile( URI uri )
    {
        final String scheme = uri.getScheme();
        final boolean hasScheme = scheme != null;

        if ( !hasScheme )
            return false;
        else
            return FILE_SCHEME.asPredicate().test( scheme );
    }

    /**
     * @param uriString - if relative we assume it's a local path and file:/ scheme will be added
     * @return the URI of the String
     */
    public static URI toURI( final String uriString )
    {
        URI uri;

        try
        {
            uri = new URI( uriString );
        }
        catch (URISyntaxException e)
        {
            // e.g. a space was in there, which is allowed for filepaths, but not other resources (must be %20)
            uri = null;
        }

        try
        {
            // maybe it works if we assume it is a file
            if ( uri == null )
                uri = new File( uriString ).toURI();

            if ( !uri.isAbsolute() )
                uri = new URI( "file", null, uriString, null );

            return uri;
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
            throw new RuntimeException( "URI couldn't be created from '" + uriString + "'. stopping: " + e );
        }
    }

    /**
     *
     * @param uri a URI
     * @return a String representation of a URI, if it starts with file:/ it will be removed
     */
    public static String fromURI( final URI uri )
    {
        final String scheme = uri.getScheme();

        if ( scheme == null )
            throw new RuntimeException( "URI '" + uri + "' has no scheme. stopping." );

        if ( FILE_SCHEME.asPredicate().test( uri.getScheme() ) )
        {
            try
            {
                return new File( uri ).toString();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                throw new RuntimeException( "Error converting file-URI '" + uri + "' to a path. stopping." );
            }
        }
        else
        {
            return uri.toString();
        }
    }
}
