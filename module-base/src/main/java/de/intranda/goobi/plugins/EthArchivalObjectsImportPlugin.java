package de.intranda.goobi.plugins;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Processproperty;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPluginVersion2;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class EthArchivalObjectsImportPlugin implements IImportPluginVersion2 {

    @Getter
    private String title = "intranda_import_eth_archival_objects";
    @Getter
    private PluginType type = PluginType.Import;

    @Getter
    private List<ImportType> importTypes;

    @Getter
    @Setter
    private Prefs prefs;
    @Getter
    @Setter
    private String importFolder;

    @Setter
    private MassImportForm form;

    @Setter
    private boolean testMode = false;

    @Getter
    @Setter
    private File file;

    @Setter
    private String workflowTitle;

    private boolean runAsGoobiScript = false;

    /**
     * define what kind of import plugin this is
     */
    public EthArchivalObjectsImportPlugin() {
        importTypes = new ArrayList<>();
        importTypes.add(ImportType.Record);
    }

    /**
     * read the configuration file
     */
    private void readConfig() {
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig("intranda_import_eth_no_catalogue");
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;
        try {
            myconfig = xmlConfig.configurationAt("//config[./template = '" + workflowTitle + "']");
        } catch (IllegalArgumentException e) {
            myconfig = xmlConfig.configurationAt("//config[./template = '*']");
        }

        if (myconfig != null) {
            runAsGoobiScript = myconfig.getBoolean("/runAsGoobiScript", false);
        }
    }

    /**
     * This method is used to actually create the Goobi processes this is done based on previously created records
     */
    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // result of the creation process
        List<ImportObject> answer = new ArrayList<>();

        // run through all records and create a Goobi process for each of it
        for (Record record : records) {
            ImportObject io = new ImportObject();

            // Split the string and generate a hashmap for all needed metadata
            String[] fields = record.getData().split("\t");

            // create a new mets file
            try {
                Fileformat fileformat = new MetsMods(prefs);

                // create digital document
                DigitalDocument dd = new DigitalDocument();
                fileformat.setDigitalDocument(dd);

                // create physical DocStruct
                DocStructType physicalType = prefs.getDocStrctTypeByName("BoundBook");
                DocStruct physical = dd.createDocStruct(physicalType);
                dd.setPhysicalDocStruct(physical);

                // set imagepath
                MetadataType pathimagefilesType = prefs.getMetadataTypeByName("pathimagefiles");
                Metadata newmd = new Metadata(pathimagefilesType);
                newmd.setValue("/images/");
                physical.addMetadata(newmd);

                // create publication
                DocStruct work = null;

                if (fields.length < 3) {
                    // beginn of 2 columns only (Archival objects)

                    // HSA Archival material
                    //
                    // - Identifier
                    // - Datum
                    //
                    // Sample:
                    //
                    // HSA_123_001 13.01.2004
                    // HSA_123_002 14.01.2004
                    // HSA125  15.01.2004
                    // HSA_123_003 16.01.2004

                    // create an archival object
                    DocStructType logicalType = prefs.getDocStrctTypeByName("ArchivalObject");
                    work = dd.createDocStruct(logicalType);
                    dd.setLogicalDocStruct(work);

                    // create metadata field for shelfmark
                    Metadata mdShelfmark = new Metadata(prefs.getMetadataTypeByName("shelfmarksource"));
                    mdShelfmark.setValue(fields[0].trim());
                    work.addMetadata(mdShelfmark);

                    // create date
                    Metadata mdDate = new Metadata(prefs.getMetadataTypeByName("datedigit"));
                    mdDate.setValue(fields[1].trim());
                    work.addMetadata(mdDate);

                    Processproperty pp0 = new Processproperty();
                    pp0.setTitel("Datum");
                    pp0.setWert(fields[1].trim());
                    pp0.setContainer("Scanvorgaben");
                    io.getProcessProperties().add(pp0);

                    // all additional collections that where selected
                    if (form != null) {
                        MetadataType typeCollection = prefs.getMetadataTypeByName("singleDigCollection");
                        for (String c : form.getDigitalCollections()) {
                            Metadata md = new Metadata(typeCollection);
                            md.setValue(c);
                            work.addMetadata(md);
                        }
                    }

                    // create metadata field for CatalogIDDigital with cleaned value
                    String newID = record.getId().replaceAll("\\W", "_") + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                    record.setId(newID);
                    Metadata md1 = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
                    md1.setValue(record.getId().replaceAll("\\W", "_"));
                    work.addMetadata(md1);

                    // end of 2 columns only (Archival objects)
                } else {

                    // begin of 3 columns

                    // Archival boxes with maps in it works
                    //
                    // - Box
                    // - Map
                    // - Date
                	//
                    // Sample:
                    //
                    // Box01 Map01  13.01.2004
                    // Box01 Map02  14.01.2004
                    // Box02 Map01  15.01.2004

                	// create an archival object
                    DocStructType logicalType = prefs.getDocStrctTypeByName("ArchivalBoxObject");
                    work = dd.createDocStruct(logicalType);
                    dd.setLogicalDocStruct(work);

                    // create metadata field for shelfmark
                    Metadata mdBox = new Metadata(prefs.getMetadataTypeByName("Box"));
                    mdBox.setValue(fields[0].trim());
                    work.addMetadata(mdBox);

                    // create metadata field for shelfmark
                    Metadata mdMap = new Metadata(prefs.getMetadataTypeByName("Mappe"));
                    mdMap.setValue(fields[1].trim());
                    work.addMetadata(mdMap);

                    // create date
                    Metadata mdDate = new Metadata(prefs.getMetadataTypeByName("datedigit"));
                    mdDate.setValue(fields[2].trim());
                    work.addMetadata(mdDate);

                    Processproperty pp0 = new Processproperty();
                    pp0.setTitel("Datum");
                    pp0.setWert(fields[2].trim());
                    pp0.setContainer("Scanvorgaben");
                    io.getProcessProperties().add(pp0);

                    // all additional collections that where selected
                    if (form != null) {
                        MetadataType typeCollection = prefs.getMetadataTypeByName("singleDigCollection");
                        for (String c : form.getDigitalCollections()) {
                            Metadata md = new Metadata(typeCollection);
                            md.setValue(c);
                            work.addMetadata(md);
                        }
                    }

                    // create metadata field for CatalogIDDigital with cleaned value
                    String newID = (fields[0].trim() + "_" + fields[1].trim()).replaceAll("\\W", "_") + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                    record.setId(newID);
                    Metadata md1 = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
                    md1.setValue(record.getId().replaceAll("\\W", "_"));
                    work.addMetadata(md1);
                }
                // end of 3 columns

                // set the title for the Goobi process
                io.setProcessTitle(record.getId().replaceAll("\\W", "_"));
                String fileName = getImportFolder() + File.separator + io.getProcessTitle() + ".xml";
                io.setMetsFilename(fileName);
                fileformat.write(fileName);
                io.setImportReturnValue(ImportReturnValue.ExportFinished);
            } catch (UGHException e) {
                log.error("Error while creating Goobi processes in the EthNoCatalogueImportPlugin", e);
                io.setImportReturnValue(ImportReturnValue.WriteError);
            }

            // now add the process to the list
            answer.add(io);
        }
        return answer;
    }

    /**
     * decide if the import shall be executed in the background via GoobiScript or not
     */
    @Override
    public boolean isRunnableAsGoobiScript() {
        readConfig();
        return runAsGoobiScript;
    }

    @Override
    public List<Record> splitRecords(String content) {
        if (StringUtils.isBlank(workflowTitle)) {
            workflowTitle = form.getTemplate().getTitel();
        }
        readConfig();

        // the list where the records are stored
        List<Record> recordList = new ArrayList<>();

        // run through the content line by line
        String lines[] = content.split("\\r?\\n");

        // generate a record for each process to be created
        for (String line : lines) {

            // Split the string and create a record
            String[] fields = line.split("\t");
            String id = fields[0].trim();
            Record r = new Record();
            r.setId(id);
            r.setData(line);
            recordList.add(r);
        }

        // return the list of all generated records
        return recordList;
    }

    /**
     * This method is used to generate records based on the imported data these records will then be used later to generate the Goobi processes
     */
    @Override
    public List<Record> generateRecordsFromFile() {
        return null;
    }

    /* *************************************************************** */
    /*                                                                 */
    /* the following methods are mostly not needed for typical imports */
    /*                                                                 */
    /* *************************************************************** */

    @Override
    public List<String> splitIds(String ids) {
        return null;
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public void deleteFiles(List<String> arg0) {
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> arg0) {
        return null;
    }

    @Override
    public List<String> getAllFilenames() {
        return null;
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null;
    }

    @Override
    public String getProcessTitle() {
        return null;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
    }

    @Override
    public void setData(Record arg0) {
    }

    @Override
    public void setDocstruct(DocstructElement arg0) {
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        return null;
    }

}