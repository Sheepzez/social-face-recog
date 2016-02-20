package isaacjordan.me.FaceRecog;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.bytedeco.javacv.*;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;
import org.bytedeco.javacpp.indexer.*;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvScalar;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_calib3d.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_face.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvSaveImage;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.IntBuffer;
import static org.bytedeco.javacpp.opencv_face.*;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;

public class GrabberShow implements Runnable {
	final int INTERVAL=100;
	IplImage image;
	FaceRecognizer faceRecognizer;
	Map<Integer, String> idToName;

	public GrabberShow() {
		String trainingDir = "images";
		File root = new File(trainingDir);

        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };

        File[] imageFiles = root.listFiles(imgFilter);

        MatVector images = new MatVector(imageFiles.length);

        Mat labels = new Mat(imageFiles.length, 1, CV_32SC1);
        IntBuffer labelsBuf = labels.getIntBuffer();

        int counter = 0;
        idToName = new HashMap<Integer, String>();
        for (File image : imageFiles) {
            Mat img = imread(image.getAbsolutePath(), CV_LOAD_IMAGE_GRAYSCALE);
            
            String[] attributes = image.getName().split("-");
            int label = Integer.parseInt(attributes[0]);
            
            idToName.put(label, attributes[1]);
            images.put(counter, img);

            labelsBuf.put(counter, label);

            counter++;
        }

        //faceRecognizer = createFisherFaceRecognizer();
        //faceRecognizer = createEigenFaceRecognizer();
        faceRecognizer = createLBPHFaceRecognizer();

