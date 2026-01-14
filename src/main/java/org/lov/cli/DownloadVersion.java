package org.lov.cli;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.hp.hpl.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.lov.LovAggregatorAgent;
import org.lov.objects.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import arq.cmdline.CmdGeneral;

/**
 * ...
 * 
 */
public class DownloadVersion extends CmdGeneral {
	private final static Logger log = LoggerFactory.getLogger(DownloadVersion.class);
	
	public static void main(String... args) {
		new DownloadVersion(args).mainRun();
	}

	private String vocabularyURI;
	private Properties lovConfig;
	
	public DownloadVersion(String[] args) {
		super(args);
		getUsage().startCategory("Arguments");
		getUsage().addUsage("vocabularyURI", "URI of the vocabulary (e.g. http://...)");
		getUsage().addUsage("configFilePath", "absolute path for the configuration file  (e.g. /home/...)");
	}
	
	@Override
    protected String getCommandName() {
		return "downloadVersion";
	}
	
	@Override
	protected String getSummary() {
		return getCommandName() + "vocabularyURI configFilePath";
	}

	@Override
	protected void processModulesAndArgs() {
		if (getPositional().size() < 2) {
			doHelp();
		}
		vocabularyURI = getPositionalArg(0);
		try {
			lovConfig = new Properties();
			File file = new File(getPositionalArg(1));
			InputStream is = new FileInputStream(file);
			lovConfig.load(is);			
		} catch (FileNotFoundException e) {
			//e.printStackTrace();
            log.error("File not found ", e);
		} catch (IOException e) {
			//e.printStackTrace();
            log.error("IOException", e);
		}
	}


    @Override
    protected void exec() {
        try {
            String agentURI = vocabularyURI;
            int idxHash = agentURI.indexOf('#');
            if (idxHash >= 0) agentURI = agentURI.substring(0, idxHash);

            String ct = headContentType(agentURI);
            if (ct != null && ct.toLowerCase().contains("text/html")) {
                agentURI = resolveDownloadURL(agentURI);
            }

            Vocabulary vocab = new Vocabulary();
            vocab.setUri(agentURI);


            List<LovAggregatorAgent> agents = new ArrayList<LovAggregatorAgent>(1);
            ExecutorService executor = Executors.newFixedThreadPool(1);
            agents.add(new LovAggregatorAgent(vocab));
            executor.invokeAll(agents, 15, TimeUnit.SECONDS);
            shutdownAndAwaitTermination(executor);

            Model model = agents.get(0).getVocabModel();

            if (model == null || model.isEmpty()) {
                String resolved = resolveDownloadURL(vocabularyURI);
                String inFormat = inferJenaLangByExtension(resolved);

                model = com.hp.hpl.jena.rdf.model.ModelFactory.createDefaultModel();
                java.net.URL url = new java.net.URL(resolved);
                java.net.URLConnection conn = url.openConnection();
                conn.setRequestProperty("Accept", "text/turtle, application/rdf+xml, application/xml, text/plain");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                try (java.io.InputStream in = new java.io.BufferedInputStream(conn.getInputStream())) {
                    model.read(in, null, inFormat);
                }
            }

            if (model != null && !model.isEmpty()) {
                String uniqueID = java.util.UUID.randomUUID().toString();

                File vocabVersionDir = new File(lovConfig.getProperty("VERSIONS_TEMP_PATH"));
                if (!vocabVersionDir.exists()) vocabVersionDir.mkdir();

                File versionFile = new File(vocabVersionDir, uniqueID + ".n3");
                if (!versionFile.exists()) versionFile.createNewFile();

                try (OutputStream fopn3 = new BufferedOutputStream(new FileOutputStream(versionFile))) {
                    RDFDataMgr.write(fopn3, model, Lang.N3);
                }

                System.out.println(versionFile.getAbsolutePath());
            } else {
                log.info("Version has not been created, model is null at this point");
            }
        } catch (Exception e) {
            //e.printStackTrace();
            log.error("Exception", e);
        }
    }


