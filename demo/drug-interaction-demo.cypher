// ══════════════════════════════════════════════════════════════
// n20s Demo: Drug Interaction Safety Check
//
// LPG graph + RDF knowledge → scoped reasoning → interaction detection
// ══════════════════════════════════════════════════════════════

// ── Step 0: Clean slate ──────────────────────────────────────

MATCH (n) DETACH DELETE n;

// ── Step 1: Create the LPG graph (patients, drugs, conditions) ──

// Drugs
CREATE (warfarin:Drug {name: 'Warfarin', brand: 'Coumadin'})
CREATE (aspirin:Drug {name: 'Aspirin', brand: 'Bayer'})
CREATE (ibuprofen:Drug {name: 'Ibuprofen', brand: 'Advil'})
CREATE (metformin:Drug {name: 'Metformin', brand: 'Glucophage'})
CREATE (lisinopril:Drug {name: 'Lisinopril', brand: 'Zestril'})
CREATE (omeprazole:Drug {name: 'Omeprazole', brand: 'Prilosec'})
CREATE (simvastatin:Drug {name: 'Simvastatin', brand: 'Zocor'})
CREATE (clopidogrel:Drug {name: 'Clopidogrel', brand: 'Plavix'})
CREATE (fluoxetine:Drug {name: 'Fluoxetine', brand: 'Prozac'})
CREATE (amiodarone:Drug {name: 'Amiodarone', brand: 'Cordarone'})

// Conditions
CREATE (afib:Condition {name: 'Atrial Fibrillation'})
CREATE (diabetes:Condition {name: 'Type 2 Diabetes'})
CREATE (hypertension:Condition {name: 'Hypertension'})
CREATE (arthritis:Condition {name: 'Osteoarthritis'})
CREATE (depression:Condition {name: 'Major Depression'})
CREATE (gerd:Condition {name: 'GERD'})
CREATE (hyperlipidemia:Condition {name: 'Hyperlipidemia'})
CREATE (cad:Condition {name: 'Coronary Artery Disease'})

// Patients
CREATE (alice:Patient {name: 'Alice', age: 72})
CREATE (bob:Patient {name: 'Bob', age: 65})
CREATE (carol:Patient {name: 'Carol', age: 58})
CREATE (dave:Patient {name: 'Dave', age: 80})

// Prescriptions
CREATE (alice)-[:PRESCRIBED {since: date('2024-01-15')}]->(warfarin)
CREATE (alice)-[:PRESCRIBED {since: date('2024-06-01')}]->(aspirin)
CREATE (alice)-[:PRESCRIBED {since: date('2023-03-10')}]->(lisinopril)

CREATE (bob)-[:PRESCRIBED {since: date('2023-05-20')}]->(metformin)
CREATE (bob)-[:PRESCRIBED {since: date('2024-02-14')}]->(simvastatin)
CREATE (bob)-[:PRESCRIBED {since: date('2024-08-01')}]->(ibuprofen)

CREATE (carol)-[:PRESCRIBED {since: date('2024-01-01')}]->(clopidogrel)
CREATE (carol)-[:PRESCRIBED {since: date('2024-03-15')}]->(omeprazole)
CREATE (carol)-[:PRESCRIBED {since: date('2024-07-01')}]->(fluoxetine)

CREATE (dave)-[:PRESCRIBED {since: date('2023-01-01')}]->(warfarin)
CREATE (dave)-[:PRESCRIBED {since: date('2024-04-01')}]->(amiodarone)
CREATE (dave)-[:PRESCRIBED {since: date('2024-06-15')}]->(simvastatin)

// Conditions
CREATE (alice)-[:HAS_CONDITION]->(afib)
CREATE (alice)-[:HAS_CONDITION]->(hypertension)

CREATE (bob)-[:HAS_CONDITION]->(diabetes)
CREATE (bob)-[:HAS_CONDITION]->(hyperlipidemia)
CREATE (bob)-[:HAS_CONDITION]->(arthritis)

CREATE (carol)-[:HAS_CONDITION]->(cad)
CREATE (carol)-[:HAS_CONDITION]->(gerd)
CREATE (carol)-[:HAS_CONDITION]->(depression)

CREATE (dave)-[:HAS_CONDITION]->(afib)
CREATE (dave)-[:HAS_CONDITION]->(hyperlipidemia);