        System.out.println("Training face rocognizer.");
        faceRecognizer.train(images, labels);
        System.out.println("Finished training.");

	}

	public void run() {
		String classifierName = null;
		URL url = null;
		try {
			url = new URL(
					"https://raw.github.com/Itseez/opencv/2.4.0/data/haarcascades/haarcascade_frontalface_alt.xml");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		File file = null;
		try {
			file = Loader.extractResource(url, null, "classifier", ".xml");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		file.deleteOnExit();
		classifierName = file.getAbsolutePath();

		// Preload the opencv_objdetect module to work around a known bug.
		Loader.load(opencv_objdetect.class);

		// We can "cast" Pointer objects by instantiating a new object of the
		// desired class.
		CvHaarClassifierCascade classifier = new CvHaarClassifierCascade(cvLoad(classifierName));
		//CvHaarClassifierCascade face_cascade = new CvHaarClassifierCascade(cvLoad(classifierName));
		if (classifier.isNull()) {
			System.err.println("Error loading classifier file \"" + classifierName + "\".");
			System.exit(1);
		}
		
		// The available FrameGrabber classes include OpenCVFrameGrabber
		// (opencv_videoio),
		// DC1394FrameGrabber, FlyCaptureFrameGrabber, OpenKinectFrameGrabber,
		// PS3EyeFrameGrabber, VideoInputFrameGrabber, and FFmpegFrameGrabber.
		FrameGrabber grabber = null;
		try {
			grabber = FrameGrabber.createDefault(0);
			grabber.start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		// CanvasFrame, FrameGrabber, and FrameRecorder use Frame objects to
		// communicate image data.
		// We need a FrameConverter to interface with other APIs (Android, Java
		// 2D, or OpenCV).
		OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();

		// FAQ about IplImage and Mat objects from OpenCV:
		// - For custom raw processing of data, createBuffer() returns an NIO
		// direct
		// buffer wrapped around the memory pointed by imageData, and under
		// Android we can
		// also use that Buffer with Bitmap.copyPixelsFromBuffer() and
		// copyPixelsToBuffer().
		// - To get a BufferedImage from an IplImage, or vice versa, we can
		// chain calls to
		// Java2DFrameConverter and OpenCVFrameConverter, one after the other.
		// - Java2DFrameConverter also has static copy() methods that we can use
		// to transfer
		// data more directly between BufferedImage and IplImage or Mat via
		// Frame objects.
		IplImage grabbedImage = null;
		try {
			grabbedImage = converter.convert(grabber.grab());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int width = grabbedImage.width();
		int height = grabbedImage.height();
		IplImage grayImage = IplImage.create(width, height, IPL_DEPTH_8U, 1);

		// Objects allocated with a create*() or clone() factory method are
		// automatically released
		// by the garbage collector, but may still be explicitly released by
		// calling release().
		// You shall NOT call cvReleaseImage(), cvReleaseMemStorage(), etc. on
		// objects allocated this way.
		CvMemStorage storage = CvMemStorage.create();

		// CanvasFrame is a JFrame containing a Canvas component, which is
		// hardware accelerated.
		// It can also switch into full-screen mode when called with a
		// screenNumber.
		// We should also specify the relative monitor/camera response for
		// proper gamma correction.
		CanvasFrame frame = new CanvasFrame("Face File Generator", CanvasFrame.getDefaultGamma() / grabber.getGamma());
		
		int count = 0;
		
		Frame videoFrame;
		Mat videoMat = new Mat();
		OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
		CascadeClassifier face_cascade = new CascadeClassifier("data/haarcascades/haarcascade_frontalface_alt.xml");
		
		if (face_cascade.isNull()) {
			System.err.println("Error loading classifier file \"" + classifierName + "\".");
			System.exit(1);
		}
		
		try {
			while (frame.isVisible() && (videoFrame = grabber.grab()) != null) {
				/*cvClearMemStorage(storage);

				// Let's try to detect some faces! but we need a grayscale image...
				cvCvtColor(grabbedImage, grayImage, CV_BGR2GRAY);
				
				CvSeq faces = cvHaarDetectObjects(grayImage, classifier, storage, 1.1, 3,
						CV_HAAR_FIND_BIGGEST_OBJECT | CV_HAAR_DO_ROUGH_SEARCH);
				
				int total = faces.total();
				
				IplImage drawnImage = grabbedImage.clone();
				for (int i = 0; i < total; i++) {
					CvRect r = new CvRect(cvGetSeqElem(faces, i));
					int x = r.x(), y = r.y(), w = r.width(), h = r.height();
					cvRectangle(drawnImage, cvPoint(x, y), cvPoint(x + w, y + h), CvScalar.RED, 1, CV_AA, 0);
				}

				Frame convertedFrame = converter.convert(drawnImage);
				frame.showImage(convertedFrame);
				
				if (total > 0) {
					CvRect r = new CvRect(cvGetSeqElem(faces, 0));
				    int x = r.x(), y = r.y(), w = r.width(), h = r.height();
				    cvSetImageROI(grayImage, cvRect(x, y, w, h));
				    
				    IplImage resizeImage = IplImage.create(250, 250, grayImage.depth(), grayImage.nChannels());
				    cvResize(grayImage.clone(), grayImage);
				    
				    Mat resizedMat = new Mat(resizeImage);
				    int result = faceRecognizer.predict(resizedMat);
				    System.out.println(result);
				}
				count++;*/
				
	            videoMat = converterToMat.convert(videoFrame);
	            Mat videoMatGray = new Mat();
	            // Convert the current frame to grayscale:
	            cvtColor(videoMat, videoMatGray, COLOR_BGRA2GRAY);
	            equalizeHist(videoMatGray, videoMatGray);

	            Point p = new Point();
	            RectVector faces = new RectVector();
	            // Find the faces in the frame:
	            face_cascade.detectMultiScale(videoMatGray, faces);

	            // At this point you have the position of the faces in
	            // faces. Now we'll get the faces, make a prediction and
	            // annotate it in the video. Cool or what?
	            for (int i = 0; i < faces.size(); i++) {
	                Rect face_i = faces.get(i);

	                Mat face = new Mat(videoMatGray, face_i);
	                // If fisher face recognizer is used, the face need to be
	                // resized.
	                // resize(face, face_resized, new Size(im_width, im_height),
	                // 1.0, 1.0, INTER_CUBIC);

	                // Now perform the prediction, see how easy that is:
	                int prediction = faceRecognizer.predict(face);

	                // And finally write all we've found out to the original image!
	                // First of all draw a green rectangle around the detected face:
	                rectangle(videoMat, face_i, new Scalar(0, 255, 0, 1));

	                // Create the text we will annotate the box with:
	                String box_text = idToName.get(prediction);
	                // Calculate the position for annotated text (make sure we don't
	                // put illegal values in there):
	                int pos_x = Math.max(face_i.tl().x() - 10, 0);
	                int pos_y = Math.max(face_i.tl().y() - 10, 0);
	                // And now put it into the image:
	                putText(videoMat, box_text, new Point(pos_x, pos_y),
	                        FONT_HERSHEY_PLAIN, 1.0, new Scalar(0, 255, 0, 2.0));
	            }
	            // Show the result:
	            IplImage image = new IplImage(videoMat);
	            Frame convertedFrame = converter.convert(image);
				frame.showImage(convertedFrame);
			    
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		frame.dispose();
		
		try {
			grabber.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}