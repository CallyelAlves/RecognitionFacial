package com.app.facesample.helpers;

public class FaceFrame {

    private byte[] buffer1;
    private byte[] buffer2;
    private byte[] buffer3;

    private int width;
    private int height;
    private int stride;

    private int rowStride1, pixelStride1, rowStride2, pixelStride2, rowStride3, pixelStride3;


    public FaceFrame(byte[] buffer1, byte[] buffer2, byte[] buffer3, int width, int height, int stride, int rowStride1, int pixelStride1, int rowStride2, int pixelStride2, int rowStride3, int pixelStride3) {
        this.buffer1 = buffer1;
        this.buffer2 = buffer2;
        this.buffer3 = buffer3;
        this.width = width;
        this.height = height;
        this.stride = stride;
        this.rowStride1 = rowStride1;
        this.pixelStride1 = pixelStride1;
        this.rowStride2 = rowStride2;
        this.pixelStride2 = pixelStride2;
        this.rowStride3 = rowStride3;
        this.pixelStride3 = pixelStride3;
    }

    public FaceFrame(byte[] buffer1) {
        this.buffer1 = buffer1;
        this.buffer2 = null;
        this.buffer3 = null;
    }

    public byte[] getBuffer1() {
        return buffer1;
    }

    public byte[] getBuffer2() {
        return buffer2;
    }

    public byte[] getBuffer3() {
        return buffer3;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getStride() {
        return stride;
    }

    public int getRowStride1() {
        return rowStride1;
    }

    public int getPixelStride1() {
        return pixelStride1;
    }

    public int getRowStride2() {
        return rowStride2;
    }

    public int getPixelStride2() {
        return pixelStride2;
    }

    public int getRowStride3() {
        return rowStride3;
    }

    public int getPixelStride3() {
        return pixelStride3;
    }
}
