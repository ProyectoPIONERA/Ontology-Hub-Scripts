package org.lov.cli;

import arq.cmdline.CmdGeneral;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.sparql.core.DatasetGraph;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.tdb.TDBLoader;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB;
import com.hp.hpl.jena.tdb.transaction.DatasetGraphTransaction;
import org.lov.config.ElasticsearchConfig;
import org.lov.vocidex.VocidexDocument;
import org.lov.vocidex.VocidexException;
import org.lov.vocidex.VocidexIndex;
import org.lov.vocidex.extract.AgentsExtractor;
import org.lov.vocidex.extract.LOVExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
/**
 * A command line tool that indexes an LOV dump, adding all vocabularies
 * and their terms to the index. Uses {@link LOVExtractor}.
 *
 * @author Richard Cyganiak
 */
public class ElasticsearchIndexLOV extends CmdGeneral {
    private final static Logger log = LoggerFactory.getLogger(ElasticsearchIndexLOV.class);

    public static void main(String... args) {
        new ElasticsearchIndexLOV(args).mainRun();
    }

    private ElasticsearchConfig elastic;
    private String lovDumpFile;
    private String lovTMPDumpPath;

    public ElasticsearchIndexLOV(String[] args) {
        super(args);
        getUsage().startCategory("Arguments");
        getUsage().addUsage("configFilePath", "absolute path for the configuration file  (e.g. /home/...)");
    }

    @Override
    protected String getCommandName() { return "index-lov"; }
    @Override
    protected String getSummary() { return getCommandName() + " clusterName hostname indexName lov.nq"; }

    @Override
    protected void processModulesAndArgs() {
        if (getPositional().size() < 1) {
            doHelp();
        }
        String configFilePath = getPositionalArg(0);
        //load properties from the config file
        try {
            Properties lovConfig = new Properties();
            File file = new File(configFilePath);
            InputStream is = new FileInputStream(file);
            lovConfig.load(is);
            elastic = ElasticsearchConfig.fromProperties(lovConfig);
            lovDumpFile = lovConfig.getProperty("LOV_NQ_FILE_PATH");
            lovTMPDumpPath = lovConfig.getProperty("LOV_TMP_DUMP_PATH");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void exec() {

        try {
            log.info("Loading LOV dump into TDB (disk): " + lovDumpFile);
            log.info("TDB directory: " + lovTMPDumpPath + " (use an empty directory for bulk load when retrying)");

            String tdbDir = lovTMPDumpPath;

            Dataset dataset = TDBFactory.createDataset(tdbDir);

            // TDBLoader uses the bulk loader (smaller heap footprint than RDFDataMgr.read for large N-Quads).
            DatasetGraph dsgWrapped = dataset.asDatasetGraph();
            DatasetGraphTDB dsgTdb;
            if (dsgWrapped instanceof DatasetGraphTransaction) {
                dsgTdb = ((DatasetGraphTransaction) dsgWrapped).getBaseDatasetGraph();
            } else if (dsgWrapped instanceof DatasetGraphTDB) {
                dsgTdb = (DatasetGraphTDB) dsgWrapped;
            } else {
                throw new IllegalStateException("Expected TDB-backed dataset, got: " + dsgWrapped.getClass().getName());
            }
            TDBLoader.load(dsgTdb, lovDumpFile, true);

            log.info("TDB bulk load finished.");
            log.info("Hostname: " + elastic.hostName);
            VocidexIndex index = new VocidexIndex(elastic.clusterName, elastic.hostName, elastic.indexName,
                    elastic.user, elastic.password, elastic.mappingsPath, elastic.perTypeIndices);
            try {
                if (!index.exists()) {
                    String source = (elastic.mappingsPath != null && !elastic.mappingsPath.trim().isEmpty())
                            ? "directory " + elastic.mappingsPath.trim()
                            : "packaged classpath mappings";
                    String target = elastic.perTypeIndices
                            ? "indices " + elastic.indexName + "_{class|property|...}"
                            : "index '" + elastic.indexName + "'";
                    log.info("{} not found; creating from {}.", target, source);
                    if (elastic.perTypeIndices) {
                        log.info("Clearing any partial per-type indices before create (prefix: {}).", elastic.indexName);
                        index.delete();
                    }
                    if (!index.create()) {
                        throw new VocidexException("Failed to create Elasticsearch indices. Run create-index with the same config or check Elasticsearch logs.");
                    }
                    log.info("Elasticsearch indices created (per-type=" + elastic.perTypeIndices + ").");
                }

                /* Process Agents */
                log.info("--Inserting Agents--");
                AgentsExtractor agentExtractor = new AgentsExtractor(dataset);
                int agentsIndexed = 0;
                int agentsSkipped = 0;
                for (VocidexDocument document : agentExtractor) {
                    if (elastic.indexContinueOnError) {
                        try {
                            index.addDocument(document);
                            agentsIndexed++;
                        } catch (IOException e) {
                            agentsSkipped++;
                            log.warn("Skipping agent document id={} type={}: {}",
                                    document.getId(), document.getType(), e.getMessage());
                        }
                    } else {
                        index.addDocument(document);
                        agentsIndexed++;
                    }
                }
                if (agentsSkipped > 0) {
                    log.warn("Agents finished: {} indexed, {} skipped", agentsIndexed, agentsSkipped);
                } else {
                    log.info(agentsIndexed + " Agents inserted");
                }

                /* Process LOV */
                log.info("--Inserting LOV--");
                LOVExtractor lovTransformer = new LOVExtractor(dataset);
                int lovIndexed = 0;
                int lovSkipped = 0;
                for (VocidexDocument document : lovTransformer) {
                    log.info("Indexing " + document.getId());
                    if (elastic.indexContinueOnError) {
                        try {
                            index.addDocument(document);
                            lovIndexed++;
                        } catch (IOException e) {
                            lovSkipped++;
                            log.warn("Skipping LOV document id={} type={}: {}",
                                    document.getId(), document.getType(), e.getMessage());
                        }
                    } else {
                        index.addDocument(document);
                        lovIndexed++;
                    }
                }
                if (lovSkipped > 0) {
                    log.warn("LOV terms/vocabs finished: {} indexed, {} skipped", lovIndexed, lovSkipped);
                }
                if (elastic.indexFailOnSkipped && (agentsSkipped + lovSkipped) > 0) {
                    throw new VocidexException("Elasticsearch indexing skipped "
                            + (agentsSkipped + lovSkipped) + " document(s); see warnings above.");
                }
                log.info("Done!");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                index.close();
                dataset.close();
            }
        } catch (com.hp.hpl.jena.shared.NotFoundException ex) {
            cmdError("Not found: " + ex.getMessage());
        } catch (org.lov.vocidex.VocidexException ex) {
            cmdError(ex.getMessage());
        }

    }
}