// ── Step 2: Attach RDF triples to drug nodes ─────────────────
//
// The LPG graph knows WHO takes WHAT.
// The RDF triples know WHY things interact.

// Namespace shortcuts for readability
// drug: = http://example.org/pharma#
// rdf:type = http://www.w3.org/1999/02/22-rdf-syntax-ns#type
// rdfs:subClassOf = http://www.w3.org/2000/01/rdf-schema#subClassOf

// Drug classification triples
MATCH (d:Drug {name: 'Warfarin'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Warfarin', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#Anticoagulant'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Warfarin', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#VitaminKAntagonist'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Warfarin', p: 'http://example.org/pharma#metabolizedBy', o: 'http://example.org/pharma#CYP2C9'});

MATCH (d:Drug {name: 'Aspirin'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Aspirin', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#NSAID'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Aspirin', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#PlateletInhibitor'});

MATCH (d:Drug {name: 'Ibuprofen'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Ibuprofen', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#NSAID'});

MATCH (d:Drug {name: 'Metformin'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Metformin', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#Antidiabetic'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Metformin', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#BiguanideAgent'});

MATCH (d:Drug {name: 'Lisinopril'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Lisinopril', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#ACEInhibitor'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Lisinopril', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#Antihypertensive'});

MATCH (d:Drug {name: 'Omeprazole'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Omeprazole', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#ProtonPumpInhibitor'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Omeprazole', p: 'http://example.org/pharma#inhibits', o: 'http://example.org/pharma#CYP2C19'});

MATCH (d:Drug {name: 'Simvastatin'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Simvastatin', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#Statin'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Simvastatin', p: 'http://example.org/pharma#metabolizedBy', o: 'http://example.org/pharma#CYP3A4'});

MATCH (d:Drug {name: 'Clopidogrel'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Clopidogrel', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#PlateletInhibitor'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Clopidogrel', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#Thienopyridine'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Clopidogrel', p: 'http://example.org/pharma#activatedBy', o: 'http://example.org/pharma#CYP2C19'});

MATCH (d:Drug {name: 'Fluoxetine'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Fluoxetine', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#SSRI'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Fluoxetine', p: 'http://example.org/pharma#inhibits', o: 'http://example.org/pharma#CYP2C19'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Fluoxetine', p: 'http://example.org/pharma#inhibits', o: 'http://example.org/pharma#CYP2D6'});

MATCH (d:Drug {name: 'Amiodarone'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Amiodarone', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#Antiarrhythmic'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Amiodarone', p: 'http://example.org/pharma#inhibits', o: 'http://example.org/pharma#CYP2C9'})
CREATE (d)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/pharma#Amiodarone', p: 'http://example.org/pharma#inhibits', o: 'http://example.org/pharma#CYP3A4'});

// ── Step 3: Create shared ontology triples ───────────────────
// These are the "rules" — class hierarchy + interaction patterns
// Stored as standalone Triple nodes (shared knowledge, not drug-specific)

CREATE (:Triple:Ontology {s: 'http://example.org/pharma#NSAID', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#AntiInflammatory'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#Anticoagulant', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#BloodThinner'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#PlateletInhibitor', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#BloodThinner'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#VitaminKAntagonist', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#Anticoagulant'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#Thienopyridine', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#PlateletInhibitor'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#ACEInhibitor', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#Antihypertensive'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#Statin', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#LipidLoweringAgent'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#SSRI', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#Antidepressant'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#BiguanideAgent', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#Antidiabetic'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#ProtonPumpInhibitor', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/pharma#AcidReducer'})

// Interaction rules — class-level
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#NSAID', p: 'http://example.org/pharma#interactsWith', o: 'http://example.org/pharma#Anticoagulant'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#NSAID', p: 'http://example.org/pharma#interactionSeverity', o: '"high"'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#NSAID', p: 'http://example.org/pharma#interactionRisk', o: '"Increased bleeding risk"'})

CREATE (:Triple:Ontology {s: 'http://example.org/pharma#PlateletInhibitor', p: 'http://example.org/pharma#interactsWith', o: 'http://example.org/pharma#Anticoagulant'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#PlateletInhibitor', p: 'http://example.org/pharma#interactionSeverity', o: '"high"'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#PlateletInhibitor', p: 'http://example.org/pharma#interactionRisk', o: '"Additive bleeding risk"'})

// CYP enzyme interaction rules
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#CYP2C19', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#CytochromeP450'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#CYP2C9', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#CytochromeP450'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#CYP3A4', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#CytochromeP450'})
CREATE (:Triple:Ontology {s: 'http://example.org/pharma#CYP2D6', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/pharma#CytochromeP450'});


// ══════════════════════════════════════════════════════════════
// ── DEMO QUERIES ─────────────────────────────────────────────
// ══════════════════════════════════════════════════════════════

// ── Demo 1: Check Alice's prescriptions ──────────────────────

MATCH (p:Patient {name: 'Alice'})-[:PRESCRIBED]->(d:Drug)
RETURN p.name AS patient, collect(d.name) AS drugs;

// ── Demo 2: Scope + Project Alice's drug knowledge ───────────
//
// 1. Traverse LPG to find Alice's drugs
// 2. Collect drug-specific triples via HAS_TRIPLE
// 3. Add ontology triples (shared rules)
// 4. Project into n20s in-memory graph

CALL {
  MATCH (:Patient {name: 'Alice'})-[:PRESCRIBED]->(:Drug)-[:HAS_TRIPLE]->(t:Triple)
  RETURN t.s AS s, t.p AS p, t.o AS o
  UNION
  MATCH (t:Triple:Ontology)
  RETURN t.s AS s, t.p AS p, t.o AS o
}
WITH n20s.graph.project('alice_check', s, p, o) AS g
RETURN g.graphName AS graph, g.tripleCount AS triples;

// ── Demo 3: Run RDFS inference ───────────────────────────────
//
// Warfarin is a VitaminKAntagonist → subClassOf Anticoagulant → subClassOf BloodThinner
// Aspirin is an NSAID, and also a PlateletInhibitor → subClassOf BloodThinner

CALL n20s.graph.infer('alice_check', 'RDFS')
YIELD triplesBefore, triplesAfter, newTriples
RETURN triplesBefore, triplesAfter, newTriples;

// ── Demo 4: Detect interactions via SPARQL ───────────────────
//
// "Find pairs of Alice's drugs where one's class interacts with the other's class"

CALL n20s.graph.query('alice_check', '
  PREFIX pharma: <http://example.org/pharma#>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

  SELECT ?drug1 ?drug2 ?class1 ?class2 ?risk ?severity WHERE {
    ?drug1 rdf:type ?class1 .
    ?drug2 rdf:type ?class2 .
    ?class1 pharma:interactsWith ?class2 .
    ?class1 pharma:interactionRisk ?risk .
    ?class1 pharma:interactionSeverity ?severity .
    FILTER(?drug1 != ?drug2)
  }
') YIELD row
RETURN row;

// ── Demo 5: CYP enzyme conflicts ─────────────────────────────
//
// "Find drugs where one inhibits the enzyme that metabolizes the other"

CALL n20s.graph.query('alice_check', '
  PREFIX pharma: <http://example.org/pharma#>

  SELECT ?inhibitor ?enzyme ?affected WHERE {
    ?inhibitor pharma:inhibits ?enzyme .
    ?affected pharma:metabolizedBy ?enzyme .
    ?enzyme a pharma:CytochromeP450 .
    FILTER(?inhibitor != ?affected)
  }
') YIELD row
RETURN row;

// ── Demo 6: Cleanup ──────────────────────────────────────────

CALL n20s.graph.drop('alice_check');

// ══════════════════════════════════════════════════════════════
// ── Now try other patients! ──────────────────────────────────
// ══════════════════════════════════════════════════════════════

// Carol: Clopidogrel + Omeprazole + Fluoxetine
//   → Clopidogrel is activated by CYP2C19
//   → Omeprazole inhibits CYP2C19
//   → Fluoxetine inhibits CYP2C19
//   → DOUBLE CYP2C19 inhibition → Clopidogrel won't activate!

// Dave: Warfarin + Amiodarone + Simvastatin
//   → Amiodarone inhibits CYP2C9 (Warfarin's metabolizer) → bleeding risk
//   → Amiodarone inhibits CYP3A4 (Simvastatin's metabolizer) → muscle damage risk
