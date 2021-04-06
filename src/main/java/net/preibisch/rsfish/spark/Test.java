package net.preibisch.rsfish.spark;

import java.io.File;
import java.util.ArrayList;

import gui.csv.overlay.CsvOverlay;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.util.Util;

public class Test {

	public static void main( String[] args )
	{
		ArrayList< RealPoint > peaks1 = CsvOverlay.readAndSortPositionsFromCsv( new File( "/Users/spreibi/Documents/BIMSB/Publications/radialsymmetry/test.csv" ) );
		ArrayList< RealPoint > peaks2 = CsvOverlay.readAndSortPositionsFromCsv( new File( "/Users/spreibi/Documents/BIMSB/Publications/radialsymmetry/test-spark.csv" ) );

		KDTree< RealPoint > tree1 = new KDTree<>( peaks1, peaks1 );
		NearestNeighborSearch< RealPoint > search1 = new NearestNeighborSearchOnKDTree<>( tree1 );

		KDTree< RealPoint > tree2 = new KDTree<>( peaks2, peaks2 );
		NearestNeighborSearch< RealPoint > search2 = new NearestNeighborSearchOnKDTree<>( tree2 );

		for ( final RealPoint p : peaks1 )
		{
			search2.search( p );
			final double d = search2.getDistance();

			if ( d >= 0.01 )
				System.out.println( "no match for: " + Util.printCoordinates( p ) + ", d=" + d);
		}

		// DoG gives identical result, Radial Symmetry varies (even without RANSAC)

		/*
		for ( final RealPoint p : peaks2 )
		{
			search2.search( p );
			final double d = search2.getDistance();

			if ( d > 0.1 )
				System.out.println( "no match for: " + Util.printCoordinates( p ) + ", d=" + d);
		}
		*/
	}
}
