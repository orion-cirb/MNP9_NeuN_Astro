# MNP9_NeuN_Astro

* **Developed for:** Rachel
* **Team:** Rouach
* **Date:** April 2022
* **Software:** Fiji

### Images description

3D images

3 channels:
  1. DAPI 
  2. NeuN or Astro
  3. MMP9 or RhoA

### Plugin description

#### Plugin 1: MNP9_NeuN

 * Detect DAPI nuclei with Stardist
 * Detect NeuN cells with Stardist
 * Detect MMP9 dots with DoG + Moments thresholding
 * Find MMP9 dots inside/outside DAPI nuclei and inside/outside NeuN cells
 * Give MMP9 dots number, volume and intensity

#### Plugin 2: MNP9_Astro

 * Detect DAPI nuclei with Stardist
 * Detect astrocytes with Median filter + thresholding
 * Detect MMP9 dots with DoG + Moments thresholding
 * Find MMP9 dots inside/outside DAPI nuclei and inside/outside astrocytes
 * Give MMP9 dots number, volume and intensity

#### Plugin 3: RohA_NeuN

 * Detect DAPI nuclei with Stardist
 * Detect NeuN cells with Stardist
 * Detect RhoA dots with DoG + Moments thresholding
 * Find RhoA dots inside/outside DAPI nuclei and inside/outside NeuN cells
 * Give RhoA dots number, volume and intensity

#### Plugin 4: RohA_Astro

 * Detect DAPI nuclei with Stardist
 * Detect astrocytes with Median filter + thresholding
 * Detect RhoA dots with DoG + Moments thresholding
 * Find RhoA dots inside/outside DAPI nuclei and inside/outside astrocytes
 * Give RhoA dots number, volume and intensity


### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin
* **Stardist** conda environment

### Version history

Version 1 released on April 8, 2022.

