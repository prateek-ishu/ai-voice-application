package com.voicepresentation.util;

import java.io.ByteArrayOutputStream;

/** Collects binary chunks streamed from the browser's MediaRecorder. */
public class AudioBuffer {

    private final int maxBytes;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public AudioBuffer(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public void add(byte[] chunk) {
        if (out.size() + chunk.length > maxBytes) {
            throw new IllegalStateException(
                    "Recording is over the " + (maxBytes / (1024 * 1024)) + " MB limit. Keep it shorter.");
        }
        out.writeBytes(chunk);
    }

    public byte[] flush() {
        byte[] data = out.toByteArray();
        out.reset();
        return data;
    }

    public int size() {
        return out.size();
    }
}
