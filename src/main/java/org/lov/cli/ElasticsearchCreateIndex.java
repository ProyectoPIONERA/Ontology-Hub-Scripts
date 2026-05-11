package org.lov.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

import org.lov.config.ElasticsearchConfig;
import org.lov.vocidex.VocidexIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import arq.cmdline.CmdGeneral;

/**
 * A command line interface that initializes a Vocidex index.
 * If an index with the given name already exists, it will be
 * deleted.
 *
 * @author Richard Cyganiak
 */
public class ElasticsearchCreateIndex extends CmdGeneral {
    private final static Logger log = LoggerFactory.getLogger(ElasticsearchCreateIndex.class);

    public static void main(String... args) {
        new ElasticsearchCreateIndex(args).mainRun();
    }

    private ElasticsearchConfig elastic;

    public ElasticsearchCreateIndex(String[] args) {
        super(args);
        getUsage().startCategory("Arguments");
        getUsage().addUsage("configFilePath", "absolute path for the configuration file  (e.g. /home/...)");
    }

    @Override
    protected String getCommandName() { return "create-index"; }
    @Override
    protected String getSummary() { return getCommandName() + " configFilePath"; }

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

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void exec() {
        VocidexIndex index = new VocidexIndex(elastic.clusterName, elastic.hostName, elastic.indexName,
                elastic.user, elastic.password);
        try {
            if (index.exists()) {
                log.info("Deleting index: " + elastic.indexName);
                index.delete();
            }
            log.info("Creating index: " + elastic.indexName);
            if (index.create()) {
                log.info("Done!");
            } else {
                log.error("Error: Index creation not acknowledged!");
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            index.close();
        }
    }
}
