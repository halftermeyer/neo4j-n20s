// ══════════════════════════════════════════════════════════════
// n20s Demo: Cosmetics Regulation Impact Analysis
//
// "EU restricts Retinol above 0.05% — what's the blast radius
//  across our product portfolio, and what can we substitute?"
//
// LPG graph: multi-level formulation BOM with concentration ratios
// RDF knowledge: regulatory ontology + ingredient interactions
// n20s: scoped compliance reasoning + substitution validation
// ══════════════════════════════════════════════════════════════

// ── Step 0: Clean slate ──────────────────────────────────────

MATCH (n) DETACH DELETE n;

// ── Step 1: Create the entire LPG graph + RDF triples ────────
// One single statement so all variables are in scope

CREATE

// Markets
(eu:Market {name: 'EU', regulation: 'EC 1223/2009'}),
(us:Market {name: 'US', regulation: 'FDA CFR Title 21'}),
(cn:Market {name: 'China', regulation: 'NMPA Cosmetics Regulation'}),
(jp:Market {name: 'Japan', regulation: 'MHLW Standards'}),

// Brands
(luxe:Brand {name: 'Luxe Beauté'}),
(natura:Brand {name: 'Natura Lab'}),
(derma:Brand {name: 'DermaScience'}),

// Suppliers
(supA:Supplier {name: 'ChemSource Asia', country: 'China'}),
(supB:Supplier {name: 'EuroActives GmbH', country: 'Germany'}),
(supC:Supplier {name: 'BioRetinol Inc', country: 'US'}),
(supD:Supplier {name: 'PlantExtract France', country: 'France'}),

// Ingredients
(retinol:Ingredient {name: 'Retinol', cas: '68-26-8', inci: 'RETINOL'}),
(retinal:Ingredient {name: 'Retinal', cas: '116-31-4', inci: 'RETINAL'}),
(bakuchiol:Ingredient {name: 'Bakuchiol', cas: '10309-37-2', inci: 'BAKUCHIOL'}),
(niacinamide:Ingredient {name: 'Niacinamide', cas: '98-92-0', inci: 'NIACINAMIDE'}),
(ha:Ingredient {name: 'Hyaluronic Acid', cas: '9004-61-9', inci: 'SODIUM HYALURONATE'}),
(squalane:Ingredient {name: 'Squalane', cas: '111-01-3', inci: 'SQUALANE'}),
(vitc:Ingredient {name: 'Ascorbic Acid', cas: '50-81-7', inci: 'ASCORBIC ACID'}),
(glycolicAcid:Ingredient {name: 'Glycolic Acid', cas: '79-14-1', inci: 'GLYCOLIC ACID'}),
(salicylicAcid:Ingredient {name: 'Salicylic Acid', cas: '69-72-7', inci: 'SALICYLIC ACID'}),
(tocopherol:Ingredient {name: 'Tocopherol', cas: '59-02-9', inci: 'TOCOPHEROL'}),
(caprylic:Ingredient {name: 'Caprylic/Capric Triglyceride', cas: '65381-09-1', inci: 'CAPRYLIC/CAPRIC TRIGLYCERIDE'}),
(water:Ingredient {name: 'Water', cas: '7732-18-5', inci: 'AQUA'}),
(phenoxyethanol:Ingredient {name: 'Phenoxyethanol', cas: '122-99-6', inci: 'PHENOXYETHANOL'}),

// Substitutions
(bakuchiol)-[:SUBSTITUTE_FOR {reason: 'Plant-based retinoid alternative'}]->(retinol),
(retinal)-[:SUBSTITUTE_FOR {reason: 'Aldehyde form, fewer side effects'}]->(retinol),

// Supply chain
(supA)-[:SUPPLIES {leadTime: 45}]->(retinol),
(supC)-[:SUPPLIES {leadTime: 21}]->(retinol),
(supB)-[:SUPPLIES {leadTime: 14}]->(retinal),
(supD)-[:SUPPLIES {leadTime: 7}]->(bakuchiol),
(supA)-[:SUPPLIES]->(niacinamide),
(supB)-[:SUPPLIES]->(vitc),
(supB)-[:SUPPLIES]->(glycolicAcid),

// ── Product 1: Luxe Anti-Aging Serum (Retinol 0.1%) ─────────

