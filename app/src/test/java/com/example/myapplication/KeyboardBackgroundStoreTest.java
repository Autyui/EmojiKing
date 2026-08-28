package com.example.myapplication;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

// 类作用：定义 KeyboardBackgroundStoreTest，承载所在模块的主要职责。
public class KeyboardBackgroundStoreTest {
// 方法作用：处理 sampleSizeKeepsDecodedImageNearMaximumDimension 对应的输入并返回或更新相关结果（sampleSizeKeepsDecodedImageNearMaximumDimension）。
    @Test
    public void sampleSizeKeepsDecodedImageNearMaximumDimension() {
        assertEquals(1, KeyboardBackgroundStore.calculateSampleSize(1600, 900, 1600));
        assertEquals(2, KeyboardBackgroundStore.calculateSampleSize(4000, 3000, 1600));
        assertEquals(4, KeyboardBackgroundStore.calculateSampleSize(8000, 4000, 1600));
    }
}
