package MNP9_Tools;


import StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.RGBStackMerge;
import ij.process.AutoThresholder;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.geom2.measurementsPopulation.MeasurePopulationColocalisation;
import mcib3d.image3d.ImageByte;
import mcib3d.image3d.ImageHandler;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author phm
 */
public class Tools {

    public boolean canceled = true;
    public double minNucVol= 50;
    public double maxNucVol = Double.MAX_VALUE;
    public double minDotVol = 0.01;
    public double maxDotVol = Double.MAX_VALUE;
    private String thMethod = "Li";
    public Calibration cal;
    
    // StarDist
    public Object syncObject = new Object();
    public final double stardistPercentileBottom = 0.2;
    public final double stardistPercentileTop = 99.8;
    public final double stardistProbThreshNuc = 0.65;
    public final double stardistOverlayThreshNuc = 0.03;
    public File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    public String stardistModel = "";
    public String stardistOutput = "Label Image"; 
    public String nucleusDetector = "";
        
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));

    public CLIJ2 clij2 = CLIJ2.getInstance();
    
     /*
    Find starDist models in Fiji models folder
    */
    public String[] findStardistModels() {
        FilenameFilter filter = (dir, name) -> name.endsWith(".zip");
        File[] modelList = modelsPath.listFiles(filter);
        String[] models = new String[modelList.length];
        for (int i = 0; i < modelList.length; i++) {
            models[i] = modelList[i].getName();
        }
        Arrays.sort(models);
        return(models);
    } 
    
     /**
     * return objects population in an ClearBuffer image
     * @param imgCL
     * @return pop objects population
     */

    public Objects3DIntPopulation getPopFromClearBuffer(ClearCLBuffer imgCL) {
        ClearCLBuffer labels = clij2.create(imgCL.getWidth(), imgCL.getHeight(), imgCL.getDepth());
        clij2.connectedComponentsLabelingBox(imgCL, labels);
        // filter size
        ClearCLBuffer labelsSizeFilter = clij2.create(imgCL.getWidth(), imgCL.getHeight(), imgCL.getDepth());
        clij2.release(imgCL);
        clij2.excludeLabelsOutsideSizeRange(labels, labelsSizeFilter, minDotVol, maxDotVol);
        clij2.release(labels);
        ImagePlus img = clij2.pull(labelsSizeFilter);
        clij2.release(labelsSizeFilter);
        ImageHandler imh = ImageHandler.wrap(img);
        imh.setScale(cal.pixelWidth, cal.pixelDepth, cal.getUnit());
        flush_close(img);
        Objects3DIntPopulation pop = new Objects3DIntPopulation(imh);
        imh.closeImagePlus();
        return(pop);
    }  
    
    
    
    public int[] dialog(String[] chs, String[] channelNames, boolean th) {
        String[] models = findStardistModels();
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        gd.addMessage("--- Dots filter size ---", Font.getFont(Font.MONOSPACED), Color.blue);
        gd.addNumericField("Min. volume size (µm3) : ", minDotVol, 3);
        gd.addNumericField("Max. volume size (µm3) : ", maxDotVol, 3);
        gd.addMessage("StarDist model", Font.getFont("Monospace"), Color.blue);
        if (models.length > 0) {
            gd.addChoice("StarDist model :",models, models[0]);
        }
        else {
            gd.addMessage("No StarDist model found in Fiji !!", Font.getFont("Monospace"), Color.red);
            gd.addFileField("StarDist model :", stardistModel);
        }
        if (th) {
            String [] thMethods = new AutoThresholder().getMethods();
            gd.addChoice("Threshold method for Astrocyte : ", thMethods, thMethod); 
        }
        gd.showDialog();
        if (gd.wasCanceled())
            canceled = true;
        int[] chChoices = new int[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = ArrayUtils.indexOf(chs, gd.getNextChoice());
        minDotVol = gd.getNextNumber();
        maxDotVol = gd.getNextNumber();
        if (models.length > 0) {
            stardistModel = modelsPath+File.separator+gd.getNextChoice();
        }
        else {
            stardistModel = gd.getNextString();
        }
        if (th) 
            thMethod = gd.getNextChoice();
        return(chChoices);
    }
     
    // Flush and close images
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }

    /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
     * @param fill 
     */
    public ClearCLBuffer threshold(ClearCLBuffer imgCL, String thMed) {
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        return(imgCLBin);
    }
    
    /* Median filter 
     * Using CLIJ2
     * @param ClearCLBuffer
     * @param sizeXY
     * @param sizeZ
     */ 
    public ClearCLBuffer median_filter(ClearCLBuffer  imgCL, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.mean3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        return(imgCLMed);
    }
    
    