(p1:Product {name: 'Anti-Aging Serum', sku: 'LXB-001'}),
(p1_phA:Phase {name: 'Water Phase'}),
(p1_phB:Phase {name: 'Oil Phase'}),
(p1_phC:Phase {name: 'Active Phase'}),
(p1_premix:PreMix {name: 'Retinol Pre-Mix'}),

(luxe)-[:PRODUCES]->(p1),
(p1)-[:SELLS_IN]->(eu),
(p1)-[:SELLS_IN]->(us),
(p1)-[:IN_LINE]->(:ProductLine {name: 'Anti-Aging'}),

(p1)-[:CONTAINS {ratio: 0.70}]->(p1_phA),
(p1)-[:CONTAINS {ratio: 0.25}]->(p1_phB),
(p1)-[:CONTAINS {ratio: 0.05}]->(p1_phC),

(p1_phA)-[:CONTAINS {ratio: 0.95}]->(water),
(p1_phA)-[:CONTAINS {ratio: 0.04}]->(niacinamide),
(p1_phA)-[:CONTAINS {ratio: 0.01}]->(phenoxyethanol),

(p1_phB)-[:CONTAINS {ratio: 0.20}]->(p1_premix),
(p1_phB)-[:CONTAINS {ratio: 0.80}]->(squalane),

(p1_premix)-[:CONTAINS {ratio: 0.02}]->(retinol),
(p1_premix)-[:CONTAINS {ratio: 0.98}]->(caprylic),

(p1_phC)-[:CONTAINS {ratio: 1.0}]->(ha),

// ── Product 2: Luxe Night Cream (Retinol 0.3%) ──────────────

(p2:Product {name: 'Night Cream', sku: 'LXB-002'}),
(p2_phA:Phase {name: 'Water Phase'}),
(p2_phB:Phase {name: 'Oil Phase'}),
(p2_premix:PreMix {name: 'Retinol Concentrate'}),

(luxe)-[:PRODUCES]->(p2),
(p2)-[:SELLS_IN]->(eu),
(p2)-[:SELLS_IN]->(jp),
(p2)-[:IN_LINE]->(:ProductLine {name: 'Anti-Aging'}),

(p2)-[:CONTAINS {ratio: 0.60}]->(p2_phA),
(p2)-[:CONTAINS {ratio: 0.40}]->(p2_phB),

(p2_phA)-[:CONTAINS {ratio: 0.97}]->(water),
(p2_phA)-[:CONTAINS {ratio: 0.03}]->(phenoxyethanol),

(p2_phB)-[:CONTAINS {ratio: 0.15}]->(p2_premix),
(p2_phB)-[:CONTAINS {ratio: 0.85}]->(squalane),

(p2_premix)-[:CONTAINS {ratio: 0.05}]->(retinol),
(p2_premix)-[:CONTAINS {ratio: 0.95}]->(caprylic),

// ── Product 3: DermaScience Peel (no retinol) ────────────────

(p3:Product {name: 'Renewal Peel', sku: 'DS-010'}),
(derma)-[:PRODUCES]->(p3),
(p3)-[:SELLS_IN]->(eu),
(p3)-[:SELLS_IN]->(us),

(p3)-[:CONTAINS {ratio: 0.90}]->(water),
(p3)-[:CONTAINS {ratio: 0.08}]->(glycolicAcid),
(p3)-[:CONTAINS {ratio: 0.02}]->(phenoxyethanol),

// ── Product 4: Natura Brightening Serum (Vit C + Niacinamide) ─

(p4:Product {name: 'Brightening Serum', sku: 'NL-005'}),
(natura)-[:PRODUCES]->(p4),
(p4)-[:SELLS_IN]->(eu),
(p4)-[:SELLS_IN]->(cn),

(p4)-[:CONTAINS {ratio: 0.85}]->(water),
(p4)-[:CONTAINS {ratio: 0.10}]->(vitc),
(p4)-[:CONTAINS {ratio: 0.04}]->(niacinamide),
(p4)-[:CONTAINS {ratio: 0.01}]->(phenoxyethanol),

// ── Product 5: Natura Retinol Booster (Retinol + Vit C) ──────

(p5:Product {name: 'Retinol Booster', sku: 'NL-008'}),
(p5_phA:Phase {name: 'Aqueous'}),
(p5_phB:Phase {name: 'Active Oil'}),

