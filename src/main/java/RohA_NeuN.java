/*
 * Find RohA in nucleus
 * RohA in nucleus-NeuN
 * RohA outside nucleus
 * Author Philippe Mailly
 */

import MNP9_Tools.Tools;
import ij.*;
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



public class RohA_NeuN implements PlugIn {
    
    Tools tools = new Tools();
    
    private String imageDir = "";
    public String outDirResults = "";
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
            tools.cal = tools.findImageCalib(meta, reader);
            System.out.println("XYCal ="+tools.cal.pixelWidth+", ZCal ="+tools.cal.pixelDepth);
            // Write header
            String header= "Image Name\tSection volume (µm3)\t#Nucleus\t#RohA\tVol RohA\tIntensity RohA\t#RohA in nucleus\tVol RohA in Nucleus\tintensity RohA in Nucleus\t"
                    + "#RohA in NeuN\tVol RohA in NeuN\tIntensity RohA in NeuN\t#RohA outside nucleus\tVol RohA outside nucleus\tintensity RohA outside nucleus\t"
                    + "#RohA outside NeuN\tVol RohA outside NeuN\tintensity RohA outside NeuN\t#RohA outside cells\tVol RohA outside cells\tintensity RohA outside cells\n";
            FileWriter fwNucleusGlobal = new FileWriter(outDirResults + "RohA_Results.xls", false);
            nucleus_Analyze = new BufferedWriter(fwNucleusGlobal);
            nucleus_Analyze.write(header);
            nucleus_Analyze.flush();
            
            // Channels dialog
            String[] channelsName = {"DAPI", "RohA", "NeuN"};
            int[] channelsIndex;
            channelsIndex = tools.dialog(chsName, channelsName, false);
            if (channelsIndex == null) {
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
                double secVol = reader.getSizeX() * tools.cal.pixelWidth * reader.getSizeY() * tools.cal.pixelHeight * reader.getSizeZ() * tools.cal.pixelDepth;
                
                // open RohA Channel
                System.out.println("--- Opening RohA channel  ...");
                ImagePlus imgRohA = BF.openImagePlus(options)[channelsIndex[1]];
                // Find all MRohA dots
                Objects3DIntPopulation RohAPop = tools.findRohADots(imgRohA, null, null);
                int RohADots = RohAPop.getNbObjects();
                System.out.println(RohADots+" RohA found");
                // Compute parameters
                double RohAVol = tools.findDotsVolume(RohAPop);
                double RohAInt = tools.findDotsIntensity(RohAPop, imgRohA);
                
                
                // Find nucleus in DAPI channel
                System.out.println("--- Opening nucleus channel ...");
                ImagePlus imgNucleus = BF.openImagePlus(options)[channelsIndex[0]];
                // Find nucleus
                Objects3DIntPopulation nucPop = tools.stardistNucleiPop(imgNucleus);
                int nuc = nucPop.getNbObjects();
                System.out.println(nuc +" nucleus found");
                tools.flush_close(imgNucleus);
                
                // Find RohA inside nucleus
                Objects3DIntPopulation RohAPop_DAPI = tools.findDots_in_Cells(RohAPop, nucPop);
                // Compute parameters
                int RohA_DapiDots = RohAPop_DAPI.getNbObjects();
                System.out.println(RohA_DapiDots +" RohA in nucleus found");
                double RohA_DapiVol = tools.findDotsVolume(RohAPop_DAPI);
                double RohA_DapiInt = tools.findDotsIntensity(RohAPop_DAPI, imgRohA);

                // Find RohA outside nucleus
                Objects3DIntPopulation RohAPop_OutDAPI = tools.findDots_out_Cells(imgRohA, nucPop, null);
                // Compute parameters
                int RohA_OutDapiDots = RohAPop_OutDAPI.getNbObjects();
                System.out.println(RohA_OutDapiDots +" RohA outside nucleus found");
                double RohA_OutDapiVol = tools.findDotsVolume(RohAPop_OutDAPI);
                double RohA_OutDapiInt = tools.findDotsIntensity(RohAPop_OutDAPI, imgRohA);

                // open NeuN Channel
                System.out.println("--- Opening NeuN channel  ...");
                ImagePlus imgNeuN = BF.openImagePlus(options)[channelsIndex[2]];
                Objects3DIntPopulation neuNPop = tools.stardistNucleiPop(imgNeuN);
                int NeuN = neuNPop.getNbObjects();
                System.out.println(NeuN +" neuN found");
                tools.flush_close(imgNeuN);
                
                // Find RohA in NeuN
                Objects3DIntPopulation RohAPop_NeuN = tools.findDots_in_Cells(RohAPop, neuNPop);
                // Compute parameters
                int RohA_NeuNDots = RohAPop_NeuN.getNbObjects();
                System.out.println(RohA_NeuNDots +" RohA in NeuN cells found");
                double RohA_NeuNVol = tools.findDotsVolume(RohAPop_NeuN);
                double RohA_NeuNInt = tools.findDotsIntensity(RohAPop_NeuN, imgRohA);
                
                // Find RohA outside NeuN
                Objects3DIntPopulation RohAPop_OutNeuN = tools.findDots_out_Cells(imgRohA, neuNPop, null);
                // Compute parameters
                int RohA_OutNeuNDots = RohAPop_OutNeuN.getNbObjects();
                System.out.println(RohA_OutNeuNDots +" RohA outside NeuN cells found");
                double RohA_OutNeuNVol = tools.findDotsVolume(RohAPop_OutNeuN);
                double RohA_OutNeuNInt = tools.findDotsIntensity(RohAPop_OutNeuN, imgRohA);
                
                // Find RohA outside cells
                Objects3DIntPopulation RohAPop_OutCells = tools.findDots_out_Cells(imgRohA, neuNPop, nucPop);
                // Compute parameters
                int RohA_OutCellsDots = RohAPop_OutCells.getNbObjects();
                System.out.println(RohA_OutCellsDots +" RohA outside cells found");
                double RohA_OutCellsVol = tools.findDotsVolume(RohAPop_OutCells);
                double RohA_OutCellsInt = tools.findDotsIntensity(RohAPop_OutCells, imgRohA);
                
                // Save image objects
                tools.saveImgObjects(nucPop, neuNPop, RohAPop, rootName+"_Objects.tif", imgRohA, outDirResults);
                tools.flush_close(imgRohA);
                
                // write data
                nucleus_Analyze.write(rootName+"\t"+secVol+"\t"+nuc+"\t"+RohADots+"\t"+RohAVol+"\t"+RohAInt+"\t"+RohA_DapiDots+"\t"+RohA_DapiVol+"\t"+RohA_DapiInt+"\t"
                        +RohA_NeuNDots+"\t"+RohA_NeuNVol+"\t"+RohA_NeuNInt+"\t"+RohA_OutDapiDots+"\t"+RohA_OutDapiVol+"\t"+RohA_OutDapiInt+"\t"
                        +RohA_OutNeuNDots+"\t"+RohA_OutNeuNVol+"\t"+RohA_OutNeuNInt+"\t"+RohA_OutCellsDots+"\t"+RohA_OutCellsVol+"\t"+RohA_OutCellsInt+"\n");
                nucleus_Analyze.flush();
            }
        } 
        catch (DependencyException | ServiceException | FormatException | IOException | NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(RohA_NeuN.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    
