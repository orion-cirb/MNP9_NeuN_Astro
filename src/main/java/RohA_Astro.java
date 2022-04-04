/*
 * Find RohA in nucleus
 * RohA in Astro+processes
 * RohA outside Astro+processes and nucleus
 * Author Philippe Mailly
 */

import MNP9_Tools.Tools;
import ij.*;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Objects3DIntPopulation;
import org.apache.commons.io.FilenameUtils;



public class RohA_Astro implements PlugIn {
    
    Tools tools = new Tools();
    
    private String imageDir = "";
    public String outDirResults = "";
    private Calibration cal = new Calibration();
    private boolean canceled = false;

    public BufferedWriter nucleus_Analyze;
   
    
    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        try {
            if (canceled) {
                IJ.showMessage(" Pluging canceled");
                return;
            }   
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }   
            // Find images with nd extension
            ArrayList<String> imageFile = tools.findImages(imageDir, "nd");
            if (imageFile == null) {
                IJ.showMessage("Error", "No images found with nd extension");
                return;
            }
            // create output folder
            outDirResults = imageDir + File.separator+ "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFile.get(0));
            
            // Find channel names
            String[] chsName = tools.findChannels(imageFile.get(0), meta, reader, true);
            
            // Find image calibration
            cal = tools.findImageCalib(meta, reader);
            
            // Write header
            String header= "Image Name\tSection volume (µm3)\t#Nucleus\t#RohA\tVol RohA\tIntensity RohA\t#RohA in nucleus\tVol RohA in Nucleus\tintensity RohA in Nucleus"
                    + "\tAstrocyte volume\t#RohA in astrocyte\tVol RohA in astrocyte\tIntensity RohA in astrocyte\t#RohA outside nucleus\tVol RohA outside nucleus\tIntensity RohA outside nucleus\t"
                    + "#RohA outside astrocyte\tVol RohA outside astrocyte\tIntensity RohA outside astrocyte\t#RohA outside cells\tVol RohA outside cells\tIntensity RohA outside cells\n";
            FileWriter fwNucleusGlobal = new FileWriter(outDirResults + "Astro_Results.xls", false);
            nucleus_Analyze = new BufferedWriter(fwNucleusGlobal);
            nucleus_Analyze.write(header);
            nucleus_Analyze.flush();
            