    private void shutdownAndAwaitTermination(ExecutorService pool) {
		   pool.shutdown(); // Disable new tasks from being submitted
		   try {
		     // Wait a while for existing tasks to terminate
		     if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
		       pool.shutdownNow(); // Cancel currently executing tasks
		       // Wait a while for tasks to respond to being cancelled
		       if (!pool.awaitTermination(5, TimeUnit.SECONDS)){}
		    	 
		     }
		   } catch (InterruptedException ie) {
		     // (Re-)Cancel if current thread also interrupted
		     pool.shutdownNow();
		     // Preserve interrupt status
		     Thread.currentThread().interrupt();
		   }
    }

    private String resolveDownloadURL(String iriOrUrl) throws IOException {
        String base = iriOrUrl;
        int hash = base.indexOf('#');
        if (hash >= 0) base = base.substring(0, hash);


        if (base.startsWith("https://github.com/") && base.contains("/blob/")) {
            String raw = base.replace("https://github.com/", "https://raw.githubusercontent.com/")
                    .replace("/blob/", "/");
            return raw;
        }

        if (base.startsWith("https://github.com/") && base.contains("/tree/")) {
            throw new IOException("La URL de GitHub apunta a un directorio (tree). Proporciona el archivo concreto (.ttl/.rdf/.owl/.jsonld).");
        }

        String ct = headContentType(base);
        if (ct != null && ct.toLowerCase().contains("text/html")) {
            String prefix = base.endsWith("/") ? base : (base + "/");
            String[] candidates = new String[] {
                    prefix + "ontology.ttl",
                    prefix + "ontology.rdf",
                    prefix + "ontology.xml",
                    prefix + "ontology.owl",
                    prefix + "ontology.jsonld",
                    prefix + "ontology.nt",
                    prefix + "ontology.n3"
            };
            for (int i = 0; i < candidates.length; i++) {
                String cand = candidates[i];
                String cct = headContentType(cand);
                if (cct == null) continue;
                String lc = cct.toLowerCase();

                if (lc.contains("text/turtle") ||
                        lc.contains("application/rdf+xml") ||
                        lc.contains("application/ld+json") ||
                        lc.contains("application/n-triples") ||
                        lc.contains("application/xml") ||
                        lc.contains("text/plain")) {
                    return cand;
                }
            }
            throw new IOException("La URL devolvió HTML (documentación). Usa una serialización (p. ej. ontology.xml/ttl/jsonld/nt).");
        }

        return base;
    }

    private String headContentType(String url) {
        java.net.URLConnection c = null;
        try {
            java.net.URL u = new java.net.URL(url);
            c = u.openConnection();

            c.setRequestProperty("Accept", "text/turtle, application/rdf+xml, application/ld+json, application/n-triples, application/xml, text/plain");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            return c.getContentType(); // puede ser null
        } catch (Exception e) {
            return null;
        }
    }

    private String inferJenaLangByExtension(String url) {
        String u = url.toLowerCase();
        if (u.endsWith(".ttl"))     return "TURTLE";
        if (u.endsWith(".rdf"))     return "RDF/XML";
        if (u.endsWith(".owl"))     return "RDF/XML";
        if (u.endsWith(".xml"))     return "RDF/XML";
        if (u.endsWith(".n3"))      return "N3";
        if (u.endsWith(".nt"))      return "N-TRIPLE";
        return "RDF/XML";
    }

    private String chooseOutputFormat(Properties cfg) {
        String fmt = (cfg != null) ? cfg.getProperty("OUTPUT_FORMAT", "N3") : "N3";
        fmt = fmt.toUpperCase();
        if ("TTL".equals(fmt) || "TURTLE".equals(fmt)) return "TURTLE";
        if ("RDFXML".equals(fmt) || "RDF/XML".equals(fmt) || "OWL".equals(fmt) || "XML".equals(fmt)) return "RDF/XML";
        if ("NTRIPLES".equals(fmt) || "N-TRIPLES".equals(fmt) || "N-TRIPLE".equals(fmt) || "NT".equals(fmt)) return "N-TRIPLE";
        return "N3";
    }

    private String fileExtensionForFormat(String jenaFormat) {
        if ("TURTLE".equals(jenaFormat))    return "ttl";
        if ("RDF/XML".equals(jenaFormat))   return "rdf";
        if ("N-TRIPLE".equals(jenaFormat))  return "nt";
        return "n3";
    }

}
