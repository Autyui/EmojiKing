package com.example.myapplication;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageShareSenderTest {
    @Test
    public void availableTargetUsesTargetedShare() {
        assertEquals(
                ImageShareSender.Destination.TARGET,
                ImageShareSender.selectDestination("com.example.target", true, true));
    }

    @Test
    public void unavailableTargetFallsBackToChooser() {
        assertEquals(
                ImageShareSender.Destination.CHOOSER,
                ImageShareSender.selectDestination("com.example.target", false, true));
    }

    @Test
    public void missingTargetUsesChooser() {
        assertEquals(
                ImageShareSender.Destination.CHOOSER,
                ImageShareSender.selectDestination(null, false, true));
        assertEquals(
                ImageShareSender.Destination.CHOOSER,
                ImageShareSender.selectDestination("  ", false, true));
    }

    @Test
    public void noShareActivityFails() {
        assertEquals(
                ImageShareSender.Destination.NONE,
                ImageShareSender.selectDestination("com.example.target", false, false));
    }
}