            // Channels dialog
            String[] channelsName = {"DAPI", "Astro", "RohA"};
            int[] channelsIndex = tools.dialog(chsName, channelsName, true);
            if ( channelsIndex == null) {
                IJ.showStatus("Plugin cancelled");
                return;
            }
            for (String f : imageFile) {
                String rootName = FilenameUtils.getBaseName(f);
                reader.setId(f);
                ImporterOptions options = new ImporterOptions();
                
                
                /**
                 * read nd
                 *
                 */
                
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Find section volume
                double secVol = reader.getSizeX() * cal.pixelWidth * reader.getSizeY() * cal.pixelHeight * reader.getSizeZ() * cal.pixelDepth;
                
                // open RohA Channel
                System.out.println("--- Opening RohA channel  ...");
                ImagePlus imgRohA = BF.openImagePlus(options)[channelsIndex[2]];
                
                // Find all MRohA staining
                Objects3DIntPopulation RohAPop = tools.findRohADots(imgRohA, null, null);
                // Compute parameters
                int RohADots = RohAPop.getNbObjects();
                System.out.println(RohADots +" RohA found");
                double RohAVol = tools.findDotsVolume(RohAPop);
                double RohAInt = tools.findDotsIntensity(RohAPop, imgRohA);
                
                // Find nucleus in DAPI channel
                System.out.println("--- Opening nucleus channel  ...");
                ImagePlus imgNucleus = BF.openImagePlus(options)[channelsIndex[0]];
                // Find nucleus
                Objects3DIntPopulation nucPop = tools.stardistNucleiPop(imgNucleus);
                int nuc = nucPop.getNbObjects();
                System.out.println(nuc +" nucleus found");
                tools.flush_close(imgNucleus);
                
                // Find RohA inside nucleus
                Objects3DIntPopulation RohAPop_DAPI = tools.findDots_in_Cells(RohAPop, nucPop);
                // Compute parameters
                int RohA_NucDots = RohAPop_DAPI.getNbObjects();
                System.out.println(RohA_NucDots +" RohA in nucleus found");
                double RohA_NucVol = tools.findDotsVolume(RohAPop_DAPI);
                double RohA_NucInt = tools.findDotsIntensity(RohAPop_DAPI, imgRohA);

                // Find RohA outside nucleus
                Objects3DIntPopulation RohAPop_OutDAPI = tools.findDots_out_Cells(imgRohA, nucPop, null);
                // Compute parameters
                int RohA_OutNucDots = RohAPop_OutDAPI.getNbObjects();
                System.out.println(RohA_OutNucDots +" RohA outside nucleus found");
                double RohA_OutNucVol = tools.findDotsVolume(RohAPop_OutDAPI);
                double RohA_OutNucInt = tools.findDotsIntensity(RohAPop_OutDAPI, imgRohA);
                
                // open Astro Channel
                System.out.println("--- Opening astrocyte channel  ...");
                ImagePlus imgAstro = BF.openImagePlus(options)[channelsIndex[1]];
                Objects3DIntPopulation astroPop = tools.findAstrocytePop(imgAstro);
                double astroVol = tools.findDotsVolume(astroPop);
                tools.flush_close(imgAstro);
                
                // Find RohA in astrocyte
                Objects3DIntPopulation RohAPop_Astro = tools.findDots_in_Cells(RohAPop, astroPop);
                // Compute parameters
                int RohA_AstroDots = RohAPop_Astro.getNbObjects();
                System.out.println(RohA_AstroDots +" RohA in astrocyte found");
                double RohA_AstroVol = tools.findDotsVolume(RohAPop_Astro);
                double RohA_AstroInt = tools.findDotsIntensity(RohAPop_Astro, imgRohA);
                
                // Find RohA outside astrocyte
                Objects3DIntPopulation RohAPop_OutAstro = tools.findDots_out_Cells(imgRohA, astroPop, null);
                // Compute parameters
                int RohA_OutAstroDots = RohAPop_OutAstro.getNbObjects();
                System.out.println(RohA_OutAstroDots +" RohA outside astrocyte found");
                double RohA_OutAstroVol = tools.findDotsVolume(RohAPop_OutAstro);
                double RohA_OutAstroInt = tools.findDotsIntensity(RohAPop_OutAstro, imgRohA);
                
                // Find RohA outside cells
                Objects3DIntPopulation RohAPop_OutCells = tools.findDots_out_Cells(imgRohA, nucPop, astroPop);
                // Compute parameters
                int RohA_OutCellsDots = RohAPop_OutCells.getNbObjects();
                System.out.println(RohA_OutCellsDots +" RohA outside cells found");
                double RohA_OutCellsVol = tools.findDotsVolume(RohAPop_OutCells);
                double RohA_OutCellsInt = tools.findDotsIntensity(RohAPop_OutCells, imgRohA);
                
                // Save image objects
                tools.saveImgObjects(nucPop, astroPop, RohAPop, rootName+"_Objects.tif", imgRohA, outDirResults);
                tools.flush_close(imgRohA);
                
                // write data
                nucleus_Analyze.write(rootName+"\t"+secVol+"\t"+nuc+"\t"+RohADots+"\t"+RohAVol+"\t"+RohAInt+"\t"+RohA_NucDots+"\t"+RohA_NucVol+"\t"+RohA_NucInt+"\t"+
                        astroVol+"\t"+RohA_AstroDots+"\t"+RohA_AstroVol+"\t"+RohA_AstroInt+"\t"+RohA_OutNucDots+"\t"+RohA_OutNucVol+"\t"+RohA_OutNucInt+"\t"+
                        RohA_OutAstroDots+"\t"+RohA_OutAstroVol+"\t"+RohA_OutAstroInt+RohA_OutCellsDots+"\t"+RohA_OutCellsVol+"\t"+RohA_OutCellsInt+"\n");
                nucleus_Analyze.flush();
            }
        } 
        catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(RohA_NeuN.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(RohA_Astro.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    
