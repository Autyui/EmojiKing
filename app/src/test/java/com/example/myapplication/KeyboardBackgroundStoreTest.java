package com.example.myapplication;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyboardBackgroundStoreTest {
    @Test
    public void sampleSizeKeepsDecodedImageNearMaximumDimension() {
        assertEquals(1, KeyboardBackgroundStore.calculateSampleSize(1600, 900, 1600));
        assertEquals(2, KeyboardBackgroundStore.calculateSampleSize(4000, 3000, 1600));
        assertEquals(4, KeyboardBackgroundStore.calculateSampleSize(8000, 4000, 1600));
    }
}
