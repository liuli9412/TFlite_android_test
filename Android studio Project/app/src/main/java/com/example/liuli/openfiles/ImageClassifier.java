package com.example.liuli.openfiles;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.LinkedList;




public abstract class ImageClassifier {

    private static final String TAG = "移动端分类器";

    private static final int RESULTS_TO_SHOW = 1;

    private static final int DIM_BATCH_SIZE = 1;

    private static final int DIM_PIXEL_SIZE = 3;

    private int[] intValues = new int[getImageSizeX() * getImageSizeY()];

    protected Interpreter tflite;


    /** Labels corresponding to the output of the vision model. */
    protected List<String> labelList;


    /** A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs. */
    protected ByteBuffer imgData = null;

    /** multi-stage low pass filter * */
    private float[][] filterLabelProbArray = null;

    private static final int FILTER_STAGES = 3;
    private static final float FILTER_FACTOR = 0.4f;

    private int carless = 0;
    private int carnormal = 0;
    private int carmore = 0;

    private PriorityQueue<Map.Entry<String,Float>> sortedLables =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String,Float> o1,Map.Entry<String,Float> o2){
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });



    /* Initializes an ImageClassifier */
    ImageClassifier(Activity activity) throws IOException {

        tflite = new Interpreter(loadModelFile(activity));
        labelList = loadLabelList(activity);
        imgData = ByteBuffer.allocateDirect(
                DIM_BATCH_SIZE
                    * getImageSizeX()
                    * getImageSizeY()
                    * DIM_PIXEL_SIZE
                    * getNumBytesPerChannel());

        imgData.order(ByteOrder.nativeOrder());
        filterLabelProbArray = new float[FILTER_STAGES][getNumLabels()];
        Log.d(TAG,"Created a Tensorflow Lite Image Classifier.");
    }

    String classifyFrame(Bitmap bitmap){
        if (tflite == null){
            Log.e(TAG,"Image classifier has not been initialized; Skipped.");
            return null;
        }
        convertBitmapToByteBuffer(bitmap);

        long startTime = SystemClock.uptimeMillis();
        runInference();
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG,"Timecost to run model inference:"+Long.toString(endTime-startTime));

        applyFilter();

        String textToShow = printTopKLabels();

        Log.d(TAG,textToShow);
        return textToShow;
    }



    void applyFilter() {

        int numLabels = getNumLabels();

        // Low pass filter 'labelProbArray' into the first stage of the filter.

        for (int j = 0;j<numLabels;++j){
            filterLabelProbArray[0][j] += FILTER_FACTOR * (getProbability(j)-filterLabelProbArray[0][j]);
        }

        // Low pass filter each stage into the next.
        for (int i = 1;i<FILTER_STAGES;++i){
            for(int j = 0;j<numLabels;++j){
                filterLabelProbArray[i][j] +=
                        FILTER_FACTOR*(filterLabelProbArray[i-1][j]-filterLabelProbArray[i][j]);
            }
        }

        // Copy the last stage filter output back to `labelProbArray`.
        for (int j = 0;j<numLabels;++j){
            setProbability(j,filterLabelProbArray[FILTER_STAGES-1][j]);
        }
    }

    /** Closes tflite to release resources. */

    public void close(){
        tflite.close();
        tflite = null;
    }


    /* Reads label list from Assets*/

    private List<String> loadLabelList(Activity activity) throws IOException{
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(activity.getAssets().open(getLabelPath())));
        String line;
        while ((line=reader.readLine())!=null){
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    /*Memory-map the model file in Assets*/
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException{
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);
    }

    /*Writes Image data into a {@code ByteBuffer}. */
    private void convertBitmapToByteBuffer(Bitmap bitmap){
        if (imgData == null){
            return;
        }
        imgData.rewind();

        bitmap.getPixels(intValues,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());

        // Convert the image to floating point
        int pixel = 0;
        long startTime = SystemClock.uptimeMillis();
        for(int i = 0;i<getImageSizeX();++i){
            for(int j = 0;j<getImageSizeY();++j){
                final int val = intValues[pixel++];
                addPixelValue(val);
            }
        }
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG,"Timecost to put values int ByteBuffer:"+Long.toString(endTime-startTime));
    }


    protected String printTopKLabels(){
        for(int i = 0;i<getNumLabels();++i){
            sortedLables.add(
                    new AbstractMap.SimpleEntry<>(labelList.get(i),getNormalizedProbability(i)));
            if(sortedLables.size()>RESULTS_TO_SHOW){
                sortedLables.poll();
            }
        }
        String textToShow = "";
        final int size = sortedLables.size();
        for(int i = 0;i<size;++i){
            Map.Entry<String,Float> label = sortedLables.poll();
            Log.d(TAG, "printTopKLabels"+label.getKey()+Double.toString(label.getValue()));
            textToShow = label.getKey();
        }
        return textToShow;
    }


    /**
     * Get the name of the model file stored in Assets.
     *
     * @return
     */

    protected abstract String getModelPath();

    /* Get the name of the label file stored in Assets */

    protected abstract String getLabelPath();



    /**
     * Get the image size along the x axis.
     *
     * @return
     */

    protected abstract int getImageSizeX();


    /**
     * Get the image size along the y axis.
     *
     * @return
     */

    protected abstract int getImageSizeY();


    /**
     * Get the number of bytes that is used to store a single color channel value.
     *
     * @return
     */

    protected abstract int getNumBytesPerChannel();


    /**
     * Add pixelValue to byteBuffer.
     *
     * @param pixelValue
     */

    protected abstract void addPixelValue(int pixelValue);


    /**
     * Read the probability value for the specified label This is either the original value as it was
     * read from the net's output or the updated value after the filter was applied.
     *
     * @param labelIndex
     * @return
     */



    protected abstract float getProbability(int labelIndex);

    /**
     * Set the probability value for the specified label.
     *
     * @param labelIndex
     * @param value
     */

    protected abstract void setProbability(int labelIndex, Number value);


    /**
     * Get the normalized probability value for the specified label. This is the final value as it
     * will be shown to the user.
     *
     * @return
     */



    protected abstract float getNormalizedProbability(int labelIndex);


    /*
    *
    * Run inference using the prepared input in {@link #imageData}. Afterwards, the result will be
    * provided by getProbability().
    *
    * <p> This additional method is necessary, because we don't have a common base for different
    * primitive data types
    *
    * */
    protected abstract void runInference();

    /*
    * Get the total number of labels.
    *
    * */

    protected int getNumLabels() {
        return labelList.size();
    }


}
