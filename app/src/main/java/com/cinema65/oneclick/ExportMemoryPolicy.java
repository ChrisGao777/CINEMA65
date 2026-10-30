package com.cinema65.oneclick;

/** Conservative preflight for Android's non-streaming, mutable full-image decode.
 * A JPEG encoder still needs the entire Bitmap, even when our grade uses stripes.
 */
public final class ExportMemoryPolicy {
    private ExportMemoryPolicy() {}
    private static final long MIB=1024L*1024L;
    public static String rejection(int width,int height,long heapAvailable,long systemAvailable,
                                   boolean lowMemory){
        if(width<=0||height<=0)return "Invalid image dimensions";
        long count=(long)width*height;
        if(count>160_000_000L)return "Image exceeds maximum pixel count";
        long bitmapBytes=count*4;
        // Decoder intermediates, original mutable Bitmap, compression and stripe arrays.
        long heapNeed=bitmapBytes*2+32*MIB;
        long systemNeed=bitmapBytes*3+128*MIB;
        if(lowMemory||heapAvailable<heapNeed||systemAvailable<systemNeed)
            return "Insufficient memory for full-resolution export";
        return null;
    }
}
