#!/bin/bash
createQuery () {
    sed "s\\LOV_DATASET_URI\\${LOV_DATASET_URI}\\" ${1} > ${2}
}

LOV_DATASET_URI="$(grep "LOV_DATASET_URI" ./lov.config | cut -d'=' -f2)"

createQuery "./src/main/resources/queries/dynamic/agent-altUris.sparql" "./src/main/resources/queries/rdf2es/agent-altUris.sparql"
createQuery "./src/main/resources/queries/dynamic/agent-name.sparql" "./src/main/resources/queries/rdf2es/agent-name.sparql"
createQuery "./src/main/resources/queries/dynamic/describe-lov-vocab-descs.sparql" "./src/main/resources/queries/rdf2es/describe-lov-vocab-descs.sparql"
createQuery "./src/main/resources/queries/dynamic/describe-lov-vocab-titles.sparql" "./src/main/resources/queries/rdf2es/describe-lov-vocab-titles.sparql"
createQuery "./src/main/resources/queries/dynamic/describe-lov-vocab.sparql" "./src/main/resources/queries/rdf2es/describe-lov-vocab.sparql"
createQuery "./src/main/resources/queries/dynamic/list-agent-related-vocab.sparql" "./src/main/resources/queries/rdf2es/list-agent-related-vocab.sparql"
createQuery "./src/main/resources/queries/dynamic/list-lov-organizations.sparql" "./src/main/resources/queries/rdf2es/list-lov-organizations.sparql"
createQuery "./src/main/resources/queries/dynamic/list-lov-persons.sparql" "./src/main/resources/queries/rdf2es/list-lov-persons.sparql"
createQuery "./src/main/resources/queries/dynamic/list-lov-swagents.sparql" "./src/main/resources/queries/rdf2es/list-lov-swagents.sparql"
createQuery "./src/main/resources/queries/dynamic/list-lov-vocabularies.sparql" "./src/main/resources/queries/rdf2es/list-lov-vocabularies.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-term-metrics.sparql" "./src/main/resources/queries/rdf2es/lov-term-metrics.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-term-tags.sparql" "./src/main/resources/queries/rdf2es/lov-term-tags.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-vocabulary-languages.sparql" "./src/main/resources/queries/rdf2es/lov-vocabulary-languages.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-vocabulary-terms.sparql" "./src/main/resources/queries/rdf2es/lov-vocabulary-terms.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-term-metrics-2.sparql" "./src/main/resources/queries/lov-term-metrics.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-vocabulary-terms-2.sparql" "./src/main/resources/queries/lov-vocabulary-terms.sparql"
