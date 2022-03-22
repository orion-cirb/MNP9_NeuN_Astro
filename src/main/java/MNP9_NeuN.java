/*
 * Find MNP9 in nucleus
 * MNP9 in nucleus-NeuN
 * MNP9 outside nucleus
 * Author Philippe Mailly
 */

import Tools.Tools;
import ij.*;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
            
            // Write header
            String header= "Image Name\tSection volume (µm3)\t#Nucleus\t#MNP9\tVol MNP9\t#MNP9 in nucleus\tVol MNP9 in Nucleus\t#MNP9 in NeuN\tVol MNP9 in NeuN\t"
                    + "#MNP9 outside nucleus\tVol MNP9 outside nucleus\n";
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
                
                // open MNP9 Channel
                System.out.println("--- Opening MNP9 channel  ...");
                ImagePlus imgMNP9 = BF.openImagePlus(options)[channelsIndex[1]];
                // Find all MMNP9 dots
                Objects3DPopulation MNP9Pop = tools.findDots(imgMNP9, null);
                System.out.println(MNP9Pop.getNbObjects());
                
                
                // Find nucleus in DAPI channel
                System.out.println("--- Opening nucleus channel ...");
                ImagePlus imgNucleus = BF.openImagePlus(options)[channelsIndex[0]];
                
                // Find section volume
                double secVol = reader.getSizeX() * tools.cal.pixelWidth * reader.getSizeY() * tools.cal.pixelHeight * reader.getSizeZ() * tools.cal.pixelDepth;
                // Find nucleus
                Objects3DPopulation nucPop = tools.stardistNucleiPop(imgNucleus);

                
                // Find MNP9 inside nucleus
                Objects3DPopulation MNP9Pop_DAPI = tools.findDots_in_Cells(MNP9Pop, nucPop);

                // Find MNP9 outside nucleus
                Objects3DPopulation MNP9Pop_OutDAPI = tools.findDots_out_Nucleus(imgMNP9, nucPop);

                // open NeuN Channel
                System.out.println("--- Opening NeuN channel  ...");
                ImagePlus imgNeuN = BF.openImagePlus(options)[channelsIndex[2]];
                Objects3DPopulation neuNPop = tools.stardistNucleiPop(imgNeuN);
                System.out.println(neuNPop.getNbObjects() +" neuN found");

                // Find MNP9 in NeuN
                Objects3DPopulation MNP9Pop_NeuN = tools.findDots_in_Cells(MNP9Pop_DAPI, neuNPop);

                // Save image objects
                tools.saveImgObjects(nucPop, neuNPop, MNP9Pop, rootName+"_Objects.tif", imgNeuN, outDirResults);
                tools.flush_close(imgMNP9);
                tools.flush_close(imgNeuN);
                tools.flush_close(imgNucleus);

                // write data
                int nuc = nucPop.getNbObjects();
                int NeuN = neuNPop.getNbObjects();
                int MNP9Dots = MNP9Pop.getNbObjects();
                double MNP9Vol = tools.findDotsVolume(MNP9Pop);
                int MNP9_DapiDots = MNP9Pop_DAPI.getNbObjects();
                double MNP9_DapiVol = tools.findDotsVolume(MNP9Pop_DAPI);
                int MNP9_OutDapiDots = MNP9Pop_OutDAPI.getNbObjects();
                double MNP9_OutDapiVol = tools.findDotsVolume(MNP9Pop_OutDAPI);
                int MNP9_DapiNeuNDots = MNP9Pop_NeuN.getNbObjects();
                double MNP9_DapiNeuNVol = tools.findDotsVolume(MNP9Pop_NeuN);

                nucleus_Analyze.write(rootName+"\t"+secVol+"\t"+nuc+"\t"+MNP9Dots+"\t"+MNP9Vol+"\t"+MNP9_DapiDots+"\t"+MNP9_DapiVol+"\t"+MNP9_DapiNeuNDots+
                        "\t"+MNP9_DapiNeuNVol+"\t"+MNP9_OutDapiDots+"\t"+MNP9_OutDapiVol+"\n");
                nucleus_Analyze.flush();
            }
                        

        } 
        catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(MNP9_NeuN.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    
