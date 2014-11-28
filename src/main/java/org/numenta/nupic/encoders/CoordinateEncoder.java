package org.numenta.nupic.encoders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.numenta.nupic.util.ArrayUtils;
import org.numenta.nupic.util.MersenneTwister;
import org.numenta.nupic.util.SortablePair;
import org.numenta.nupic.util.Tuple;

public class CoordinateEncoder extends Encoder<Tuple> implements CoordinateOrder {
	private static MersenneTwister random = new MersenneTwister();
	
	/**
	 * Package private to encourage construction using the Builder Pattern
	 * but still allow inheritance.
	 */
	CoordinateEncoder() {}
	
	/**
	 * @see Encoder for more information
	 */
	@Override
	public int getWidth() {
		return n;
	}

	/**
	 * @see Encoder for more information
	 */
	@Override
	public boolean isDelta() {
		return false;
	}
	
	/**
	 * Returns a {@link List} of {@link Tuple}s containing
	 * [String:"name", int:offset]
	 * 
	 * @return List of Tuple(String, int)'s
	 * @see Encoder for more information
	 */
	@Override
	public List<Tuple> getDescription() {
		List<Tuple> retVal = new ArrayList<Tuple>();
		Tuple desc = new Tuple(2, "coordinate", 0);
		Tuple desc2 = new Tuple(2, "radius", 1);
		retVal.add(desc);
		retVal.add(desc2);
		
		return retVal;
	}

	/**
	 * Returns a builder for building ScalarEncoders. 
	 * This builder may be reused to produce multiple builders
	 * 
	 * @return a {@code CoordinateEncoder.Builder}
	 */
	public static Encoder.Builder<CoordinateEncoder.Builder, CoordinateEncoder> builder() {
		return new CoordinateEncoder.Builder();
	}
	
	/**
	 * Returns coordinates around given coordinate, within given radius.
     * Includes given coordinate.
     * 
	 * @param coordinate	Coordinate whose neighbors to find
	 * @param radius		Radius around `coordinate`
	 * @return
	 */
	public List<int[]> neighbors(int[] coordinate, double radius) {
		int[][] ranges = new int[coordinate.length][];
		for(int i = 0;i < coordinate.length;i++) {
			ranges[i] = ArrayUtils.range(coordinate[i] - (int)radius, coordinate[i] + (int)radius + 1);
		}
		
		List<int[]> retVal = new ArrayList<int[]>();
		int len = ranges.length == 1 ? 1 : ranges[0].length;
		for(int k = 0;k < ranges[0].length;k++) {
			for(int j = 0;j < len;j++) {
				int[] entry = new int[ranges.length];
				entry[0] = ranges[0][k];
				for(int i = 1;i < ranges.length;i++) {
					entry[i] = ranges[i][j];
				}
				retVal.add(entry);
			}
		}
		return retVal;
	}
	
	/**
	 * Returns the top W coordinates by order.
	 * 
	 * @param co			Implementation of {@link CoordinateOrder}
	 * @param coordinates	A 2D array, where each element
                            is a coordinate
	 * @param w				(int) Number of top coordinates to return
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public int[][] topWCoordinates(CoordinateOrder co, int[][] coordinates, int w) {
		SortablePair<Double, Integer>[] pairs = new SortablePair[coordinates.length];
		for(int i = 0; i < coordinates.length;i++) {
		    pairs[i] = new SortablePair<Double, Integer>(co.orderForCoordinate(coordinates[i]), i);
		}
		
		Arrays.sort(pairs);
		
		int[][] topCoordinates = new int[w][];
		for(int i = 0, wIdx = pairs.length - w; i < w; i++, wIdx++) {
		    int index = pairs[wIdx].second();
		    topCoordinates[i] = coordinates[index];
		}
		return topCoordinates;
	}
	
	/**
	 * Returns the order for a coordinate.
	 * 
	 * @param coordinate	coordinate array
	 * 
	 * @return	A value in the interval [0, 1), representing the
     *          order of the coordinate
	 */
	public double orderForCoordinate(int[] coordinate) {
		random.setSeed(coordinate);
		return random.nextDouble();
	}
	
	/**
	 * Returns the order for a coordinate.
	 * 
	 * @param coordinate	coordinate array
	 * @param n				the number of available bits in the SDR
	 * 
	 * @return	The index to a bit in the SDR
	 */
	public static int bitForCoordinate(int[] coordinate, int n) {
		random.setSeed(coordinate);
		return random.nextInt(n);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void encodeIntoArray(Tuple inputData, int[] output) {
		List<int[]> neighs = neighbors((int[])inputData.get(0), (double)inputData.get(1));
		int[][] neighbors = new int[neighs.size()][];
		for(int i = 0;i < neighs.size();i++) neighbors[i] = neighs.get(i);
		
		int[][] winners = topWCoordinates(this, neighbors, w);
		
		for(int i = 0;i < winners.length;i++) {
			int bit = bitForCoordinate(winners[i], n);
			output[bit] = 1;
		}
	}

	@Override
	public void setLearning(boolean learningEnabled) {
		super.setLearningEnabled(learningEnabled);
	}

	@Override
	public <T> List<T> getBucketValues(Class<T> returnType) {
		return null;
	}

	/**
	 * Returns a {@code Builder} for constructing {@link CoordinateEncoder}s
	 * 
	 * The base class architecture is put together in such a way where boilerplate
	 * initialization can be kept to a minimum for implementing subclasses, while avoiding
	 * the mistake-proneness of extremely long argument lists.
	 * 
	 * @see ScalarEncoder.Builder#setStuff(int)
	 */
	public static class Builder extends Encoder.Builder<CoordinateEncoder.Builder, CoordinateEncoder> {
		private Builder() {}

		@Override
		public CoordinateEncoder build() {
			//Must be instantiated so that super class can initialize 
			//boilerplate variables.
			encoder = new CoordinateEncoder();
			
			//Call super class here
			super.build();
			
			////////////////////////////////////////////////////////
			//  Implementing classes would do setting of specific //
			//  vars here together with any sanity checking       //
			////////////////////////////////////////////////////////
			
			if(w <= 0 || w % 2 == 0) {
				throw new IllegalArgumentException("w must be odd, and must be a positive integer");
			}
			
			if(n <= 6 * w) {
				throw new IllegalArgumentException(
					"n must be an int strictly greater than 6*w. For " +
                       "good results we recommend n be strictly greater than 11*w");
			}
			
			if(name == null || name.equals("None")) {
				name = new StringBuilder("[").append(n).append(":").append(w).append("]").toString();
			}
			
			return (CoordinateEncoder)encoder;
		}
	}
}