(natura)-[:PRODUCES]->(p5),
(p5)-[:SELLS_IN]->(eu),
(p5)-[:SELLS_IN]->(us),

(p5)-[:CONTAINS {ratio: 0.70}]->(p5_phA),
(p5)-[:CONTAINS {ratio: 0.30}]->(p5_phB),

(p5_phA)-[:CONTAINS {ratio: 0.90}]->(water),
(p5_phA)-[:CONTAINS {ratio: 0.10}]->(vitc),

(p5_phB)-[:CONTAINS {ratio: 0.03}]->(retinol),
(p5_phB)-[:CONTAINS {ratio: 0.05}]->(tocopherol),
(p5_phB)-[:CONTAINS {ratio: 0.92}]->(squalane),

// ── RDF triples: ingredient classifications ──────────────────

(retinol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Retinol', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#RetinoidAgent'}),
(retinol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Retinol', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#PhotosensitiveAgent'}),
(retinol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Retinol', p: 'http://example.org/cosmo#maxConcentrationEU', o: '"0.05"'}),
(retinol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Retinol', p: 'http://example.org/cosmo#regulatoryRef', o: '"EC 1223/2009 Annex III/98a"'}),

(retinal)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Retinal', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#RetinoidAgent'}),
(retinal)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Retinal', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#PhotosensitiveAgent'}),
(retinal)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Retinal', p: 'http://example.org/cosmo#maxConcentrationEU', o: '"0.01"'}),

(bakuchiol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Bakuchiol', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#RetinoidAlternative'}),
(bakuchiol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Bakuchiol', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#PlantExtract'}),
(bakuchiol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Bakuchiol', p: 'http://example.org/cosmo#functionallyEquivalentTo', o: 'http://example.org/cosmo#RetinoidAgent'}),

(vitc)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#AscorbicAcid', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#Antioxidant'}),
(vitc)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#AscorbicAcid', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#AcidActiveAgent'}),
(vitc)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#AscorbicAcid', p: 'http://example.org/cosmo#optimalPH', o: '"3.5"'}),

(glycolicAcid)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#GlycolicAcid', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#AHAExfoliant'}),
(glycolicAcid)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#GlycolicAcid', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#AcidActiveAgent'}),
(glycolicAcid)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#GlycolicAcid', p: 'http://example.org/cosmo#maxConcentrationEU', o: '"0.10"'}),

(salicylicAcid)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#SalicylicAcid', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#BHAExfoliant'}),
(salicylicAcid)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#SalicylicAcid', p: 'http://example.org/cosmo#maxConcentrationEU', o: '"0.02"'}),

(niacinamide)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Niacinamide', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#VitaminDerivative'}),
(niacinamide)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Niacinamide', p: 'http://example.org/cosmo#optimalPH', o: '"6.0"'}),

(phenoxyethanol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Phenoxyethanol', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#Preservative'}),
(phenoxyethanol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Phenoxyethanol', p: 'http://example.org/cosmo#maxConcentrationEU', o: '"0.01"'}),

(tocopherol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Tocopherol', p: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type', o: 'http://example.org/cosmo#Antioxidant'}),
(tocopherol)-[:HAS_TRIPLE]->(:Triple {s: 'http://example.org/cosmo#Tocopherol', p: 'http://example.org/cosmo#stabilizes', o: 'http://example.org/cosmo#RetinoidAgent'}),

// ── RDF triples: ontology (class hierarchy + rules) ──────────

