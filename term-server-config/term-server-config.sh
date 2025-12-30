#!/bin/bash

# ==========================================
# CONFIGURATION
# ==========================================
FHIR_BASE="http://localhost:8080/fhir"
RESOURCES_DIR="../src/main/resources/terminology-mapping-resources"

# Canonical URLs based on your POC requirements.
# Ensure these match the "url" field inside your JSON files exactly.
URL_INTERNAL_SYSTEM="http://my-hospital.com/internal-lab-codes"
URL_LOINC_FRAGMENT="http://loinc.org"
URL_CONCEPT_MAP="http://my-hospital.com/fhir/ConceptMap/internal-hospital-2-loinc-fragment"

# ==========================================
# FUNCTIONS
# ==========================================

# Function 1: Wait for server to be up
wait_for_server() {
    echo "Checking server status..."
    until curl -s -f -o /dev/null "$FHIR_BASE/metadata"; do
        echo "Waiting for HAPI FHIR server to respond at $FHIR_BASE..."
        sleep 5
    done
    echo "Server is online!"
    echo "------------------------------------------"
}

# Function 2: Load resource only if missing
# Arguments: $1=ResourceType, $2=CanonicalUrl, $3=FileName
load_if_missing() {
    local resourceType=$1
    local canonicalUrl=$2
    local fileName=$3
    
    echo "Checking $resourceType: $canonicalUrl"

    # FHIR search by url.
    # HAPI responds with a Bundle. We check for '"total": 0'.
    search_response=$(curl -s "$FHIR_BASE/$resourceType?url=$canonicalUrl")
    
    if echo "$search_response" | grep -q '"total": 0'; then
        echo "   -> Not found. Creating resource from $fileName..."
        
        http_code=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST \
            -H "Content-Type: application/json" \
            -d @$RESOURCES_DIR/$fileName \
            "$FHIR_BASE/$resourceType")
            
        if [ "$http_code" -eq 201 ]; then
            echo "   -> [OK] Successfully created (HTTP 201)."
        else
            echo "   -> [ERROR] Creation failed. HTTP Code: $http_code"
        fi
    else
        echo "   -> Already exists. Skipping load."
    fi
    echo "------------------------------------------"
}

# ==========================================
# EXECUTION
# ==========================================

echo "=========================================="
echo " STARTING TERMINOLOGY CONFIGURATION"
echo "=========================================="

# 1. Verify Server Health
wait_for_server

# 2. Load CodeSystem: Hospital Labs
load_if_missing "CodeSystem" \
                "$URL_INTERNAL_SYSTEM" \
                "codesystem-internal-hospital-labs.json"

# 3. Load CodeSystem: LOINC Fragment
load_if_missing "CodeSystem" \
                "$URL_LOINC_FRAGMENT" \
                "codesystem-loinc-fragment-labs.json"

# 4. Load ConceptMap
load_if_missing "ConceptMap" \
                "$URL_CONCEPT_MAP" \
                "conceptmap-internal-hospital-2-loinc-fragment.json"

echo "=========================================="
echo " CONFIGURATION PROCESS COMPLETED"
echo "=========================================="
