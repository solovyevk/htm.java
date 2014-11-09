package org.numenta.nupic.unit.encoders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.numenta.nupic.encoders.DecodeResult;
import org.numenta.nupic.encoders.Encoder;
import org.numenta.nupic.encoders.EncoderResult;
import org.numenta.nupic.encoders.RangeList;
import org.numenta.nupic.encoders.ScalarEncoder;
import org.numenta.nupic.util.ArrayUtils;
import org.numenta.nupic.util.MinMax;
import org.numenta.nupic.util.Tuple;

public class ScalarEncoderTest {
	private ScalarEncoder se;
	private ScalarEncoder.Builder builder;
	
	private void setUp() {
        builder =  ScalarEncoder.builder()
	        .n(14)
	        .w(3)
	        .radius(0.0)
	        .minVal(1.0)
	        .maxVal(8.0)
	        .periodic(true)
	        .forced(true);
    }
	
	private void initSE() {
		se = builder.build();
	}
	
	@Test
	public void testScalarEncoder() {
		setUp();
		initSE();
		
		int[] empty = se.encode(Encoder.SENTINEL_VALUE_FOR_MISSING_DATA);
		System.out.println("\nEncoded missing data as: " + Arrays.toString(empty));
		int[] expected = new int[14];
		assertTrue(Arrays.equals(expected, empty));
	}
	
	@Test
	public void testBottomUpEncodingPeriodicEncoder() {
		setUp();
		initSE();
		
		assertEquals("[1:8]", se.getDescription().get(0).get(0));
		
		setUp();
		builder.name("scalar");
		initSE();
		
		assertEquals("scalar", se.getDescription().get(0).get(0));
		int[] res = se.encode(3);
		assertTrue(Arrays.equals(new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0 }, res));
		