/**
     * Find images in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No Image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        return(images);
    }
    
     /**
     * Find channels name
     * @param imageName
     * @param meta
     * @param reader
     * @param bioformat
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader, boolean bioformat) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                    if (!bioformat) {
                        channels[n] = channels[n].replace("_", "-");
                        channels[n] = "w"+(n+1)+channels[n];
                    }
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelFluor(0, n);
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelExcitationWavelength(0, n).toString();
                break;    
            default :
                for (int n = 0; n < chs; n++)
                    channels[0] = Integer.toString(n);
        }
        return(channels);         
    }
    
    /**
     * Resize values in pixels
     */
    private void resizeMinVol_MaxVol(Calibration cal) {
        double factor = cal.pixelWidth * cal.pixelHeight * cal.pixelDepth;
        minNucVol = minNucVol / factor;
        maxNucVol = maxNucVol / factor;
        minDotVol = minDotVol / factor;
        maxDotVol = maxDotVol / factor;
    }
    
    
    /**
     * Find image calibration
     * @param meta
     * @param reader
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta, ImageProcessorReader reader) {
        cal = new Calibration();  
        // read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        // resize minVol & maxVol values in pixel 
        resizeMinVol_MaxVol(cal);
        return(cal);
    }
    
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param size1
     * @param size2
     * @return imgGauss
     */ 
    private ClearCLBuffer DOG(ClearCLBuffer imgCL, double size1, double size2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, size1, size1, size1, size2, size2, size2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
    /**
     * Get all objects population in one object
     */
    private Object3DInt getInOneObject(Objects3DIntPopulation pop) {
        ImageHandler imh = pop.drawImage();
        ImageByte imhTh = imh.thresholdAboveExclusive(1);
        imh.closeImagePlus();
        Object3DInt obj = new Object3DInt(imhTh);
        imhTh.closeImagePlus();
        obj.setResXY(cal.pixelWidth);
        obj.setResZ(cal.pixelDepth);
        return(obj);
    }
    
     /**
     * Find volume of objects  
     * @param dotsPop
     * @return vol
     */
    
    public double findDotsVolume (Objects3DIntPopulation dotsPop) {
        IJ.showStatus("Findind object's volume");
        List<Double[]> results = dotsPop.getMeasurementsList(new MeasureVolume().getNamesMeasurement());
        double sum = results.stream().map(arr -> arr[1]).reduce(0.0, Double::sum);
        System.out.println("Sum of volume "+sum);
        return(sum);
    }
    
    
     /**
     * Find sum intensity of objects  
     * @param dotsPop
     * @param img
     * @return intensity
     */
    
    public double findDotsIntensity (Objects3DIntPopulation dotsPop, ImagePlus img) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        IJ.showStatus("Findind object's intensity");
        ImageHandler imh = ImageHandler.wrap(img);
        List<Double[]> results = dotsPop.getMeasurementsIntensityList(new MeasureIntensity().getNamesMeasurement(), imh);
        double sum = results.stream().map(arr -> arr[1]).reduce(0.0, Double::sum);
        System.out.println("Sum of intensity "+sum);
        return(sum);
    }

    
    /** Look for all nuclei
         Do z slice by slice stardist 
         * return nuclei population
     * @param imgNuc
     * @return 
     * @throws java.io.IOException
         */
        public Objects3DIntPopulation stardistNucleiPop(ImagePlus imgNuc) throws IOException{
            // resize to be in a stardist-friendly scale
            int width = imgNuc.getWidth();
            int height = imgNuc.getHeight();
            float factor = 0.25f;
            ImagePlus img = imgNuc.resize((int)(width*factor), (int)(height*factor), 1, "none");
            IJ.run(img, "Remove Outliers", "block_radius_x=40 block_radius_y=40 standard_deviations=1 stack");
            // Go StarDist
            File starDistModelFile = new File(stardistModel);
            StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
            star.loadInput(img);
            star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThreshNuc, stardistOverlayThreshNuc, stardistOutput);
            star.run();
            // label in 3D
            ImagePlus nuclei = star.associateLabels().resize(width, height, 1, "none");
            flush_close(img);
            ClearCLBuffer labels = clij2.push(nuclei);
            flush_close(nuclei);
            // filter size
            ClearCLBuffer labelsSizeFilter = clij2.create(labels.getWidth(), labels.getHeight(), labels.getDepth());
            clij2.excludeLabelsOutsideSizeRange(labels, labelsSizeFilter, minNucVol, maxNucVol);
            clij2.release(labels);
            // find population
            Objects3DIntPopulation nucPop = getPopFromClearBuffer(labelsSizeFilter);
            clij2.release(labelsSizeFilter);
            return(nucPop);
        }
    
    /**
    * Find Astrocyte
    */
    public Objects3DIntPopulation findAstrocytePop(ImagePlus img) {
        IJ.showStatus("Finding astrocyte");
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgMed = median_filter(imgCL, 2, 2);
        clij2.release(imgCL);
        ClearCLBuffer imgCLBin = threshold(imgMed, thMethod);
        clij2.release(imgMed);
        Objects3DIntPopulation astroPop = getPopFromClearBuffer(imgCLBin);
        clij2.release(imgCLBin);
        return(astroPop);
    }    
    
     /**
     * Find dots population
     * @param imgDot
     * @return 
     */
     public Objects3DIntPopulation findDots(ImagePlus imgDot, Objects3DIntPopulation nucPop, Objects3DIntPopulation cellPop) {
        IJ.showStatus("Finding dots");
        ClearCLBuffer imgCL = clij2.push(imgDot);
        ClearCLBuffer imgDOG = DOG(imgCL, 1, 2);
        clij2.release(imgCL);
        ClearCLBuffer imgCLBin = threshold(imgDOG, "Moments");
        clij2.release(imgDOG);
        ImagePlus imgBin = clij2.pull(imgCLBin);
        clij2.release(imgCLBin);
        imgBin.setCalibration(cal);
        if (nucPop != null)
            nucPop.getObjects3DInt().forEach(object3DInt -> object3DInt.drawObject(ImageHandler.wrap(imgBin), 0));
        if (cellPop != null)
            cellPop.getObjects3DInt().forEach(object3DInt -> object3DInt.drawObject(ImageHandler.wrap(imgBin), 0));
        ClearCLBuffer maskCL = clij2.push(imgBin);
        Objects3DIntPopulation dotsPop = getPopFromClearBuffer(maskCL);
        clij2.release(maskCL);
        flush_close(imgBin);
        return(dotsPop);
     }
     /**
     * Find dots population
     * @param imgDot
     * @param nucPop
     * @param cellPop
     * @return 
     */
     public Objects3DIntPopulation findRohADots(ImagePlus imgDot, Objects3DIntPopulation nucPop, Objects3DIntPopulation cellPop) {
        IJ.showStatus("Finding RohA");
        ClearCLBuffer imgCL = clij2.push(imgDot);
        ClearCLBuffer imgDOG = DOG(imgCL, 1, 2);
        clij2.release(imgCL);
        ClearCLBuffer imgMed = median_filter(imgDOG, 4, 4);
        clij2.release(imgDOG);
        ClearCLBuffer imgCLBin = threshold(imgMed, "Moments");
        clij2.release(imgMed);
        ImagePlus imgBin = clij2.pull(imgCLBin);
        clij2.release(imgCLBin);
        imgBin.setCalibration(cal);
        if (nucPop != null)
            nucPop.getObjects3DInt().forEach(object3DInt -> object3DInt.drawObject(ImageHandler.wrap(imgBin), 0));
        if (cellPop != null)
            cellPop.getObjects3DInt().forEach(object3DInt -> object3DInt.drawObject(ImageHandler.wrap(imgBin), 0));
        ClearCLBuffer maskCL = clij2.push(imgBin);
        Objects3DIntPopulation dotsPop = getPopFromClearBuffer(maskCL);
        clij2.release(maskCL);
        flush_close(imgBin);
        return(dotsPop);
     }
     
    /**
     * find dots inside Nucleus | NeuN
     * @param cellsPop
     * @param dotsPop
     * @return 
     */
    public Objects3DIntPopulation findDots_in_Cells(Objects3DIntPopulation dotsPop, Objects3DIntPopulation cellsPop) {
        IJ.showStatus("Finding coloc ....");
        Objects3DIntPopulation dotsCellPop = new Objects3DIntPopulation();
        MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(dotsPop, cellsPop);
        for (Object3DInt dot : dotsPop.getObjects3DInt()) {
            double[] colocVal = coloc.getValuesObject1(dot.getValue());
            if(colocVal.length > 0) {
                dot.setResXY(cal.pixelWidth);
                dot.setResZ(cal.pixelDepth);
                dotsCellPop.addObject(dot);
            }
        }
        return(dotsCellPop);
    } 
    
    
    /**
     * Find dots outside nucleus
     * @param imgDot
     * @param dotsPop
     * @param nucPop
     * @return 
     */
    public Objects3DIntPopulation findDots_out_Cells(ImagePlus imgDot, Objects3DIntPopulation nucPop, Objects3DIntPopulation cellPop) {
        Objects3DIntPopulation dotsPop = findDots(imgDot, nucPop, cellPop);
        return(dotsPop);
    } 
    
    
    /**
     * Save dots Population in image
     * @param pop1
     * @param pop2
     * @param pop3
     * @param imageName
     * @param img 
     * @param outDir 
     */
    public void saveImgObjects(Objects3DIntPopulation pop1, Objects3DIntPopulation pop2, Objects3DIntPopulation pop3, String imageName, ImagePlus img, String outDir) {
        //create image objects population
        ImageHandler imgObj1 = pop1.drawImage();
        ImageHandler imgObj2 = pop2.drawImage();
        ImageHandler imgObj3 = ImageHandler.wrap(img).createSameDimensions();
        if (pop3 != null) {
            pop3.getObjects3DInt().forEach(object3DInt -> object3DInt.drawObject(imgObj3, 255));
        }
            
        // save image for objects population
        ImagePlus[] imgColors = {imgObj1.getImagePlus(), imgObj2.getImagePlus(), imgObj3.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir + imageName + "_Objects.tif"); 
        imgObj1.closeImagePlus();
        imgObj2.closeImagePlus();
        imgObj3.closeImagePlus();
        flush_close(imgObjects);
    }
    
    
    
}
