/*
 * Find MNP9 in nucleus
 * MNP9 in Astro+processes
 * MNP9 outside Astro+processes and nucleus
 * Author Philippe Mailly
 */

import Tools.Tools;
import ij.*;
import ij.gui.WaitForUserDialog;
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



public class MNP9_Astro implements PlugIn {
    
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
            String header= "Image Name\tSection volume (µm3)\t#Nucleus\t#MNP9\tVol MNP9\t#MNP9 in nucleus\tVol MNP9 in Nucleus\t#MNP9 in astrocyte\tVol MNP9 in astrocyte\t"
                    + "#MNP9 outside nucleus\tVol MNP9 outside nucleus\t#MNP9 outside astrocyte\tVol MNP9 outside astrocyte\n";
            FileWriter fwNucleusGlobal = new FileWriter(outDirResults + "MNP9_Results.xls", false);
            nucleus_Analyze = new BufferedWriter(fwNucleusGlobal);
            nucleus_Analyze.write(header);
            nucleus_Analyze.flush();
            
            // Channels dialog
            String[] channelsName = {"DAPI", "Astro", "MNP9"};
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
                
                // open MNP9 Channel
                System.out.println("--- Opening MNP9 channel  ...");
                ImagePlus imgMNP9 = BF.openImagePlus(options)[channelsIndex[2]];
                
                // Find all MMNP9 dots
                Objects3DPopulation MNP9Pop = tools.findDots(imgMNP9, null);
                
                // Find nucleus in DAPI channel
                System.out.println("--- Opening nucleus channel  ...");
                ImagePlus imgNucleus = BF.openImagePlus(options)[channelsIndex[0]];
                
                // Find section volume
                double secVol = reader.getSizeX() * cal.pixelWidth * reader.getSizeY() * cal.pixelHeight * reader.getSizeZ() * cal.pixelDepth;
                // Find nucleus
                Objects3DPopulation nucPop = tools.stardistNucleiPop(imgNucleus);
                                
                // Find MNP9 inside nucleus
                Objects3DPopulation MNP9Pop_DAPI = tools.findDots_in_Cells(MNP9Pop, nucPop);

                // Find MNP9 outside nucleus
                Objects3DPopulation MNP9Pop_OutDAPI = tools.findDots_out_Nucleus(imgMNP9, nucPop);
                
                // open Astro Channel
                System.out.println("--- Opening astrocyte channel  ...");
                ImagePlus imgAstro = BF.openImagePlus(options)[channelsIndex[1]];
                Objects3DPopulation astroPop = tools.findAstrocytePop(imgAstro);

                // Find MNP9 in astrocyte
                Objects3DPopulation MNP9Pop_Astro = tools.findDots_in_Cells(MNP9Pop, astroPop);
                
                // Find MNP9 outside astrocyte
                Objects3DPopulation MNP9Pop_OutAstro = tools.findDots_out_Nucleus(imgMNP9, astroPop);
                
                // Save image objects
                tools.saveImgObjects(nucPop, astroPop, MNP9Pop, rootName+"_Objects.tif", imgAstro, outDirResults);
                tools.flush_close(imgMNP9);
                tools.flush_close(imgAstro);
                tools.flush_close(imgNucleus);

                // write data
                int nuc = nucPop.getNbObjects();
                double astroVol = tools.findDotsVolume(astroPop);
                int MNP9Dots = MNP9Pop.getNbObjects();
                double MNP9Vol = tools.findDotsVolume(MNP9Pop);
                int MNP9_DapiDots = MNP9Pop_DAPI.getNbObjects();
                double MNP9_DapiVol = tools.findDotsVolume(MNP9Pop_DAPI);
                int MNP9_OutDapiDots = MNP9Pop_OutDAPI.getNbObjects();
                double MNP9_OutDapiVol = tools.findDotsVolume(MNP9Pop_OutDAPI);
                int MNP9_AstroDots = MNP9Pop_Astro.getNbObjects();
                double MNP9_AstroVol = tools.findDotsVolume(MNP9Pop_Astro);
                int MNP9_OutAstroDots = MNP9Pop_OutAstro.getNbObjects();
                double MNP9_OutAstroVol = tools.findDotsVolume(MNP9Pop_OutAstro);

                nucleus_Analyze.write(rootName+"\t"+secVol+"\t"+nuc+"\t"+MNP9Dots+"\t"+MNP9Vol+"\t"+MNP9_DapiDots+"\t"+MNP9_DapiVol+"\t"+MNP9_AstroDots+
                        "\t"+MNP9_AstroVol+"\t"+MNP9_OutDapiDots+"\t"+MNP9_OutDapiVol+"\t"+MNP9_OutAstroDots+"\t"+MNP9_OutAstroVol+"\n");
                nucleus_Analyze.flush();
            }
                        

        } 
        catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(MNP9_NeuN.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    