		res = se.encode(3.1);
		assertTrue(Arrays.equals(new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0 }, res));
		
		res = se.encode(3.5);
		assertTrue(Arrays.equals(new int[] { 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 }, res));
		
		res = se.encode(3.6);
		assertTrue(Arrays.equals(new int[] { 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 }, res));
		
		res = se.encode(3.7);
		assertTrue(Arrays.equals(new int[] { 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 }, res));
		
		res = se.encode(4);
		assertTrue(Arrays.equals(new int[] { 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0 }, res));
		
		res = se.encode(1);
		assertTrue(Arrays.equals(new int[] { 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 }, res));

		res = se.encode(1.5);
		assertTrue(Arrays.equals(new int[] { 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, res));
		
		res = se.encode(7);
		assertTrue(Arrays.equals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1 }, res));
		
		res = se.encode(7.5);
		assertTrue(Arrays.equals(new int[] { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1 }, res));
		
		assertEquals(0.5d, se.getResolution(), 0);
		assertEquals(1.5d, se.getRadius(), 0);
	}

	/**
	 * Test that we get the same encoder when we construct it using resolution
     * instead of n
	 */
	@Test
	public void testCreateResolution() {
		setUp();
		initSE();
		List<Tuple> dict = se.dict();
		
		setUp();
		builder.resolution(0.5);
		initSE();
		List<Tuple> compare = se.dict();
		assertEquals(dict.toString(), compare.toString());
		
		setUp();
		builder.radius(1.5);
		initSE();
		compare = se.dict();
		assertEquals(dict.toString(), compare.toString());
		
		//Negative test
		setUp();
		builder.resolution(0.5);
		initSE();
		se.setName("break this");
		compare = se.dict();
		assertFalse(dict.equals(compare));
	}
	
	/**
	 * Test the input description generation, top-down compute, and bucket
     * support on a periodic encoder
     */
	@Test
	public void testDecodeAndResolution() {
		setUp();
		builder.name("scalar");
		initSE();
		double resolution = se.getResolution();
		StringBuilder out = new StringBuilder();
		for(double v = se.getMinVal();v < se.getMaxVal();v+=(resolution / 4.0d)) {
			int[] output = se.encode(v);
			DecodeResult decoded = se.decode(output, "");
			
			System.out.println(out.append("decoding ").append(Arrays.toString(output)).append(" (").
			append(String.format("%.6f", v)).append(")=> ").append(se.decodedToStr(decoded)));
			out.setLength(0);
			
			Map<String, RangeList> fieldsMap = decoded.getFields();
			assertEquals(1, fieldsMap.size());
			RangeList ranges = (RangeList)new ArrayList<RangeList>(fieldsMap.values()).get(0);
			assertEquals(1, ranges.size());
			assertEquals(ranges.getRange(0).min(), ranges.getRange(0).max(), 0);
			assertTrue(ranges.getRange(0).min() - v < se.getResolution());
			
			EncoderResult topDown = se.topDownCompute(output).get(0);
			System.out.println("topdown => " + topDown);
			assertTrue(topDown.get(3).equals(Arrays.toString(output)));
			assertTrue(Math.abs(((double)topDown.get(1)) - v) <= se.getResolution() / 2);
			
			//Test bucket support
			int[] bucketIndices = se.getBucketIndices(v);
			System.out.println("bucket index => " + bucketIndices[0]);
			topDown = se.getBucketInfo(bucketIndices).get(0);
			assertTrue(Math.abs(((double)topDown.get(1)) - v) <= se.getResolution() / 2);
			assertEquals(topDown.get(1), se.getBucketValues(Double.class).toArray()[bucketIndices[0]]);
			assertEquals(topDown.get(2), topDown.get(1));
			assertTrue(topDown.get(3).equals(Arrays.toString(output)));
		}
		
		// -----------------------------------------------------------------------
	    // Test the input description generation on a large number, periodic encoder
		setUp();
		builder.name("scalar")
	        .w(3)
	        .radius(1.5)
	        .minVal(1.0)
	        .maxVal(8.0)
	        .periodic(true)
	        .forced(true);
		initSE();
		
		System.out.println("\nTesting periodic encoder decoding, resolution of " + se.getResolution());
		
		//Test with a "hole"
		int[] encoded = new int[] { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0 };
		DecodeResult decoded = se.decode(encoded, "");
		Map<String, RangeList> fieldsMap = decoded.getFields();
		assertEquals(1, fieldsMap.size());
		assertEquals(1, decoded.getRanges("scalar").size());
		assertEquals(decoded.getRanges("scalar").getRange(0).toString(), "7.5, 7.5");
		
		//Test with something wider than w, and with a hole, and wrapped
		encoded = new int[] { 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0 };
		decoded = se.decode(encoded, "");
		fieldsMap = decoded.getFields();
		assertEquals(1, fieldsMap.size());
		assertEquals(2, decoded.getRanges("scalar").size());
		assertEquals(decoded.getRanges("scalar").getRange(0).toString(), "7.5, 8.0");
		
		//Test with something wider than w, no hole
		encoded = new int[] { 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		decoded = se.decode(encoded, "");
		fieldsMap = decoded.getFields();
		assertEquals(1, fieldsMap.size());
		assertEquals(1, decoded.getRanges("scalar").size());
		assertEquals(decoded.getRanges("scalar").getRange(0).toString(), "1.5, 2.5");
		
		//Test with 2 ranges
		encoded = new int[] { 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0 };
		decoded = se.decode(encoded, "");
		fieldsMap = decoded.getFields();
		assertEquals(1, fieldsMap.size());
		assertEquals(2, decoded.getRanges("scalar").size());
		assertEquals(decoded.getRanges("scalar").getRange(0).toString(), "1.5, 1.5");
		assertEquals(decoded.getRanges("scalar").getRange(1).toString(), "5.5, 6.0");
		
		//Test with 2 ranges, 1 of which is narrower than w
		encoded = new int[] { 0, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0 };
		decoded = se.decode(encoded, "");
		fieldsMap = decoded.getFields();
		assertEquals(1, fieldsMap.size());
		assertEquals(2, decoded.getRanges("scalar").size());
		assertEquals(decoded.getRanges("scalar").getRange(0).toString(), "1.5, 1.5");
		assertEquals(decoded.getRanges("scalar").getRange(1).toString(), "5.5, 6.0");
	}
	
	/**
	 * Test closenessScores for a periodic encoder
	 */
	@Test
	public void testCloseness() {
		setUp();
		builder.name("day of week")
	        .w(7)
	        .radius(1.0)
	        .minVal(0.0)
	        .maxVal(7.0)
	        .periodic(true)
	        .forced(true);
		initSE();
		
		TDoubleList expValues = new TDoubleArrayList(new double[] { 2, 4, 7 });
		TDoubleList actValues = new TDoubleArrayList(new double[] { 4, 2, 1 });
		
		TDoubleList scores = se.closenessScores(expValues, actValues, false);
		for(Tuple t : ArrayUtils.zip(Arrays.asList(2, 2, 1), Arrays.asList(scores.get(0)))) {
			double a = (int)t.get(0);
			double b = (double)t.get(1);
			assertTrue(a == b);
		}
	}
	
	@Test
	public void testNonPeriodicBottomUp() {
		setUp();
		builder.name("day of week")
	        .w(5)
	        .n(14)
	        .radius(1.0)
	        .minVal(1.0)
	        .maxVal(10.0)
	        .periodic(false)
	        .forced(true);
		initSE();
		
		System.out.println(String.format("Testing non-periodic encoder encoding resolution of ", se.getResolution()));
		
		assertTrue(Arrays.equals(se.encode(1), new int[] { 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 }));
		assertTrue(Arrays.equals(se.encode(2), new int[] { 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0 }));
		assertTrue(Arrays.equals(se.encode(10), new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1 }));
		
		// Test that we get the same encoder when we construct it using resolution
	    // instead of n
		setUp();
		builder.name("day of week")
	        .w(5)
	        .radius(5.0)
	        .minVal(1.0)
	        .maxVal(10.0)
	        .periodic(false)
	        .forced(true);
		initSE();
		
		double v = se.getMinVal();
		while(v < se.getMaxVal()) {
			int[] output = se.encode(v);
			DecodeResult decoded = se.decode(output, "");
			System.out.println("decoding " + Arrays.toString(output) + String.format("(%f)=>", v) + se.decodedToStr(decoded));
			
			assertEquals(decoded.getFields().size(), 1, 0);
			List<RangeList> rangeList = new ArrayList<RangeList>(decoded.getFields().values());
			assertEquals(rangeList.get(0).size(), 1, 0);
			MinMax minMax = rangeList.get(0).getRanges().get(0);
			assertEquals(minMax.min(), minMax.max(), 0);
			assertTrue(Math.abs(minMax.min() - v) <= se.getResolution());
			
			List<EncoderResult> topDowns = se.topDownCompute(output);
			EncoderResult topDown = topDowns.get(0);
			System.out.println("topDown => " + topDown);
			assertEquals(topDown.getEncoding(), Arrays.toString(output));
			assertTrue(Math.abs(((double)topDown.getValue()) - v) <= se.getResolution());
			
			//Test bucket support
			int[] bucketIndices = se.getBucketIndices(v);
			System.out.println("bucket index => " + bucketIndices[0]);
			topDown = se.getBucketInfo(bucketIndices).get(0);
			assertTrue(Math.abs(((double)topDown.getValue()) - v) <= se.getResolution() / 2);
			assertEquals(topDown.getScalar(), topDown.getValue());
			assertEquals(topDown.getEncoding(), Arrays.toString(output));
			
			// Next value
			v += se.getResolution() / 4;
		}
		
		// Make sure we can fill in holes
		DecodeResult decoded = se.decode(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1 }, "");
		assertEquals(decoded.getFields().size(), 1, 0);
		List<RangeList> rangeList = new ArrayList<RangeList>(decoded.getFields().values());
		assertEquals(1, rangeList.get(0).size(), 0);
		System.out.println("decodedToStr of " + rangeList + " => " + se.decodedToStr(decoded));
		
		decoded = se.decode(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 1 }, "");
		assertEquals(decoded.getFields().size(), 1, 0);
		rangeList = new ArrayList<RangeList>(decoded.getFields().values());
		assertEquals(1, rangeList.get(0).size(), 0);
		System.out.println("decodedToStr of " + rangeList + " => " + se.decodedToStr(decoded));
		
		// Test min and max
		setUp();
		builder.name("scalar")
	        .w(3)
	        .minVal(1.0)
	        .maxVal(10.0)
	        .periodic(false)
	        .forced(true);
		initSE();
		
		List<EncoderResult> decode = se.topDownCompute(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1 });
		assertEquals(10, (Double)decode.get(0).getScalar(), 0);
		decode = se.topDownCompute(new int[] { 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
		assertEquals(1, (Double)decode.get(0).getScalar(), 0);
		
		// Make sure only the last and first encoding encodes to max and min, and there is no value greater than max or min
		setUp();
		builder.name("scalar")
	        .w(3)
	        .n(140)
	        .radius(1.0)
	        .minVal(1.0)
	        .maxVal(141.0)
	        .periodic(false)
	        .forced(true);
		initSE();
		
		List<int[]> iterlist = new ArrayList<int[]>();
		for(int i = 0;i < 137;i++) {
			iterlist.add(new int[140]);
			ArrayUtils.setRangeTo(iterlist.get(i), i, i+3, 1);
			decode = se.topDownCompute(iterlist.get(i));
			int value = decode.get(0).getScalar().intValue();
			assertTrue(value <= 141);
			assertTrue(value >= 1);
			assertTrue(value < 141 || i==137);
			assertTrue(value > 1 || i==0);
		}
		
		// -------------------------------------------------------------------------
	    // Test the input description generation and top-down compute on a small number
	    //   non-periodic encoder
		setUp();
		builder.name("scalar")
	        .w(3)
	        .n(15)
	        .minVal(.001)
	        .maxVal(.002)
	        .periodic(false)
	        .forced(true);
		initSE();
		
		System.out.println(String.format("\nTesting non-periodic encoder decoding resolution of %f...", se.getResolution()));
		v = se.getMinVal();
		while(v < se.getMaxVal()) {
			int[] output = se.encode(v);
			decoded = se.decode(output, "");
			System.out.println(String.format("decoding (%f)=>", v) + " " + se.decodedToStr(decoded));
			
			assertEquals(decoded.getFields().size(), 1, 0);
			rangeList = new ArrayList<RangeList>(decoded.getFields().values());
			assertEquals(rangeList.get(0).size(), 1, 0);
			MinMax minMax = rangeList.get(0).getRanges().get(0);
			assertEquals(minMax.min(), minMax.max(), 0);
			assertTrue(Math.abs(minMax.min() - v) <= se.getResolution());
			
			decode = se.topDownCompute(output);
			System.out.println("topdown => " + decode);
			assertTrue(Math.abs((Double)decode.get(0).getScalar() - v) <= se.getResolution() / 2);
			
			v += (se.getResolution() / 4);
		}
		
		// -------------------------------------------------------------------------
	    // Test the input description generation on a large number, non-periodic encoder
		setUp();
		builder.name("scalar")
	        .w(3)
	        .n(15)
	        .minVal(1.0)
	        .maxVal(1000000000.0)
	        .periodic(false)
	        .forced(true);
		initSE();
		
		System.out.println(String.format("\nTesting non-periodic encoder decoding resolution of %f...", se.getResolution()));
		v = se.getMinVal();
		while(v < se.getMaxVal()) {
			int[] output = se.encode(v);
			decoded = se.decode(output, "");
			System.out.println(String.format("decoding (%f)=>", v) + " " + se.decodedToStr(decoded));
			
			assertEquals(decoded.getFields().size(), 1, 0);
			rangeList = new ArrayList<RangeList>(decoded.getFields().values());
			assertEquals(rangeList.get(0).size(), 1, 0);
			MinMax minMax = rangeList.get(0).getRanges().get(0);
			assertEquals(minMax.min(), minMax.max(), 0);
			assertTrue(Math.abs(minMax.min() - v) <= se.getResolution());
			
			decode = se.topDownCompute(output);
			System.out.println("topdown => " + decode);
			assertTrue(Math.abs((Double)decode.get(0).getScalar() - v) <= se.getResolution() / 2);
			
			v += (se.getResolution() / 4);
		}
	}
}
