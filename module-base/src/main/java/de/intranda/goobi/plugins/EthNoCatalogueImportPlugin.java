package de.intranda.goobi.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
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
public class EthNoCatalogueImportPlugin implements IImportPluginVersion2 {

    @Getter
    private String title = "intranda_import_eth_no_catalogue";
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
    private String collection;

    /**
     * define what kind of import plugin this is
     */
    public EthNoCatalogueImportPlugin() {
        importTypes = new ArrayList<>();
        importTypes.add(ImportType.Record);
    }

    /**
     * read the configuration file
     */
    private void readConfig() {
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
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
    @SuppressWarnings("unchecked")
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
                if (record.getId().contains("_")) {

                    // create multi volume work
                    DocStructType anchorType = prefs.getDocStrctTypeByName("MultiVolumeWork");
                    DocStruct anchor = dd.createDocStruct(anchorType);
                    dd.setLogicalDocStruct(anchor);

                    // add catalogue id to anchor
                    Metadata anchorid = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
                    anchorid.setValue(record.getId().substring(0, record.getId().indexOf("_")));
                    anchor.addMetadata(anchorid);

                    // create metadata field for main title
                    if (fields.length > 3) {
                        Metadata mdTitle = new Metadata(prefs.getMetadataTypeByName("TitleDocMain"));
                        mdTitle.setValue(fields[2].trim());
                        anchor.addMetadata(mdTitle);
                    }

                    // add collection to work
                    Metadata mdCollection = new Metadata(prefs.getMetadataTypeByName("singleDigCollection"));
                    mdCollection.setValue(fields[2].trim());
                    anchor.addMetadata(mdCollection);

                    // add additional collections to anchor as well
                    MetadataType typeCollection = prefs.getMetadataTypeByName("singleDigCollection");
                    for (String c : form.getDigitalCollections()) {
                        Metadata md = new Metadata(typeCollection);
                        md.setValue(c);
                        anchor.addMetadata(md);
                    }

                    // create volume
                    DocStructType volumeType = prefs.getDocStrctTypeByName("Volume");
                    work = dd.createDocStruct(volumeType);
                    anchor.addChild(work);
                } else {

                    // create a monograph
                    DocStructType logicalType = prefs.getDocStrctTypeByName("Monograph");
                    work = dd.createDocStruct(logicalType);
                    dd.setLogicalDocStruct(work);
                }

                // create metadata field for CatalogIDDigital with cleaned value
                Metadata md1 = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
                md1.setValue(record.getId().replaceAll("\\W", "_"));
                work.addMetadata(md1);

                // create metadata field for year and sorting number
                if (record.getId().contains("_")) {
                    String year = record.getId().substring(record.getId().indexOf("_") + 1);

                    // year - not written, because in ETH it is not a year but a volume number
                    // Metadata md3 = new Metadata(prefs.getMetadataTypeByName("PublicationYear"));
                    // md3.setValue(year);
                    // work.addMetadata(md3);

                    // Sorting number
                    Metadata md4 = new Metadata(prefs.getMetadataTypeByName("CurrentNoSorting"));
                    md4.setValue(year);
                    work.addMetadata(md4);
                }

                // create metadata field for shelfmark
                Metadata mdShelfmark = new Metadata(prefs.getMetadataTypeByName("shelfmarksource"));
                mdShelfmark.setValue(fields[1].trim());
                work.addMetadata(mdShelfmark);

                // add collection to work
                Metadata mdCollection = new Metadata(prefs.getMetadataTypeByName("singleDigCollection"));
                mdCollection.setValue(fields[2].trim());
                work.addMetadata(mdCollection);

                // create metadata field for main title
                if (fields.length > 3) {
                    Metadata mdTitle = new Metadata(prefs.getMetadataTypeByName("TitleDocMain"));
                    mdTitle.setValue(fields[2].trim());
                    work.addMetadata(mdTitle);
                }

                // all additional collections that where selected
                if (form != null) {
                    MetadataType typeCollection = prefs.getMetadataTypeByName("singleDigCollection");
                    for (String c : form.getDigitalCollections()) {
                        Metadata md = new Metadata(typeCollection);
                        md.setValue(c);
                        work.addMetadata(md);
                    }
                }

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