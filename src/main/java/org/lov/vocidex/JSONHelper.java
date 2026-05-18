package org.lov.vocidex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.lov.vocidex.describers.DatatypeIdentifier;
import org.lov.vocidex.describers.LabelDescriber;

import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.FileUtils;

public class JSONHelper {
    protected final static ObjectMapper mapper = new ObjectMapper();

    public static ObjectNode createObject() {
        return mapper.createObjectNode();
    }
    public static ArrayNode createArray() {
        return mapper.createArrayNode();
    }

    public static String asJsonString(JsonNode jsonNode) {
        try {
            return mapper.writeValueAsString(jsonNode);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Reads a JSON resource from the classpath (typically under {@code src/main/resources}).
     * Tries several class loaders because some launchers (e.g. Jena ARQ {@code CmdMain}) can leave
     * {@code VocidexIndex}'s loader unable to see resources from the application JAR.
     */
    public static String readFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new VocidexException("Resource name is empty");
        }
        String slashName = fileName.startsWith("/") ? fileName : "/" + fileName;
        String bareName = slashName.startsWith("/") ? slashName.substring(1) : slashName;

        InputStream in = JSONHelper.class.getResourceAsStream(slashName);
        if (in == null) {
            in = VocidexIndex.class.getResourceAsStream(slashName);
        }
        if (in == null) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) {
                in = tccl.getResourceAsStream(bareName);
            }
        }
        if (in == null) {
            ClassLoader cl = JSONHelper.class.getClassLoader();
            if (cl != null) {
                in = cl.getResourceAsStream(bareName);
            }
        }
        if (in == null) {
            in = ClassLoader.getSystemResourceAsStream(bareName);
        }
        if (in == null) {
            throw new VocidexException("Classpath resource not found: " + bareName
                    + " (ensure lovscripts JAR on classpath contains /mappings/*.json)");
        }
        try {
            return FileUtils.readWholeFileAsUTF8(in);
        } catch (IOException ex) {
            throw new VocidexException(ex);
        }
    }

    /** Reads UTF-8 JSON from an absolute or relative filesystem path. */
    public static String readFileFromFilesystem(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new VocidexException("Cannot read file: " + path, ex);
        }
    }
    public void putString(ObjectNode json, String key, String value) {
        if (value != null && value.length()>0) {
            json.put(key, value);
        }
    }


    public void putBoolean(ObjectNode json, String key, boolean value) {
        if (value) {
            json.put(key, value);
        }
    }

    public void putLong(ObjectNode json, String key, Long value) {
        json.put(key, value);
    }

    public void putArrayString(ObjectNode json, String key, List<String> array) {
        if (array.size() > 0) {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (int i = 0; i < array.size(); i++) {
                arrayNode.add(array.get(i));
            }
            json.put(key, arrayNode);
        }
    }
    public void putArrayString(ObjectNode json, String key, String s) {
        if (s!=null && s.length()>0) {
            ArrayNode arrayNode = mapper.createArrayNode();
            arrayNode.add(s);
            json.put(key, arrayNode);
        }
    }

    public void putURIArray(ObjectNode json, String key, ArrayNode array) {
        if (array.size() > 0) {
            json.put(key, array);
        }
    }

    public void putURIArray(ObjectNode json, String key, Collection<Resource> uris) {
        ArrayNode array = mapper.createArrayNode();
        for (Resource uri: uris) {
            array.add(uri.getURI());
        }
        if (array.size() > 0) {
            json.put(key, array);
        }
    }

    public void putURIArrayWithLabels(ObjectNode json, String key,
                                      Collection<Resource> uris, LabelDescriber labeller) {
        putURIArrayWithLabels(json, key, uris, labeller, null);
    }

    public void putURIArrayWithLabels(ObjectNode json, String key,
                                      Collection<Resource> uris, LabelDescriber labeller, DatatypeIdentifier datatypeIdentifier) {
        ArrayNode array = mapper.createArrayNode();
        for (Resource uri: uris) {
            ObjectNode o = mapper.createObjectNode();
            o.put("uri", uri.getURI());
            labeller.describe(uri, o);
            if (datatypeIdentifier != null) {
                if (datatypeIdentifier.isDatatype(uri)) {
                    o.put("isDatatype", true);
                } else {
                    o.put("isClass", true);
                }
            }
            array.add(o);
        }
        if (array.size() > 0) {
            json.put(key, array);
        }
    }
    public void putVocabURIPrefix(ObjectNode json, String key,
                                  List<String[]> vocabs) {
        ArrayNode array = mapper.createArrayNode();
        for (String[] vocab: vocabs) {
            ObjectNode o = mapper.createObjectNode();
            o.put("uri", vocab[0]);
            o.put("prefix", vocab[1]);
            array.add(o);
        }
        if (array.size() > 0) {
            json.put(key, array);
        }
    }
    public void putTagLabelOcc(ObjectNode json, String key, Map<String,Integer> tags) {
        ArrayNode array = mapper.createArrayNode();
        for (String tagLabel: tags.keySet()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("label", tagLabel);
            o.put("occurrences", tags.get(tagLabel).intValue());
            array.add(o);
        }
        if (array.size() > 0) {
            json.put(key, array);
        }
    }
}
