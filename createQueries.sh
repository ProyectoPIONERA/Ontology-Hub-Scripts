#!/bin/sh
set -e -u

# Lee la URI desde lov.config, elimina CR/LF y TRIMEA espacios al inicio/fin
read_config_value() {
  key="$1"
  val=$(awk -F'=' -v k="$key" '
    $1 ~ ("^" k "$") && $0 !~ /^[[:space:]]*#/ {
      $1="";
      sub(/^[[:space:]]*=/,"");         # quita espacios antes del '='
      print; exit
    }
  ' ./lov.config | tr -d '\r\n')
  # trim leading/trailing whitespace
  # (POSIX: usamos sed para quitar espacios alrededor)
  echo "$val" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

# Escapa & y \ para uso en sed
escape_for_sed_replacement() {
  sed -e 's/\\/\\\\/g' -e 's/&/\\&/g'
}

# Reemplazo robusto:
#  - normaliza CRLF a LF
#  - intenta primero reemplazar "<LOV_DATASET_URI>" por "<URI>"
#  - si no existe el patrón con corchetes, reemplaza "LOV_DATASET_URI" por "URI" a secas
#  - imprime DEBUG
createQuery() {
    src="$1"
    dst="$2"

    tmp_src="$(mktemp)"
    tr -d '\r' < "$src" > "$tmp_src"

    # Valor limpio (trim) y escapado para sed
    raw_uri="$LOV_DATASET_URI"
    trimmed_uri=$(printf '%s' "$raw_uri" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')
    escaped_uri="$(printf '%s' "$trimmed_uri" | escape_for_sed_replacement)"

    tmp_out="$(mktemp)"
    # Intento 1: reemplazar con corchetes exactamente
    if grep -q '<LOV_DATASET_URI>' "$tmp_src"; then
      sed "s|<LOV_DATASET_URI>|<$escaped_uri>|g" "$tmp_src" > "$tmp_out"
    else
      # Intento 2: reemplazar token sin corchetes
      sed "s|LOV_DATASET_URI|$escaped_uri|g" "$tmp_src" > "$tmp_out"
    fi

    # Fuerza LF en salida
    tr -d '\r' < "$tmp_out" > "$dst"
    rm -f "$tmp_src" "$tmp_out"

    # DEBUG
    echo "===== DEBUG: Generado $dst ====="
    echo "URI cruda:      [$raw_uri]"
    echo "URI con trim:   [$trimmed_uri]"
    echo "----- Contenido (primeras 120 líneas) -----"
    nl -ba "$dst" | sed -n '1,120p'
    echo "-------------------------------------------"
    if grep -n '<<' "$dst" >/dev/null 2>&1; then
      echo "⚠️  Atención: encontradas apariciones de '<<' en $dst:"
      grep -n '<<' "$dst" || true
    else
      echo "OK: no hay '<<' en $dst"
    fi
    # Verifica si quedó espacio después de '<'
    if grep -n '< ' "$dst" >/dev/null 2>&1; then
      echo "⚠️  Atención: se detectó '< ' (espacio tras '<') en $dst:"
      grep -n '< ' "$dst" || true
    else
      echo "OK: no hay espacio tras '<' en $dst"
    fi
    echo "==========================================="
}

# ---------- MAIN ----------
# Normaliza lov.config a LF
if [ -f ./lov.config ]; then
  if sed -i 's/\r$//' ./lov.config 2>/dev/null; then :; else
    tmp_cfg="$(mktemp)"
    tr -d '\r' < ./lov.config > "$tmp_cfg"
    mv "$tmp_cfg" ./lov.config
  fi
fi

LOV_DATASET_URI="$(read_config_value 'LOV_DATASET_URI')"

if [ -z "$LOV_DATASET_URI" ]; then
  echo "Error: LOV_DATASET_URI no encontrado o vacío en ./lov.config" >&2
  exit 1
fi

# Genera todas las queries
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
createQuery "./src/main/resources/queries/dynamic/list-individuals.sparql" "./src/main/resources/queries/rdf2es/list-individuals.sparql"
createQuery "./src/main/resources/queries/dynamic/list-types-of-individual.sparql" "./src/main/resources/queries/rdf2es/list-types-of-individual.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-vocabulary-languages.sparql" "./src/main/resources/queries/rdf2es/lov-vocabulary-languages.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-vocabulary-terms.sparql" "./src/main/resources/queries/rdf2es/lov-vocabulary-terms.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-term-metrics-2.sparql" "./src/main/resources/queries/lov-term-metrics.sparql"
createQuery "./src/main/resources/queries/dynamic/lov-vocabulary-terms-2.sparql" "./src/main/resources/queries/lov-vocabulary-terms.sparql"

createQuery "./src/main/resources/queries/list-classes.sparql" "./src/main/resources/queries/rdf2es/list-classes.sparql"
createQuery "./src/main/resources/queries/list-properties.sparql" "./src/main/resources/queries/rdf2es/list-properties.sparql"
createQuery "./src/main/resources/queries/list-datatypes.sparql" "./src/main/resources/queries/rdf2es/list-datatypes.sparql"
createQuery "./src/main/resources/queries/list-instances.sparql" "./src/main/resources/queries/rdf2es/list-instances.sparql"
createQuery "./src/main/resources/queries/term-label.sparql" "./src/main/resources/queries/rdf2es/term-label.sparql"
createQuery "./src/main/resources/queries/term-labels.sparql" "./src/main/resources/queries/rdf2es/term-labels.sparql"