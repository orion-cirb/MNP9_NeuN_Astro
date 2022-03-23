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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Objects3DPopulationColocalisation;
import mcib3d.geom.PairColocalisationOld;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurementsPopulation.MeasurePopulationColocalisation;
import mcib3d.geom2.measurementsPopulation.PairObjects3DInt;
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
    public double minDotVol = 1;
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
        boolean connectedComponentsLabelingBox = clij2.connectedComponentsLabelingBox(imgCL, labels);
        // filter size
        ClearCLBuffer labelsSizeFilter = clij2.create(imgCL.getWidth(), imgCL.getHeight(), imgCL.getDepth());
        clij2.excludeLabelsOutsideSizeRange(labels, labelsSizeFilter, minDotVol, maxDotVol);
        ImageHandler imh = ImageHandler.wrap(clij2.pull(labelsSizeFilter));
        Objects3DPopulation pop = new Objects3DPopulation(imh);
        imh.closeImagePlus();
        clij2.release(imgCL);
        clij2.release(labels);
        clij2.release(labelsSizeFilter);
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
        gd.addNumericField("Min. volume size (voxels) : ", minDotVol, 3);
        gd.addNumericField("Max. volume size (voxels) : ", maxDotVol, 3);
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
    public ClearCLBuffer DOG(ClearCLBuffer imgCL, double size1, double size2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, size1, size1, size1, size2, size2, size2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
     /**
     * Find volume of objects  
     * @param dotsPop
     * @return vol
     */
    
    public Double findDotsVolume (Objects3DPopulation dotsPop) {
        Double vol = 0.0;
        for (int i = 0; i < dotsPop.getNbObjects(); i++) {
            Object3D dotObj = dotsPop.getObject(i);
            vol += dotObj.getVolumeUnit();
        }
        return(vol);
    }
    
    /** Look for all nuclei
         Do z slice by slice stardist 
         * return nuclei population
         */
        public Objects3DPopulation stardistNucleiPop(ImagePlus imgNuc) throws IOException{
            // resize to be in a stardist-friendly scale
            int width = imgNuc.getWidth();
            int height = imgNuc.getHeight();
            double factor = 0.25;
            ImagePlus img = imgNuc.resize((int)(width*factor), (int)(height*factor), 1, "bilinear");
            IJ.run(img, "Remove Outliers", "block_radius_x=40 block_radius_y=40 standard_deviations=1 stack");
            // Go StarDist
            File starDistModelFile = new File(stardistModel);
            StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
            star.loadInput(img);
            star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThreshNuc, stardistOverlayThreshNuc, stardistOutput);
            star.run();
            // label in 3D
            ImagePlus nuclei = star.associateLabels();
            nuclei.setCalibration(cal);
            flush_close(img);
            ImagePlus newNuc = nuclei.resize(width, height, 1, "bilinear");
            newNuc.setCalibration(cal);
            flush_close(nuclei);
            ImageInt label3D = ImageInt.wrap(newNuc);
            label3D.setCalibration(cal);
            Objects3DPopulation nucPop = new Objects3DPopulation(label3D);
            Objects3DPopulation nPop = new Objects3DPopulation(nucPop.getObjectsWithinVolume(minNucVol, maxNucVol, true));
            flush_close(label3D.getImagePlus());
            return(nPop);
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
        astroPop.setCalibration(cal.pixelWidth, cal.pixelDepth, cal.getUnit());
        clij2.release(imgCLBin);
        return(astroPop);
    }    
    
     /**
     * Find dots population
     * @param imgDot
     * @return 
     */
     public Objects3DPopulation findDots(ImagePlus imgDot, Objects3DPopulation nucPop) {
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
            nucPop.draw(imgBin.getImageStack(), 0);
        Objects3DPopulation dotsPop = getPopFromClearBuffer(clij2.push(imgBin));
        dotsPop.setCalibration(cal.pixelWidth, cal.pixelDepth, cal.getUnit());
        flush_close(imgBin);
        return(dotsPop);
     }
     
     /**
      * test if object exist in population
      */
    private boolean existsObj(Object3D obj, Objects3DPopulation pop) {
        boolean index = (pop.getIndexOf(obj) >= 0) ? true : false;
        return(index);
    }
    
    /**
     * find dots inside Nucleus | NeuN
     * @param cellPop
     * @param dotsPop
     * @return 
     */
    public Objects3DPopulation findDots_in_Cells(Objects3DPopulation dotsPop, Objects3DPopulation cellPop) {
        Objects3DPopulation dotsCellPop = new Objects3DPopulation();
        Objects3DPopulationColocalisation coloc =  new Objects3DPopulationColocalisation(dotsPop, cellPop);
        ArrayList<PairColocalisationOld> pairColoc = coloc.getAllColocalisationPairs();
        for (PairColocalisationOld p : pairColoc) {
            int volColoc = p.getVolumeColoc();
            if (volColoc > 0 && !existsObj(p.getObject3D1(),dotsCellPop))
                dotsCellPop.addObject(p.getObject3D1());
        }
        dotsCellPop.setCalibration(cal.pixelWidth, cal.pixelDepth, cal.getUnit());
        return(dotsPop);
    }
    
    
    /**
     * Find dots outside nucleus
     * @param imgDot
     * @param dotsPop
     * @param nucPop
     * @return 
     */
    public Objects3DPopulation findDots_out_Nucleus(ImagePlus imgDot, Objects3DPopulation nucPop) {
        Objects3DPopulation dotsPop = findDots(imgDot, nucPop);
        return(dotsPop);
    } 
    
    
    /**
     * Save dots Population in image
     * @param dots1Pop
     * @param dots2Pop
     * @param imageName
     * @param img 
     */
    public void saveImgObjects(Objects3DPopulation pop1, Objects3DPopulation pop2, Objects3DPopulation pop3, String imageName, ImagePlus img, String outDir) {
        //create image objects population
        ImageHandler imgObj1 = ImageInt.wrap(img).createSameDimensions();
        ImageHandler imgObj2 = ImageInt.wrap(img).createSameDimensions();
        ImageHandler imgObj3 = null;
        if (pop3 != null)
            imgObj3 = ImageInt.wrap(img).createSameDimensions();
        //population green
        pop1.draw(imgObj1, 255);
        //population red       
        pop2.draw(imgObj2, 255);
        //population blue
        if (pop3 != null)
            pop3.draw(imgObj3, 255);

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
