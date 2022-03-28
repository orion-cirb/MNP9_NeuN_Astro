/*
 * Find MNP9 in nucleus
 * MNP9 in nucleus-NeuN
 * MNP9 outside nucleus
 * Author Philippe Mailly
 */

import MNP9_Tools.Tools;
import ij.*;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import mcib3d.geom.Objects3DPopulation;
import org.apache.commons.io.FilenameUtils;



public class MNP9_NeuN implements PlugIn {
    
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
            String header= "Image Name\tSection volume (µm3)\t#Nucleus\t#MNP9\tVol MNP9\tIntensity MNP9\t#MNP9 in nucleus\tVol MNP9 in Nucleus\tintensity MNP9 in Nucleus\t"
                    + "#MNP9 in NeuN\tVol MNP9 in NeuN\tIntensity MNP9 in NeuN\t#MNP9 outside nucleus\tVol MNP9 outside nucleus\tintensity MNP9 outside nucleus\t"
                    + "#MNP9 outside NeuN\tVol MNP9 outside NeuN\tintensity MNP9 outside NeuN\t#MNP9 outside cells\tVol MNP9 outside cells\tintensity MNP9 outside cells\n";
            FileWriter fwNucleusGlobal = new FileWriter(outDirResults + "MNP9_Results.xls", false);
            nucleus_Analyze = new BufferedWriter(fwNucleusGlobal);
            nucleus_Analyze.write(header);
            nucleus_Analyze.flush();
            
            // Channels dialog
            String[] channelsName = {"DAPI", "MNP9", "NeuN"};
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
                
                // open MNP9 Channel
                System.out.println("--- Opening MNP9 channel  ...");
                ImagePlus imgMNP9 = BF.openImagePlus(options)[channelsIndex[1]];
                // Find all MMNP9 dots
                Objects3DPopulation MNP9Pop = tools.findDots(imgMNP9, null, null);
                int MNP9Dots = MNP9Pop.getNbObjects();
                System.out.println(MNP9Dots+" MNP9 found");
                // Compute parameters
                double[] MNP9VolInt = tools.findDotsVolumeIntensity(MNP9Pop,imgMNP9);
                
                
                // Find nucleus in DAPI channel
                System.out.println("--- Opening nucleus channel ...");
                ImagePlus imgNucleus = BF.openImagePlus(options)[channelsIndex[0]];
                // Find nucleus
                Objects3DPopulation nucPop = tools.stardistNucleiPop(imgNucleus);
                int nuc = nucPop.getNbObjects();
                System.out.println(nuc +" nucleus found");
                tools.flush_close(imgNucleus);
                
                // Find MNP9 inside nucleus
                Objects3DPopulation MNP9Pop_DAPI = tools.findDots_in_Cells(MNP9Pop, nucPop,imgMNP9);
                // Compute parameters
                int MNP9_DapiDots = MNP9Pop_DAPI.getNbObjects();
                System.out.println(MNP9_DapiDots +" MNP9 in nucleus found");
                double[] MNP9_DapiVolInt = tools.findDotsVolumeIntensity(MNP9Pop_DAPI, imgMNP9);

                // Find MNP9 outside nucleus
                Objects3DPopulation MNP9Pop_OutDAPI = tools.findDots_out_Cells(imgMNP9, nucPop, null);
                // Compute parameters
                int MNP9_OutDapiDots = MNP9Pop_OutDAPI.getNbObjects();
                System.out.println(MNP9_OutDapiDots +" MNP9 outside nucleus found");
                double[] MNP9_OutDapiVolInt = tools.findDotsVolumeIntensity(MNP9Pop_OutDAPI, imgMNP9);

                // open NeuN Channel
                System.out.println("--- Opening NeuN channel  ...");
                ImagePlus imgNeuN = BF.openImagePlus(options)[channelsIndex[2]];
                Objects3DPopulation neuNPop = tools.stardistNucleiPop(imgNeuN);
                int NeuN = neuNPop.getNbObjects();
                System.out.println(NeuN +" neuN found");
                tools.flush_close(imgNeuN);
                
                // Find MNP9 in NeuN
                Objects3DPopulation MNP9Pop_NeuN = tools.findDots_in_Cells(MNP9Pop, neuNPop,imgMNP9);
                // Compute parameters
                int MNP9_NeuNDots = MNP9Pop_NeuN.getNbObjects();
                System.out.println(MNP9_NeuNDots +" MNP9 in NeuN cells found");
                double[] MNP9_NeuNVolInt = tools.findDotsVolumeIntensity(MNP9Pop_NeuN, imgMNP9);
                
                // Find MNP9 outside NeuN
                Objects3DPopulation MNP9Pop_OutNeuN = tools.findDots_out_Cells(imgMNP9, neuNPop, null);
                // Compute parameters
                int MNP9_OutNeuNDots = MNP9Pop_OutNeuN.getNbObjects();
                System.out.println(MNP9_OutNeuNDots +" MNP9 outside NeuN cells found");
                double[] MNP9_OutNeuNVolInt = tools.findDotsVolumeIntensity(MNP9Pop_OutNeuN, imgMNP9);
                
                // Find MNP9 outside cells
                Objects3DPopulation MNP9Pop_OutCells = tools.findDots_out_Cells(imgMNP9, neuNPop, nucPop);
                // Compute parameters
                int MNP9_OutCellsDots = MNP9Pop_OutCells.getNbObjects();
                System.out.println(MNP9_OutCellsDots +" MNP9 outside cells found");
                double[] MNP9_OutCellsVolInt = tools.findDotsVolumeIntensity(MNP9Pop_OutCells, imgMNP9);
                
                // Save image objects
                tools.saveImgObjects(nucPop, neuNPop, MNP9Pop, rootName+"_Objects.tif", imgMNP9, outDirResults);
                tools.flush_close(imgMNP9);
                
                // write data
                nucleus_Analyze.write(rootName+"\t"+secVol+"\t"+nuc+"\t"+MNP9Dots+"\t"+MNP9VolInt[0]+"\t"+MNP9VolInt[1]+"\t"+MNP9_DapiDots+"\t"+
                        MNP9_DapiVolInt[0]+"\t"+MNP9_DapiVolInt[1]+"\t"+MNP9_NeuNDots+"\t"+MNP9_NeuNVolInt[0]+"\t"+MNP9_NeuNVolInt[1]+"\t"+MNP9_OutDapiDots+"\t"+
                        MNP9_OutDapiVolInt[0]+"\t"+MNP9_OutDapiVolInt[1]+"\t"+MNP9_OutNeuNDots+"\t"+MNP9_OutNeuNVolInt[0]+"\t"+MNP9_OutNeuNVolInt[1]+"\t"+
                        MNP9_OutCellsDots+"\t"+MNP9_OutCellsVolInt[0]+"\t"+MNP9_OutCellsVolInt[1]+"\n");
                nucleus_Analyze.flush();
            }
        } 
        catch (DependencyException | ServiceException | FormatException | IOException  ex) {
            Logger.getLogger(MNP9_NeuN.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    
