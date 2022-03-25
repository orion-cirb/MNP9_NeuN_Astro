package Tools;


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
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.image3d.ImageByte;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
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

    public Objects3DPopulation getPopFromClearBuffer(ClearCLBuffer imgCL) {
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
        flush_close(img);
        Objects3DPopulation pop = new Objects3DPopulation(imh);
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
    private Object3D getInOneObject(Objects3DPopulation pop, ImagePlus img) {
        ImageHandler imh = ImageHandler.wrap(img);
        pop.draw(imh, 255);
        Object3D obj = new Objects3DPopulation(imh).getObject(0);
        imh.closeImagePlus();
        obj.setResXY(cal.pixelWidth);
        obj.setResZ(cal.pixelDepth);
        return(obj);
    }
    
     /**
     * Find volume and intensity of objects  
     * @param dotsPop
     * @return vol
     */
    
    public double[] findDotsVolumeIntensity (Objects3DPopulation dotsPop, ImagePlus img) {
        IJ.showStatus("Findind object's volume / intensity");
        double [] volInt = {0, 0};
        ImageHandler imh = ImageHandler.wrap(img);
        Object3D dot = getInOneObject(dotsPop, img);
        volInt[0] = dot.getVolumeUnit();
        volInt[1] = dot.getIntegratedDensity(imh);
        return(volInt);
    }
    
    
    /** Look for all nuclei
         Do z slice by slice stardist 
         * return nuclei population
     * @param imgNuc
     * @return 
     * @throws java.io.IOException
         */
        public Objects3DPopulation stardistNucleiPop(ImagePlus imgNuc) throws IOException{
            // resize to be in a stardist-friendly scale
            int width = imgNuc.getWidth();
            int height = imgNuc.getHeight();
            float factor = 0.25f;
            ImagePlus img = imgNuc.resize((int)(width*factor), (int)(height*factor), 1, "bilinear");
            IJ.run(img, "Remove Outliers", "block_radius_x=40 block_radius_y=40 standard_deviations=1 stack");
            // Go StarDist
            File starDistModelFile = new File(stardistModel);
            StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
            star.loadInput(img);
            star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThreshNuc, stardistOverlayThreshNuc, stardistOutput);
            star.run();
            // label in 3D
            ImagePlus nuclei = star.associateLabels().resize(width, height, 1, "bilinear");
            flush_close(img);
            ClearCLBuffer labels = clij2.push(nuclei);
            flush_close(nuclei);
            // filter size
            ClearCLBuffer labelsSizeFilter = clij2.create(labels.getWidth(), labels.getHeight(), labels.getDepth());
            clij2.excludeLabelsOutsideSizeRange(labels, labelsSizeFilter, minNucVol, maxNucVol);
            clij2.release(labels);
            // find population
            Objects3DPopulation nucPop = getPopFromClearBuffer(labelsSizeFilter);
            clij2.release(labelsSizeFilter);
            nucPop.setCalibration(cal.pixelWidth, cal.pixelDepth, "microns");
            return(nucPop);
        }
    
    /**
    * Find Astrocyte
    */
    public Objects3DPopulation findAstrocytePop(ImagePlus img) {
        IJ.showStatus("Finding astrocyte");
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgMed = median_filter(imgCL, 2, 2);
        clij2.release(imgCL);
        ClearCLBuffer imgCLBin = threshold(imgMed, thMethod);
        clij2.release(imgMed);
        Objects3DPopulation astroPop = getPopFromClearBuffer(imgCLBin);
        astroPop.setCalibration(cal.pixelWidth, cal.pixelDepth, "microns");
        clij2.release(imgCLBin);
        return(astroPop);
    }    
    
     /**
     * Find dots population
     * @param imgDot
     * @return 
     */
     public Objects3DPopulation findDots(ImagePlus imgDot, Objects3DPopulation nucPop, Objects3DPopulation cellPop) {
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
            nucPop.draw(ImageHandler.wrap(imgBin), 0);
        if (cellPop != null)
            cellPop.draw(ImageHandler.wrap(imgBin), 0);
        ClearCLBuffer maskCL = clij2.push(imgBin);
        Objects3DPopulation dotsPop = getPopFromClearBuffer(maskCL);
        clij2.release(maskCL);
        dotsPop.setCalibration(cal.pixelWidth, cal.pixelDepth, "microns");
        flush_close(imgBin);
        return(dotsPop);
     }
    
    /**
     * find dots inside Nucleus | NeuN
     * @param cellPop
     * @param dotsPop
     * @return 
     */
    public Objects3DPopulation findDots_in_Cells(Objects3DPopulation dotsPop, Objects3DPopulation cellPop) {
        Objects3DPopulation dotInCellPop = new Objects3DPopulation();
        for (int n = 0; n < dotsPop.getNbObjects(); n++) {
            Object3D dot = dotsPop.getObject(n);
            for (int j = 0; j < cellPop.getNbObjects(); j++) {
                Object3D cell = cellPop.getObject(j);
                if (dot.hasOneVoxelColoc(cell))
                dotInCellPop.addObject(dot);
            }
        }
        return(dotInCellPop);
    } 
    
    
    /**
     * Find dots outside nucleus
     * @param imgDot
     * @param dotsPop
     * @param nucPop
     * @return 
     */
    public Objects3DPopulation findDots_out_Cells(ImagePlus imgDot, Objects3DPopulation nucPop, Objects3DPopulation cellPop) {
        Objects3DPopulation dotsPop = findDots(imgDot, nucPop, cellPop);
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
    public void saveImgObjects(Objects3DPopulation pop1, Objects3DPopulation pop2, Objects3DPopulation pop3, String imageName, ImagePlus img, String outDir) {
        //create image objects population
        ImageHandler imgObj1 = ImageInt.wrap(img).createSameDimensions();
        ImageHandler imgObj2 = ImageInt.wrap(img).createSameDimensions();
        ImageHandler imgObj3 = null;
        if (pop3 != null)
            imgObj3 = ImageInt.wrap(img).createSameDimensions();
        //population green
        pop1.draw(imgObj1);
        //population red       
        pop2.draw(imgObj2);
        //population blue
        if (pop3 != null)
            pop3.draw(imgObj3);

        // save image for objects population
        ImagePlus[] imgColors = {imgObj1.getImagePlus(), imgObj2.getImagePlus(), imgObj3.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir + imageName + "_Objects.tif"); 
        flush_close(imgObj1.getImagePlus());
        flush_close(imgObj2.getImagePlus());
        if (pop3 != null)
            flush_close(imgObj3.getImagePlus());
        flush_close(imgObjects);
    }
    
    
    
}
