package com.example.myapplication;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

// 类作用：定义 ImageShareSenderTest，承载所在模块的主要职责。
public class ImageShareSenderTest {
// 方法作用：处理 availableTargetUsesTargetedShare 对应的输入并返回或更新相关结果（availableTargetUsesTargetedShare）。
    @Test
    public void availableTargetUsesTargetedShare() {
        assertEquals(
                ImageShareSender.Destination.TARGET,
                ImageShareSender.selectDestination("com.example.target", true, true));
    }

// 方法作用：处理 unavailableTargetFallsBackToChooser 对应的输入并返回或更新相关结果（unavailableTargetFallsBackToChooser）。
    @Test
    public void unavailableTargetFallsBackToChooser() {
        assertEquals(
                ImageShareSender.Destination.CHOOSER,
                ImageShareSender.selectDestination("com.example.target", false, true));
    }

// 方法作用：处理 missingTargetUsesChooser 对应的输入并返回或更新相关结果（missingTargetUsesChooser）。
    @Test
    public void missingTargetUsesChooser() {
        assertEquals(
                ImageShareSender.Destination.CHOOSER,
                ImageShareSender.selectDestination(null, false, true));
        assertEquals(
                ImageShareSender.Destination.CHOOSER,
                ImageShareSender.selectDestination("  ", false, true));
    }

// 方法作用：处理 noShareActivityFails 对应的输入并返回或更新相关结果（noShareActivityFails）。
    @Test
    public void noShareActivityFails() {
        assertEquals(
                ImageShareSender.Destination.NONE,
                ImageShareSender.selectDestination("com.example.target", false, false));
    }
}
