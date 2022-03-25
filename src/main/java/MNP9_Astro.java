/*
 * Find MNP9 in nucleus
 * MNP9 in Astro+processes
 * MNP9 outside Astro+processes and nucleus
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
            String header= "Image Name\tSection volume (µm3)\t#Nucleus\t#MNP9\tVol MNP9\tIntensity MNP9\t#MNP9 in nucleus\tVol MNP9 in Nucleus\tintensity MNP9 in Nucleus"
                    + "\tAstrocyte volume\t#MNP9 in astrocyte\tVol MNP9 in astrocyte\tIntensity MNP9 in astrocyte\t#MNP9 outside nucleus\tVol MNP9 outside nucleus\tIntensity MNP9 outside nucleus\t"
                    + "#MNP9 outside astrocyte\tVol MNP9 outside astrocyte\tIntensity MNP9 outside astrocyte#MNP9 outside cells\tVol MNP9 outside cells\tIntensity MNP9 outside cells\n";
            FileWriter fwNucleusGlobal = new FileWriter(outDirResults + "Astro_Results.xls", false);
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
                
                // Find section volume
                double secVol = reader.getSizeX() * cal.pixelWidth * reader.getSizeY() * cal.pixelHeight * reader.getSizeZ() * cal.pixelDepth;
                
                // open MNP9 Channel
                System.out.println("--- Opening MNP9 channel  ...");
                ImagePlus imgMNP9 = BF.openImagePlus(options)[channelsIndex[2]];
                
                // Find all MMNP9 dots
                Objects3DPopulation MNP9Pop = tools.findDots(imgMNP9, null, null);
                // Compute parameters
                int MNP9Dots = MNP9Pop.getNbObjects();
                System.out.println(MNP9Dots +" MNP9 found");
                double[] MNP9VolInt = tools.findDotsVolumeIntensity(MNP9Pop, imgMNP9);
                
                // Find nucleus in DAPI channel
                System.out.println("--- Opening nucleus channel  ...");
                ImagePlus imgNucleus = BF.openImagePlus(options)[channelsIndex[0]];
                // Find nucleus
                Objects3DPopulation nucPop = tools.stardistNucleiPop(imgNucleus);
                int nuc = nucPop.getNbObjects();
                System.out.println(nuc +" nucleus found");
                tools.flush_close(imgNucleus);
                
                // Find MNP9 inside nucleus
                Objects3DPopulation MNP9Pop_DAPI = tools.findDots_in_Cells(MNP9Pop, nucPop,imgMNP9);
                // Compute parameters
                int MNP9_NucDots = MNP9Pop_DAPI.getNbObjects();
                System.out.println(MNP9_NucDots +" MNP9 in nucleus found");
                double[] MNP9_NucVolInt = tools.findDotsVolumeIntensity(MNP9Pop_DAPI, imgMNP9);

                // Find MNP9 outside nucleus
                Objects3DPopulation MNP9Pop_OutDAPI = tools.findDots_out_Cells(imgMNP9, nucPop, null);
                // Compute parameters
                int MNP9_OutNucDots = MNP9Pop_OutDAPI.getNbObjects();
                System.out.println(MNP9_OutNucDots +" MNP9 outside nucleus found");
                double[] MNP9_OutNucVolInt = tools.findDotsVolumeIntensity(MNP9Pop_OutDAPI, imgMNP9);
                
                // open Astro Channel
                System.out.println("--- Opening astrocyte channel  ...");
                ImagePlus imgAstro = BF.openImagePlus(options)[channelsIndex[1]];
                Objects3DPopulation astroPop = tools.findAstrocytePop(imgAstro);
                double astroVol = tools.findDotsVolumeIntensity(astroPop, imgMNP9)[0];
                tools.flush_close(imgAstro);
                
                // Find MNP9 in astrocyte
                Objects3DPopulation MNP9Pop_Astro = tools.findDots_in_Cells(MNP9Pop, astroPop,imgMNP9);
                // Compute parameters
                int MNP9_AstroDots = MNP9Pop_Astro.getNbObjects();
                System.out.println(MNP9_AstroDots +" MNP9 in astrocyte found");
                double[] MNP9_AstroVolInt = tools.findDotsVolumeIntensity(MNP9Pop_Astro, imgMNP9);
                
                // Find MNP9 outside astrocyte
                Objects3DPopulation MNP9Pop_OutAstro = tools.findDots_out_Cells(imgMNP9, astroPop, null);
                // Compute parameters
                int MNP9_OutAstroDots = MNP9Pop_OutAstro.getNbObjects();
                System.out.println(MNP9_OutAstroDots +" MNP9 outside astrocyte found");
                double[] MNP9_OutAstroVolInt = tools.findDotsVolumeIntensity(MNP9Pop_OutAstro, imgMNP9);
                
                // Find MNP9 outside cells
                Objects3DPopulation MNP9Pop_OutCells = tools.findDots_out_Cells(imgMNP9, nucPop, astroPop);
                // Compute parameters
                int MNP9_OutCellsDots = MNP9Pop_OutCells.getNbObjects();
                System.out.println(MNP9_OutCellsDots +" MNP9 outside cells found");
                double[] MNP9_OutCellsVolInt = tools.findDotsVolumeIntensity(MNP9Pop_OutCells, imgMNP9);
                
                // Save image objects
                tools.saveImgObjects(nucPop, astroPop, MNP9Pop, rootName+"_Objects.tif", imgMNP9, outDirResults);
                tools.flush_close(imgMNP9);
                
                // write data
                nucleus_Analyze.write(rootName+"\t"+secVol+"\t"+nuc+"\t"+MNP9Dots+"\t"+MNP9VolInt[0]+"\t"+MNP9VolInt[1]+"\t"+MNP9_NucDots+"\t"+MNP9_NucVolInt[0]+"\t"
                        +MNP9_NucVolInt[1]+"\t"+astroVol+"\t"+MNP9_AstroDots+"\t"+MNP9_AstroVolInt[0]+"\t"+MNP9_AstroVolInt[1]+"\t"+MNP9_OutNucDots+"\t"+
                        MNP9_OutNucVolInt[0]+"\t"+MNP9_OutNucVolInt[1]+"\t"+MNP9_OutAstroDots+"\t"+MNP9_OutAstroVolInt[0]+"\t"+MNP9_OutAstroVolInt[1]+
                        MNP9_OutCellsDots+"\t"+MNP9_OutCellsVolInt[0]+"\t"+MNP9_OutCellsVolInt[1]+"\n");
                nucleus_Analyze.flush();
            }
        } 
        catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(MNP9_NeuN.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    