(:Triple:Ontology {s: 'http://example.org/cosmo#RetinoidAgent', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#ActiveIngredient'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#RetinoidAlternative', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#ActiveIngredient'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#Antioxidant', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#ActiveIngredient'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#AHAExfoliant', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#Exfoliant'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#BHAExfoliant', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#Exfoliant'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#Exfoliant', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#ActiveIngredient'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#PhotosensitiveAgent', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#SensitivityConcern'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#AcidActiveAgent', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#pHSensitiveAgent'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#VitaminDerivative', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#ActiveIngredient'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#PlantExtract', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#NaturalOrigin'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#Preservative', p: 'http://www.w3.org/2000/01/rdf-schema#subClassOf', o: 'http://example.org/cosmo#FunctionalIngredient'}),

// Incompatibility rules
(:Triple:Ontology {s: 'http://example.org/cosmo#PhotosensitiveAgent', p: 'http://example.org/cosmo#incompatibleWith', o: 'http://example.org/cosmo#AHAExfoliant'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#PhotosensitiveAgent', p: 'http://example.org/cosmo#incompatibilityRisk', o: '"Increased photosensitivity and irritation"'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#PhotosensitiveAgent', p: 'http://example.org/cosmo#incompatibilitySeverity', o: '"high"'}),

(:Triple:Ontology {s: 'http://example.org/cosmo#RetinoidAgent', p: 'http://example.org/cosmo#incompatibleWith', o: 'http://example.org/cosmo#AcidActiveAgent'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#RetinoidAgent', p: 'http://example.org/cosmo#incompatibilityRisk', o: '"Retinoid degradation at low pH, increased irritation"'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#RetinoidAgent', p: 'http://example.org/cosmo#incompatibilitySeverity', o: '"medium"'}),

(:Triple:Ontology {s: 'http://example.org/cosmo#AcidActiveAgent', p: 'http://example.org/cosmo#incompatibleWith', o: 'http://example.org/cosmo#VitaminDerivative'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#AcidActiveAgent', p: 'http://example.org/cosmo#incompatibilityRisk', o: '"Niacin flushing reaction at low pH"'}),
(:Triple:Ontology {s: 'http://example.org/cosmo#AcidActiveAgent', p: 'http://example.org/cosmo#incompatibilitySeverity', o: '"low"'});


// ══════════════════════════════════════════════════════════════
// ── DEMO QUERIES ─────────────────────────────────────────────
// ══════════════════════════════════════════════════════════════


// ── Demo 1: Compute final concentrations via BOM traversal ───
//
// reduce() multiplies ratios along the path:
// Product → Phase (70%) → PreMix (20%) → Retinol (2%) = 0.28%

MATCH path = (p:Product)-[:CONTAINS*]->(i:Ingredient {name: 'Retinol'})
WITH p, i,
     round(reduce(conc = 1.0, r IN relationships(path) | conc * r.ratio) * 100, 4) AS pct
RETURN p.name AS product, pct AS retinol_pct
ORDER BY pct DESC;


// ── Demo 2: Regulation blast radius ──────────────────────────
//
// "EU restricts Retinol above 0.05% — which products are affected?"

MATCH path = (p:Product)-[:CONTAINS*]->(i:Ingredient {name: 'Retinol'})
WITH p, i,
     reduce(conc = 1.0, r IN relationships(path) | conc * r.ratio) AS finalConc
WHERE finalConc > 0.0005
MATCH (p)-[:SELLS_IN]->(m:Market {name: 'EU'})
MATCH (b:Brand)-[:PRODUCES]->(p)
RETURN b.name AS brand, p.name AS product, p.sku AS sku,
       round(finalConc * 100, 4) AS retinol_pct,
       0.05 AS eu_limit_pct,
       'NON-COMPLIANT' AS status
ORDER BY finalConc DESC;


// ── Demo 3: Substitution candidates ──────────────────────────
//
// "What can replace Retinol? Who supplies it?"

MATCH (retinol:Ingredient {name: 'Retinol'})<-[sf:SUBSTITUTE_FOR]-(alt:Ingredient)
OPTIONAL MATCH (sup:Supplier)-[:SUPPLIES]->(alt)
RETURN alt.name AS substitute, alt.inci AS inci,
       sf.reason AS reason,
       collect(sup.name) AS suppliers;


// ── Demo 4: Validate substitute with n20s ────────────────────
//
// "Is Bakuchiol compliant in EU? Any interactions with other
//  ingredients in the Anti-Aging Serum?"

CALL {
  MATCH (:Ingredient {name: 'Bakuchiol'})-[:HAS_TRIPLE]->(t:Triple)
  RETURN t.s AS s, t.p AS p, t.o AS o
  UNION
  MATCH (:Product {name: 'Anti-Aging Serum'})-[:CONTAINS*]->(:Ingredient)-[:HAS_TRIPLE]->(t:Triple)
  RETURN t.s AS s, t.p AS p, t.o AS o
  UNION
  MATCH (t:Triple:Ontology)
  RETURN t.s AS s, t.p AS p, t.o AS o
}
WITH n20s.graph.project('reformulation_check', s, p, o) AS g
RETURN g.graphName AS graph, g.tripleCount AS triples;

// ── Demo 4b: Infer RDFS on substitute check ─────────────────

CALL n20s.graph.infer('reformulation_check', 'RDFS')
YIELD triplesBefore, triplesAfter, newTriples
RETURN triplesBefore, triplesAfter, newTriples;

// ── Demo 4c: Check incompatibilities ─────────────────────────

CALL n20s.graph.query('reformulation_check', '
  PREFIX cosmo: <http://example.org/cosmo#>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

  SELECT ?ing1 ?ing2 ?class1 ?class2 ?risk ?severity WHERE {
    ?ing1 rdf:type ?class1 .
    ?ing2 rdf:type ?class2 .
    ?class1 cosmo:incompatibleWith ?class2 .
    ?class1 cosmo:incompatibilityRisk ?risk .
    ?class1 cosmo:incompatibilitySeverity ?severity .
    FILTER(?ing1 != ?ing2)
  }
') YIELD row
RETURN row;

// ── Demo 4d: Check EU concentration limits ───────────────────

CALL n20s.graph.query('reformulation_check', '
  PREFIX cosmo: <http://example.org/cosmo#>
  SELECT ?ingredient ?limit WHERE {
    ?ingredient cosmo:maxConcentrationEU ?limit .
  }
') YIELD row
RETURN row;

// ── Demo 4e: Cleanup reformulation check ─────────────────────

CALL n20s.graph.drop('reformulation_check');


// ── Demo 5a: Project Retinol Booster — known incompatibility ─
//
// Retinol Booster has Retinol + Vitamin C (Ascorbic Acid)
// Retinoids are incompatible with acid actives

CALL {
  MATCH (:Product {name: 'Retinol Booster'})-[:CONTAINS*]->(:Ingredient)-[:HAS_TRIPLE]->(t:Triple)
  RETURN t.s AS s, t.p AS p, t.o AS o
  UNION
  MATCH (t:Triple:Ontology)
  RETURN t.s AS s, t.p AS p, t.o AS o
}
WITH n20s.graph.project('booster_check', s, p, o) AS g
RETURN g.graphName, g.tripleCount;

// ── Demo 5b: Infer RDFS on booster ───────────────────────────

CALL n20s.graph.infer('booster_check', 'RDFS')
YIELD triplesBefore, triplesAfter, newTriples
RETURN triplesBefore, triplesAfter, newTriples;

// ── Demo 5c: Detect incompatibilities in booster ─────────────

CALL n20s.graph.query('booster_check', '
  PREFIX cosmo: <http://example.org/cosmo#>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

  SELECT ?ing1 ?ing2 ?risk ?severity WHERE {
    ?ing1 rdf:type ?class1 .
    ?ing2 rdf:type ?class2 .
    ?class1 cosmo:incompatibleWith ?class2 .
    ?class1 cosmo:incompatibilityRisk ?risk .
    ?class1 cosmo:incompatibilitySeverity ?severity .
    FILTER(?ing1 != ?ing2)
  }
') YIELD row
RETURN row;

// ── Demo 5d: Cleanup booster check ───────────────────────────

CALL n20s.graph.drop('booster_check');


// ── Demo 6: Full portfolio risk scan ─────────────────────────
//
// For each product sold in EU: compute Retinol concentration,
// check against limit, flag non-compliant

MATCH (p:Product)-[:SELLS_IN]->(:Market {name: 'EU'})
OPTIONAL MATCH path = (p)-[:CONTAINS*]->(retinol:Ingredient {name: 'Retinol'})
WITH p,
     CASE WHEN path IS NOT NULL
       THEN reduce(conc = 1.0, r IN relationships(path) | conc * r.ratio)
       ELSE 0.0
     END AS retinolConc
MATCH (b:Brand)-[:PRODUCES]->(p)
RETURN b.name AS brand, p.name AS product,
       round(retinolConc * 100, 4) AS retinol_pct,
       CASE
         WHEN retinolConc > 0.0005 THEN 'NON-COMPLIANT'
         WHEN retinolConc > 0 THEN 'COMPLIANT'
         ELSE 'NO RETINOL'
       END AS eu_status
ORDER BY retinolConc DESC;
